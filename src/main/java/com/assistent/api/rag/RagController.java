package com.assistent.api.rag;

import com.assistent.api.conversation.dto.ChatMessageRequest;
import com.assistent.api.rag.dto.RagQueryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Tag(name = "RAG")
public class RagController {

    private final RagService ragService;

    @PostMapping("/query")
    @Operation(summary = "Faz uma pergunta avulsa ao serviço de RAG (sem streaming e sem salvar conversa)")
    public RagQueryResponse query(@Valid @RequestBody ChatMessageRequest request) {
        return ragService.ask(request);
    }
}
