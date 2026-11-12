package com.assistent.api.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record MessageSourceId(
        @Column(name = "message_id") UUID messageId,
        @Column(name = "chunk_id") UUID chunkId) implements Serializable {
}
