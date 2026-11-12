package com.assistent.api.document;

import com.assistent.api.user.AppUser;
import com.assistent.api.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "document")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 150)
    private String mimeType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 512)
    private String storageKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(columnDefinition = "text")
    private String error;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant processedAt;

    public static Document create(Workspace workspace, AppUser uploadedBy, String fileName, String mimeType,
            long sizeBytes, String sha256Hash) {
        Document document = new Document();
        document.workspace = workspace;
        document.uploadedBy = uploadedBy;
        document.fileName = fileName;
        document.mimeType = mimeType;
        document.sizeBytes = sizeBytes;
        document.sha256Hash = sha256Hash;
        document.storageKey = "pending";
        document.status = DocumentStatus.AWAITING_UPLOAD;
        return document;
    }

    public void markUploaded() {
        this.status = DocumentStatus.PENDING;
        this.error = null;
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void markReady(Instant processedAt) {
        this.status = DocumentStatus.READY;
        this.error = null;
        this.processedAt = processedAt;
    }

    public void markFailed(String error) {
        this.status = DocumentStatus.FAILED;
        this.error = error;
    }
}
