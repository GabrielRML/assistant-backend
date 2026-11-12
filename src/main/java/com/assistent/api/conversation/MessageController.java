package com.assistent.api.conversation;

import com.assistent.api.conversation.dto.MessageSourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "Mensagens")
public class MessageController {

    private final MessageSourceService messageSourceService;

    @GetMapping("/{id}/sources")
    @Operation(summary = "Lista as fontes (trechos de documentos) usadas em uma resposta")
    public List<MessageSourceResponse> sources(@PathVariable UUID id) {
        return messageSourceService.sources(id);
    }
}
