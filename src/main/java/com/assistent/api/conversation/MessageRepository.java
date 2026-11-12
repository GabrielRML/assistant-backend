package com.assistent.api.conversation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationId(UUID conversationId, Pageable pageable);

    List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    Optional<Message> findByIdAndConversationWorkspaceId(UUID id, UUID workspaceId);

    @Query("""
            select m from Message m
            where m.conversation.id in :conversationIds
              and m.createdAt = (select max(m2.createdAt) from Message m2 where m2.conversation = m.conversation)""")
    List<Message> findLatestByConversationIds(@Param("conversationIds") Collection<UUID> conversationIds);
}
