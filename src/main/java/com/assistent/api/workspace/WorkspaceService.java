package com.assistent.api.workspace;

import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.workspace.dto.WorkspaceResponse;
import com.assistent.api.workspace.dto.WorkspaceUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public WorkspaceResponse get() {
        return workspaceMapper.toResponse(find());
    }

    @Transactional
    public WorkspaceResponse update(WorkspaceUpdateRequest request) {
        Workspace workspace = find();
        workspaceMapper.update(request, workspace);
        return workspaceMapper.toResponse(workspace);
    }

    private Workspace find() {
        return workspaceRepository.findById(currentUser.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace não encontrado."));
    }
}
