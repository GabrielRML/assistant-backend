package com.assistent.api.document.dto;

import com.assistent.api.document.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String fileName,
        String mimeType,
        Long sizeBytes,
        DocumentStatus status,
        String error,
        String uploadedByName,
        Instant createdAt,
        Instant processedAt) {
}
