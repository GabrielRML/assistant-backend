package com.assistent.api.conversation;

import com.assistent.api.rag.dto.RagQueryResponse;
import com.assistent.api.rag.dto.RagSource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AssistantMessageRecorder {

    private final MessageRepository messageRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public Message record(Conversation conversation, RagQueryResponse response) {
        Message message = Message.of(conversation, MessageRole.ASSISTANT, response.answer());
        message.setInputTokens(response.inputTokens());
        message.setOutputTokens(response.outputTokens());
        message.setLatencyMs(response.latencyMs());
        messageRepository.save(message);

        List<RagSource> sources = response.sources() == null ? List.of() : response.sources();
        sources.stream()
                .collect(java.util.stream.Collectors.toMap(RagSource::chunkId, s -> s, (a, b) -> a.score() >= b.score() ? a : b))
                .values()
                .forEach(s -> messageSourceRepository.save(MessageSource.of(message, s.chunkId(), s.score())));

        conversation.registerMessage(Instant.now(clock));
        return message;
    }

}
