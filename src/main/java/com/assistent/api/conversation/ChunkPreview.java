package com.assistent.api.conversation;

import java.util.UUID;

public interface ChunkPreview {

    UUID getId();

    String getContent();

    UUID getDocumentId();

    String getFileName();
}
