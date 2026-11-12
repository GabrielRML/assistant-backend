package com.assistent.api.conversation;

import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.config.RagProperties;
import com.assistent.api.conversation.dto.ChatMessageRequest;
import com.assistent.api.conversation.dto.ConversationResponse;
import com.assistent.api.conversation.dto.ConversationSummaryResponse;
import com.assistent.api.conversation.dto.MessageResponse;
import com.assistent.api.rag.RagClient;
import com.assistent.api.rag.dto.RagQueryRequest;
import com.assistent.api.rag.dto.RagQueryResponse;
import com.assistent.api.common.exception.RagUnavailableException;
import com.assistent.api.user.AppUserRepository;
import com.assistent.api.workspace.Workspace;
import com.assistent.api.workspace.WorkspaceRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AppUserRepository userRepository;
    private final ConversationMapper conversationMapper;
    private final ConversationSummaryAssembler summaryAssembler;
    private final ChatContextBuilder chatContextBuilder;
    private final AssistantMessageRecorder assistantMessageRecorder;
    private final RagClient ragClient;
    private final RagProperties ragProperties;
    private final CurrentUser currentUser;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Transactional
    public ConversationResponse create() {
        Conversation conversation = Conversation.forPlayground(
                workspaceRepository.getReferenceById(currentUser.workspaceId()),
                userRepository.getReferenceById(currentUser.userId()));
        return conversationMapper.toResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> list(Pageable pageable) {
        return summaryAssembler.toSummaries(conversationRepository.findByWorkspaceId(
                currentUser.workspaceId(), pageable));
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> messages(UUID conversationId, Pageable pageable) {
        Conversation conversation = find(conversationId);
        return messageRepository.findByConversationId(conversation.getId(), pageable)
                .map(conversationMapper::toResponse);
    }

    @Transactional
    public void delete(UUID conversationId) {
        conversationRepository.delete(find(conversationId));
    }

    /** Grava a pergunta e inicia o streaming da resposta numa thread virtual. */
    public SseEmitter sendMessage(UUID conversationId, ChatMessageRequest request) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID workspaceId = currentUser.workspaceId();
        RagQueryRequest ragRequest = tx.execute(status -> prepare(conversationId, request.content()));

        SseEmitter emitter = new SseEmitter(ragProperties.timeout().toMillis() * 2);
        Thread.ofVirtual().name("playground-stream-" + conversationId).start(() -> stream(conversationId, workspaceId, ragRequest, emitter, tx));
        return emitter;
    }

    private RagQueryRequest prepare(UUID conversationId, String content) {
        Conversation conversation = find(conversationId);
        Workspace workspace = conversation.getWorkspace();
        var history = chatContextBuilder.history(conversation.getId());
        Message question = Message.of(conversation, MessageRole.USER, content);
        messageRepository.save(question);
        conversation.registerMessage(Instant.now(clock));
        return new RagQueryRequest(workspace.getId(), conversation.getId(), workspace.getSystemPrompt(), content,
                history, false);
    }

    private void stream(UUID conversationId, UUID workspaceId, RagQueryRequest ragRequest, SseEmitter emitter, TransactionTemplate tx) {
        try {
            RagQueryResponse response = ragClient.query(ragRequest);
            MessageResponse saved = tx.execute(status -> {
                Conversation conversation = conversationRepository
                        .findByIdAndWorkspaceId(conversationId, workspaceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada."));
                Message message = assistantMessageRecorder.record(conversation, response);
                return conversationMapper.toResponse(message);
            });
            send(emitter, "token", Map.of("text", response.answer() == null ? "" : response.answer()));
            send(emitter, "done", saved);
            emitter.complete();
        } catch (RagUnavailableException e) {
            log.warn("Playground chat failed for conversation {}: {}", conversationId, e.getMessage());
            try {
                send(emitter, "error", Map.of("message", "O serviço de respostas está indisponível no momento."));
                emitter.complete();
            } catch (RuntimeException ignored) {
                emitter.completeWithError(e);
            }
        } catch (RuntimeException e) {
            log.warn("Playground chat aborted for conversation {}: {}", conversationId, e.getMessage());
            emitter.completeWithError(e);
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Conversation find(UUID id) {
        return conversationRepository
                .findByIdAndWorkspaceId(id, currentUser.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada."));
    }
}
