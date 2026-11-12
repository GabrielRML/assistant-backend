package com.assistent.api.document.dto;

import java.time.Instant;
import java.util.Map;

public record DocumentUploadResponse(
        DocumentResponse document,
        String uploadUrl,
        Map<String, String> formFields,
        Instant expiresAt) {
}
