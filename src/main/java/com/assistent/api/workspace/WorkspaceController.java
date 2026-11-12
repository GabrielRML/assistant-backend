package com.assistent.api.workspace;

import com.assistent.api.workspace.dto.WorkspaceResponse;
import com.assistent.api.workspace.dto.WorkspaceUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
@Tag(name = "Workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    @Operation(summary = "Retorna o workspace do usuário autenticado")
    public WorkspaceResponse get() {
        return workspaceService.get();
    }

    @PutMapping
    @Operation(summary = "Atualiza nome e prompt de sistema do workspace")
    public WorkspaceResponse update(@Valid @RequestBody WorkspaceUpdateRequest request) {
        return workspaceService.update(request);
    }
}
