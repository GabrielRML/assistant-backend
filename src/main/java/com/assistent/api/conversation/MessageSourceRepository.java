package com.assistent.api.conversation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSourceRepository extends JpaRepository<MessageSource, MessageSourceId> {

    List<MessageSource> findByIdMessageId(UUID messageId);

    /** Leitura nativa de rag.chunk (o Spring nunca escreve nessa tabela). */
    @Query(value = """
            select c.id as "id", c.content as "content", c.document_id as "documentId", d.file_name as "fileName"
            from rag.chunk c
            join app.document d on d.id = c.document_id
            where c.id in (:chunkIds) and c.workspace_id = :workspaceId""", nativeQuery = true)
    List<ChunkPreview> findChunkPreviews(@Param("chunkIds") Collection<UUID> chunkIds,
            @Param("workspaceId") UUID workspaceId);
}
