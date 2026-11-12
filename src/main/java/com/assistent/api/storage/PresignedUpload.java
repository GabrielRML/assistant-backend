package com.assistent.api.storage;

import java.time.Instant;
import java.util.Map;

public record PresignedUpload(String url, Map<String, String> formFields, Instant expiresAt) {
}
