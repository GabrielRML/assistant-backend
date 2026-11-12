package com.assistent.api.conversation;

import com.assistent.api.rag.dto.RagHistoryMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatContextBuilder {

    private final MessageRepository messageRepository;

    /** Últimas 10 mensagens em ordem cronológica. Deve ser chamado antes de gravar a pergunta atual. */
    public List<RagHistoryMessage> history(UUID conversationId) {
        List<Message> latest = new ArrayList<>(
                messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId));
        Collections.reverse(latest);
        return latest.stream()
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .map(m -> new RagHistoryMessage(m.getRole().name(), m.getContent()))
                .toList();
    }
}
