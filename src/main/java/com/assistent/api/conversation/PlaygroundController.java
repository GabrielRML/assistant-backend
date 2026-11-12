package com.assistent.api.conversation;

import com.assistent.api.conversation.dto.ChatMessageRequest;
import com.assistent.api.conversation.dto.ConversationResponse;
import com.assistent.api.conversation.dto.ConversationSummaryResponse;
import com.assistent.api.conversation.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/playground/conversations")
@RequiredArgsConstructor
@Tag(name = "Playground")
public class PlaygroundController {

    private final PlaygroundService playgroundService;

    @PostMapping
    @Operation(summary = "Cria uma conversa de teste")
    public ResponseEntity<ConversationResponse> create() {
        ConversationResponse response = playgroundService.create();
        return ResponseEntity.created(URI.create("/api/v1/playground/conversations/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista as conversas de teste do workspace")
    public PagedModel<ConversationSummaryResponse> list(
            @PageableDefault(sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(playgroundService.list(pageable));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Lista as mensagens de uma conversa de teste")
    public PagedModel<MessageResponse> messages(@PathVariable UUID id,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(playgroundService.messages(id, pageable));
    }

    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Envia uma pergunta e recebe a resposta em streaming (SSE: token, done, error)")
    public SseEmitter sendMessage(@PathVariable UUID id, @Valid @RequestBody ChatMessageRequest request) {
        return playgroundService.sendMessage(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma conversa de teste")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        playgroundService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
