package com.assistent.api.rag.dto;

import java.util.List;
import java.util.UUID;

public record RagQueryRequest(
        UUID workspaceId, UUID conversationId, String systemPrompt, String question,
        List<RagHistoryMessage> history, boolean stream) {
}
