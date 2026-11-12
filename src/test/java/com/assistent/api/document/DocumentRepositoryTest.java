package com.assistent.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistent.api.PostgresTestSupport;
import com.assistent.api.config.JpaConfig;
import com.assistent.api.user.AppUser;
import com.assistent.api.workspace.Workspace;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class DocumentRepositoryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestSupport.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestSupport.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestSupport.POSTGRES::getPassword);
    }

    @Autowired DocumentRepository documentRepository;
    @Autowired EntityManager em;

    private Workspace workspace(String name) {
        Workspace workspace = Workspace.create(name);
        em.persist(workspace);
        return workspace;
    }

    private AppUser user(Workspace workspace, String email) {
        AppUser user = AppUser.create(workspace, "Ana", email, "{bcrypt}hash");
        em.persist(user);
        return user;
    }

    private Document document(Workspace workspace, AppUser user, String hash, DocumentStatus status) {
        Document document = Document.create(workspace, user, "a.pdf", "application/pdf", 10L, hash);
        document.setStorageKey("k/" + hash);
        document.setStatus(status);
        em.persist(document);
        return document;
    }

    @Test
    void findByIdAndWorkspaceId_documentOfAnotherWorkspace_returnsEmpty() {
        Workspace a = workspace("A");
        Workspace b = workspace("B");
        Document doc = document(a, user(a, "a@a.com"), "1".repeat(64), DocumentStatus.READY);
        em.flush();

        assertThat(documentRepository.findByIdAndWorkspaceId(doc.getId(), a.getId())).isPresent();
        assertThat(documentRepository.findByIdAndWorkspaceId(doc.getId(), b.getId())).isEmpty();
    }

    @Test
    void findByWorkspaceIdAndStatus_returnsOnlyMatchingDocumentsOfWorkspace() {
        Workspace a = workspace("A");
        Workspace b = workspace("B");
        AppUser ua = user(a, "a@a.com");
        AppUser ub = user(b, "b@b.com");
        document(a, ua, "1".repeat(64), DocumentStatus.READY);
        document(a, ua, "2".repeat(64), DocumentStatus.FAILED);
        document(b, ub, "3".repeat(64), DocumentStatus.READY);
        em.flush();

        var page = documentRepository.findByWorkspaceIdAndStatus(a.getId(), DocumentStatus.READY, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getSha256Hash()).isEqualTo("1".repeat(64));
    }

    @Test
    void save_sameHashInSameWorkspace_violatesUniqueConstraint() {
        Workspace a = workspace("A");
        AppUser ua = user(a, "a@a.com");
        document(a, ua, "1".repeat(64), DocumentStatus.READY);
        em.flush();

        assertThatThrownBy(() -> {
            document(a, ua, "1".repeat(64), DocumentStatus.READY);
            em.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }

    @Test
    void save_sameHashInDifferentWorkspaces_isAllowed() {
        Workspace a = workspace("A");
        Workspace b = workspace("B");
        document(a, user(a, "a@a.com"), "1".repeat(64), DocumentStatus.READY);
        document(b, user(b, "b@b.com"), "1".repeat(64), DocumentStatus.READY);
        em.flush();

        assertThat(documentRepository.findByWorkspaceIdAndSha256Hash(a.getId(), "1".repeat(64))).isPresent();
        assertThat(documentRepository.findByWorkspaceIdAndSha256Hash(b.getId(), "1".repeat(64))).isPresent();
    }

    @Test
    void findByStatusAndCreatedAtBefore_returnsOnlyOldAwaitingUploads() {
        Workspace a = workspace("A");
        AppUser ua = user(a, "a@a.com");
        Document awaiting = document(a, ua, "1".repeat(64), DocumentStatus.AWAITING_UPLOAD);
        Document ready = document(a, ua, "2".repeat(64), DocumentStatus.READY);
        em.flush();

        var future = Instant.now().plus(1, ChronoUnit.DAYS);
        var past = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThat(documentRepository.findByStatusAndCreatedAtBefore(DocumentStatus.AWAITING_UPLOAD, future))
                .extracting(Document::getId).contains(awaiting.getId())
                .doesNotContain(ready.getId());
        assertThat(documentRepository.findByStatusAndCreatedAtBefore(DocumentStatus.AWAITING_UPLOAD, past))
                .extracting(Document::getId).doesNotContain(awaiting.getId());
    }
}
