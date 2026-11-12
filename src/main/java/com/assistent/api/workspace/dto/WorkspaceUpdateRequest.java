package com.assistent.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceUpdateRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String name,
        @Size(max = 10000, message = "O prompt deve ter no máximo 10000 caracteres.")
        String systemPrompt) {
}
