package com.assistent.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.assistent.api.TestData;
import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.workspace.dto.WorkspaceResponse;
import com.assistent.api.workspace.dto.WorkspaceUpdateRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock WorkspaceRepository workspaceRepository;
    @Mock CurrentUser currentUser;

    WorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceService(workspaceRepository, new WorkspaceMapperImpl(), currentUser);
        when(currentUser.workspaceId()).thenReturn(TestData.WORKSPACE_ID);
    }

    @Test
    void update_validRequest_changesNameAndSystemPrompt() {
        Workspace workspace = TestData.workspace(TestData.WORKSPACE_ID);
        when(workspaceRepository.findById(TestData.WORKSPACE_ID)).thenReturn(Optional.of(workspace));

        WorkspaceResponse response = service.update(new WorkspaceUpdateRequest("Nova Acme", "Responda em português."));

        assertThat(response.name()).isEqualTo("Nova Acme");
        assertThat(response.systemPrompt()).isEqualTo("Responda em português.");
        assertThat(workspace.getName()).isEqualTo("Nova Acme");
    }

    @Test
    void get_workspaceMissing_throwsNotFound() {
        when(workspaceRepository.findById(TestData.WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get()).isInstanceOf(ResourceNotFoundException.class);
    }
}
