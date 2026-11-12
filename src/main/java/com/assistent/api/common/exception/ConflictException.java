package com.assistent.api.common.exception;

import java.util.UUID;

public class ConflictException extends RuntimeException {

    private final UUID existingResourceId;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, UUID existingResourceId) {
        super(message);
        this.existingResourceId = existingResourceId;
    }

    public UUID getExistingResourceId() {
        return existingResourceId;
    }
}
