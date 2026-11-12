package com.assistent.api.conversation.dto;

import java.util.UUID;

public record MessageSourceResponse(
        UUID chunkId, UUID documentId, String fileName, String contentPreview, float score) {
}
