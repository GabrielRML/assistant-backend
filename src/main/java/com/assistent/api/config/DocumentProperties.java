package com.assistent.api.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(
        @NotNull DataSize maxSize,
        @NotNull Duration pendingUploadTtl,
        @NotEmpty List<String> allowedMimeTypes) {
}
