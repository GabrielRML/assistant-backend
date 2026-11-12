package com.assistent.api.conversation;

import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.conversation.dto.MessageSourceResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageSourceService {

    private static final int PREVIEW_LENGTH = 300;

    private final MessageRepository messageRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<MessageSourceResponse> sources(UUID messageId) {
        UUID workspaceId = currentUser.workspaceId();
        messageRepository.findByIdAndConversationWorkspaceId(messageId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensagem não encontrada."));

        List<MessageSource> sources = messageSourceRepository.findByIdMessageId(messageId);
        if (sources.isEmpty()) {
            return List.of();
        }
        Map<UUID, ChunkPreview> previews = messageSourceRepository
                .findChunkPreviews(sources.stream().map(MessageSource::getChunkId).toList(), workspaceId)
                .stream()
                .collect(Collectors.toMap(ChunkPreview::getId, Function.identity()));

        return sources.stream()
                .filter(s -> previews.containsKey(s.getChunkId()))
                .map(s -> {
                    ChunkPreview chunk = previews.get(s.getChunkId());
                    return new MessageSourceResponse(s.getChunkId(), chunk.getDocumentId(), chunk.getFileName(),
                            truncate(chunk.getContent()), s.getScore());
                })
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .toList();
    }

    private String truncate(String content) {
        if (content == null || content.length() <= PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, PREVIEW_LENGTH) + "…";
    }
}
