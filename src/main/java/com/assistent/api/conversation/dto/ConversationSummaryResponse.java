package com.assistent.api.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryResponse(UUID id, String lastMessagePreview, Instant lastMessageAt) {
}
