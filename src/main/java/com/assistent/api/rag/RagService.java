package com.assistent.api.rag;

import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.conversation.dto.ChatMessageRequest;
import com.assistent.api.rag.dto.RagQueryRequest;
import com.assistent.api.rag.dto.RagQueryResponse;
import com.assistent.api.workspace.Workspace;
import com.assistent.api.workspace.WorkspaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RagService {

    private final RagClient ragClient;
    private final WorkspaceRepository workspaceRepository;
    private final CurrentUser currentUser;

    /** Pergunta avulsa ao RAG, sem conversa nem persistência. */
    @Transactional(readOnly = true)
    public RagQueryResponse ask(ChatMessageRequest request) {
        Workspace workspace = workspaceRepository.getReferenceById(currentUser.workspaceId());
        return ragClient.query(new RagQueryRequest(
                workspace.getId(), null, workspace.getSystemPrompt(), request.content(), List.of(), false));
    }
}
