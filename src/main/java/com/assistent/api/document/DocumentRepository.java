package com.assistent.api.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Document> findByWorkspaceIdAndSha256Hash(UUID workspaceId, String sha256Hash);

    Page<Document> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<Document> findByWorkspaceIdAndStatus(UUID workspaceId, DocumentStatus status, Pageable pageable);

    List<Document> findByStatusAndCreatedAtBefore(DocumentStatus status, Instant threshold);
}
