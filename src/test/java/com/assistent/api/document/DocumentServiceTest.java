package com.assistent.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistent.api.TestData;
import com.assistent.api.common.exception.BusinessException;
import com.assistent.api.common.exception.ConflictException;
import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.config.DocumentProperties;
import com.assistent.api.config.StorageProperties;
import com.assistent.api.document.dto.DocumentCreateRequest;
import com.assistent.api.document.dto.DocumentResponse;
import com.assistent.api.document.dto.DocumentUploadResponse;
import com.assistent.api.storage.PresignedUpload;
import com.assistent.api.storage.StorageService;
import com.assistent.api.storage.StoredObject;
import com.assistent.api.user.AppUser;
import com.assistent.api.user.AppUserRepository;
import com.assistent.api.workspace.Workspace;
import com.assistent.api.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final String HASH = "a".repeat(64);
    static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Mock DocumentRepository documentRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock AppUserRepository userRepository;
    @Mock StorageService storageService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CurrentUser currentUser;

    DocumentService service;
    Workspace workspace;
    AppUser user;

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties("http://minio", "http://minio", "k", "s", "bucket", false,
                Duration.ofMinutes(15), Duration.ofMinutes(5));
        DocumentProperties documents = new DocumentProperties(DataSize.ofMegabytes(50), Duration.ofHours(24),
                List.of("application/pdf", "text/plain"));
        service = new DocumentService(documentRepository, workspaceRepository, userRepository,
                new DocumentMapperImpl(), storageService, storage, documents, eventPublisher, currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC));
        workspace = TestData.workspace(TestData.WORKSPACE_ID);
        user = TestData.user(TestData.USER_ID, workspace);
    }

    private Document document(DocumentStatus status) {
        Document document = Document.create(workspace, user, "manual.pdf", "application/pdf", 1000L, HASH);
        document.setId(DOC_ID);
        document.setStorageKey("workspaces/%s/documents/%s/manual.pdf".formatted(TestData.WORKSPACE_ID, DOC_ID));
        document.setStatus(status);
        return document;
    }

    private void authenticated() {
        when(currentUser.workspaceId()).thenReturn(TestData.WORKSPACE_ID);
    }

    @Test
    void create_validRequest_persistsAwaitingUploadAndReturnsPresignedPost() {
        authenticated();
        when(currentUser.userId()).thenReturn(TestData.USER_ID);
        when(documentRepository.findByWorkspaceIdAndSha256Hash(TestData.WORKSPACE_ID, HASH)).thenReturn(Optional.empty());
        when(workspaceRepository.getReferenceById(TestData.WORKSPACE_ID)).thenReturn(workspace);
        when(userRepository.getReferenceById(TestData.USER_ID)).thenReturn(user);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document saved = inv.getArgument(0);
            saved.setId(DOC_ID);
            return saved;
        });
        PresignedUpload upload = new PresignedUpload("http://minio/bucket", Map.of("key", "k"), NOW.plusSeconds(900));
        when(storageService.createUpload(anyString(), eq("application/pdf"), anyLong(), any())).thenReturn(upload);

        DocumentUploadResponse response = service.create(
                new DocumentCreateRequest("manual de uso.pdf", "application/pdf", 1000L, HASH));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).createUpload(key.capture(), eq("application/pdf"), eq(DataSize.ofMegabytes(50).toBytes()), any());
        assertThat(key.getValue()).isEqualTo("workspaces/%s/documents/%s/manual_de_uso.pdf".formatted(TestData.WORKSPACE_ID, DOC_ID));
        assertThat(response.document().status()).isEqualTo(DocumentStatus.AWAITING_UPLOAD);
        assertThat(response.uploadUrl()).isEqualTo("http://minio/bucket");
    }

    @Test
    void create_mimeTypeNotAllowed_throwsBusinessException() {
        authenticated();

        assertThatThrownBy(() -> service.create(new DocumentCreateRequest("a.exe", "application/x-msdownload", 10L, HASH)))
                .isInstanceOf(BusinessException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void create_sizeAboveLimit_throwsBusinessException() {
        authenticated();
        long tooBig = DataSize.ofMegabytes(51).toBytes();

        assertThatThrownBy(() -> service.create(new DocumentCreateRequest("a.pdf", "application/pdf", tooBig, HASH)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_hashAlreadyInWorkspace_throwsConflictWithExistingId() {
        authenticated();
        when(documentRepository.findByWorkspaceIdAndSha256Hash(TestData.WORKSPACE_ID, HASH))
                .thenReturn(Optional.of(document(DocumentStatus.READY)));

        assertThatThrownBy(() -> service.create(new DocumentCreateRequest("a.pdf", "application/pdf", 10L, HASH)))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.getExistingResourceId()).isEqualTo(DOC_ID));
    }

    @Test
    void complete_objectMatches_marksPending() {
        authenticated();
        Document document = document(DocumentStatus.AWAITING_UPLOAD);
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.WORKSPACE_ID)).thenReturn(Optional.of(document));
        when(storageService.stat(document.getStorageKey())).thenReturn(Optional.of(new StoredObject(1000L, "application/pdf")));

        DocumentResponse response = service.complete(DOC_ID);

        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void complete_alreadyPending_returnsCurrentState() {
        authenticated();
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.WORKSPACE_ID))
                .thenReturn(Optional.of(document(DocumentStatus.PENDING)));

        DocumentResponse response = service.complete(DOC_ID);

        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
        verify(storageService, never()).stat(anyString());
    }

    @Test
    void complete_sizeMismatch_deletesObjectAndThrowsBusinessException() {
        authenticated();
        Document document = document(DocumentStatus.AWAITING_UPLOAD);
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.WORKSPACE_ID)).thenReturn(Optional.of(document));
        when(storageService.stat(document.getStorageKey())).thenReturn(Optional.of(new StoredObject(999L, "application/pdf")));

        assertThatThrownBy(() -> service.complete(DOC_ID)).isInstanceOf(BusinessException.class);

        verify(storageService).delete(document.getStorageKey());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.AWAITING_UPLOAD);
    }

    @Test
    void complete_objectMissing_throwsBusinessException() {
        authenticated();
        Document document = document(DocumentStatus.AWAITING_UPLOAD);
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.WORKSPACE_ID)).thenReturn(Optional.of(document));
        when(storageService.stat(document.getStorageKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(DOC_ID)).isInstanceOf(BusinessException.class);
    }

    @Test
    void get_documentFromAnotherWorkspace_throwsNotFound() {
        when(currentUser.workspaceId()).thenReturn(TestData.OTHER_WORKSPACE_ID);
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.OTHER_WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(DOC_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reprocess_processingDocument_throwsBusinessException() {
        authenticated();
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.WORKSPACE_ID))
                .thenReturn(Optional.of(document(DocumentStatus.PROCESSING)));

        assertThatThrownBy(() -> service.reprocess(DOC_ID)).isInstanceOf(BusinessException.class);
    }

    @Test
    void reprocess_failedDocument_goesBackToPending() {
        authenticated();
        Document document = document(DocumentStatus.FAILED);
        document.setError("boom");
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.WORKSPACE_ID)).thenReturn(Optional.of(document));

        DocumentResponse response = service.reprocess(DOC_ID);

        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.error()).isNull();
    }

    @Test
    void delete_existingDocument_removesRecordAndSchedulesObjectRemoval() {
        authenticated();
        Document document = document(DocumentStatus.READY);
        when(documentRepository.findByIdAndWorkspaceId(DOC_ID, TestData.WORKSPACE_ID)).thenReturn(Optional.of(document));

        service.delete(DOC_ID);

        verify(documentRepository).delete(document);
        verify(eventPublisher).publishEvent(new DocumentDeletedEvent(document.getStorageKey()));
    }

    @Test
    void cleanupExpiredUploads_removesObjectAndRecord() {
        Document expired = document(DocumentStatus.AWAITING_UPLOAD);
        when(documentRepository.findByStatusAndCreatedAtBefore(DocumentStatus.AWAITING_UPLOAD, NOW.minus(Duration.ofHours(24))))
                .thenReturn(List.of(expired));

        int removed = service.cleanupExpiredUploads();

        assertThat(removed).isEqualTo(1);
        verify(storageService).delete(expired.getStorageKey());
        verify(documentRepository).delete(expired);
    }
}
