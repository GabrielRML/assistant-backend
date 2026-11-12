package com.assistent.api.conversation.dto;

import com.assistent.api.conversation.MessageRole;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, MessageRole role, String content, Instant createdAt) {
}
