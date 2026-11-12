package com.assistent.api.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(UUID id, Instant createdAt, Instant lastMessageAt) {
}
