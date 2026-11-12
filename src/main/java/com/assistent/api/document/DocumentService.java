package com.assistent.api.document;

import com.assistent.api.common.exception.BusinessException;
import com.assistent.api.common.exception.ConflictException;
import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.config.DocumentProperties;
import com.assistent.api.config.StorageProperties;
import com.assistent.api.document.dto.DocumentCreateRequest;
import com.assistent.api.document.dto.DocumentResponse;
import com.assistent.api.document.dto.DocumentUploadResponse;
import com.assistent.api.document.dto.DownloadUrlResponse;
import com.assistent.api.storage.PresignedDownload;
import com.assistent.api.storage.PresignedUpload;
import com.assistent.api.storage.StorageService;
import com.assistent.api.storage.StoredObject;
import com.assistent.api.user.AppUserRepository;
import com.assistent.api.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AppUserRepository userRepository;
    private final DocumentMapper documentMapper;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final DocumentProperties documentProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Transactional
    public DocumentUploadResponse create(DocumentCreateRequest request) {
        UUID workspaceId = currentUser.workspaceId();
        if (!documentProperties.allowedMimeTypes().contains(request.mimeType())) {
            throw new BusinessException("Tipo de arquivo não permitido: " + request.mimeType() + ".");
        }
        if (request.sizeBytes() > documentProperties.maxSize().toBytes()) {
            throw new BusinessException("O arquivo excede o tamanho máximo de "
                    + documentProperties.maxSize().toMegabytes() + " MB.");
        }
        documentRepository.findByWorkspaceIdAndSha256Hash(workspaceId, request.sha256Hash()).ifPresent(existing -> {
            throw new ConflictException("Este arquivo já foi enviado.", existing.getId());
        });

        Document document = Document.create(
                workspaceRepository.getReferenceById(workspaceId),
                userRepository.getReferenceById(currentUser.userId()),
                request.fileName(), request.mimeType(), request.sizeBytes(), request.sha256Hash());
        documentRepository.save(document);
        document.setStorageKey("workspaces/%s/documents/%s/%s".formatted(
                workspaceId, document.getId(), sanitizeFileName(request.fileName())));

        PresignedUpload upload = storageService.createUpload(document.getStorageKey(), document.getMimeType(),
                documentProperties.maxSize().toBytes(), storageProperties.uploadUrlExpiration());
        return new DocumentUploadResponse(documentMapper.toResponse(document), upload.url(), upload.formFields(),
                upload.expiresAt());
    }

    @Transactional
    public DocumentResponse complete(UUID id) {
        Document document = find(id);
        if (document.getStatus() != DocumentStatus.AWAITING_UPLOAD) {
            return documentMapper.toResponse(document);
        }
        Optional<StoredObject> stored = storageService.stat(document.getStorageKey());
        if (stored.isEmpty()) {
            throw new BusinessException("O arquivo ainda não foi enviado ao armazenamento.");
        }
        if (!matches(document, stored.get())) {
            storageService.delete(document.getStorageKey());
            throw new BusinessException("O arquivo enviado não confere com o tamanho ou tipo declarados.");
        }
        document.markUploaded();
        return documentMapper.toResponse(document);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(DocumentStatus status, Pageable pageable) {
        UUID workspaceId = currentUser.workspaceId();
        Page<Document> page = status == null
                ? documentRepository.findByWorkspaceId(workspaceId, pageable)
                : documentRepository.findByWorkspaceIdAndStatus(workspaceId, status, pageable);
        return page.map(documentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id) {
        return documentMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse downloadUrl(UUID id) {
        Document document = find(id);
        if (document.getStatus() == DocumentStatus.AWAITING_UPLOAD) {
            throw new BusinessException("O upload deste documento ainda não foi concluído.");
        }
        PresignedDownload download = storageService.createDownload(document.getStorageKey(), document.getFileName(),
                storageProperties.downloadUrlExpiration());
        return new DownloadUrlResponse(download.url(), download.expiresAt());
    }

    @Transactional
    public DocumentResponse reprocess(UUID id) {
        Document document = find(id);
        if (document.getStatus() != DocumentStatus.FAILED && document.getStatus() != DocumentStatus.READY) {
            throw new BusinessException("Somente documentos processados ou com falha podem ser reprocessados.");
        }
        document.markUploaded();
        return documentMapper.toResponse(document);
    }

    @Transactional
    public void delete(UUID id) {
        Document document = find(id);
        documentRepository.delete(document);
        eventPublisher.publishEvent(new DocumentDeletedEvent(document.getStorageKey()));
    }

    /** Remove documentos cujo upload nunca foi confirmado dentro do prazo. */
    @Transactional
    public int cleanupExpiredUploads() {
        Instant threshold = Instant.now(clock).minus(documentProperties.pendingUploadTtl());
        var expired = documentRepository.findByStatusAndCreatedAtBefore(DocumentStatus.AWAITING_UPLOAD, threshold);
        for (Document document : expired) {
            try {
                storageService.delete(document.getStorageKey());
            } catch (RuntimeException e) {
                log.warn("Could not remove expired object {}: {}", document.getStorageKey(), e.getMessage());
            }
            documentRepository.delete(document);
        }
        return expired.size();
    }

    private Document find(UUID id) {
        return documentRepository.findByIdAndWorkspaceId(id, currentUser.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Documento não encontrado."));
    }

    private boolean matches(Document document, StoredObject stored) {
        String storedType = stored.contentType() == null ? "" : stored.contentType().split(";")[0].trim();
        return stored.size() == document.getSizeBytes() && storedType.equalsIgnoreCase(document.getMimeType());
    }

    static String sanitizeFileName(String fileName) {
        String sanitized = fileName.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        return sanitized.isBlank() ? "file" : sanitized;
    }
}
