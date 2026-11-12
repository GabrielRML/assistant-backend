package com.assistent.api.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotBlank(message = "A mensagem é obrigatória.")
        @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres.")
        String content) {
}
