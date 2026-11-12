package com.assistent.api.conversation;

import com.assistent.api.conversation.dto.ConversationSummaryResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConversationSummaryAssembler {

    private static final int PREVIEW_LENGTH = 120;

    private final MessageRepository messageRepository;
    private final ConversationMapper conversationMapper;

    public Page<ConversationSummaryResponse> toSummaries(Page<Conversation> page) {
        List<UUID> ids = page.map(Conversation::getId).getContent();
        Map<UUID, String> previews = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Message message : messageRepository.findLatestByConversationIds(ids)) {
                previews.putIfAbsent(message.getConversation().getId(), preview(message.getContent()));
            }
        }
        return page.map(c -> conversationMapper.toSummary(c, previews.get(c.getId())));
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
    }
}
