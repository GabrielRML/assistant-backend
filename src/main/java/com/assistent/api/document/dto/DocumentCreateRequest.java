package com.assistent.api.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DocumentCreateRequest(
        @NotBlank(message = "O nome do arquivo é obrigatório.")
        @Size(max = 255, message = "O nome do arquivo deve ter no máximo 255 caracteres.")
        String fileName,
        @NotBlank(message = "O tipo do arquivo é obrigatório.")
        @Size(max = 150, message = "O tipo do arquivo deve ter no máximo 150 caracteres.")
        String mimeType,
        @NotNull(message = "O tamanho é obrigatório.")
        @Positive(message = "O tamanho deve ser positivo.")
        Long sizeBytes,
        @NotBlank(message = "O hash SHA-256 é obrigatório.")
        @Pattern(regexp = "^[a-f0-9]{64}$", message = "O hash SHA-256 deve ter 64 caracteres hexadecimais minúsculos.")
        String sha256Hash) {
}
