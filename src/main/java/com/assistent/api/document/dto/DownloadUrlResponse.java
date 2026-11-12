package com.assistent.api.document.dto;

import java.time.Instant;

public record DownloadUrlResponse(String url, Instant expiresAt) {
}
