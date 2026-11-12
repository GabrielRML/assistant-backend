package com.assistent.api.conversation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<Conversation> findByWorkspaceId(UUID workspaceId, Pageable pageable);
}
