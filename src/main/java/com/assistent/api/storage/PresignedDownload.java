package com.assistent.api.storage;

import java.time.Instant;

public record PresignedDownload(String url, Instant expiresAt) {
}
