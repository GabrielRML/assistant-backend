package com.assistent.api.rag.dto;

import java.util.UUID;

public record RagSource(UUID chunkId, float score) {
}
