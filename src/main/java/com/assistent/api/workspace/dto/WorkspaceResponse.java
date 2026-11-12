package com.assistent.api.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(UUID id, String name, String systemPrompt, Instant createdAt) {
}
