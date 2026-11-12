package com.assistent.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistent.api.TestData;
import com.assistent.api.common.exception.BusinessException;
import com.assistent.api.common.exception.ConflictException;
import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.user.dto.UserCreateRequest;
import com.assistent.api.user.dto.UserResponse;
import com.assistent.api.workspace.WorkspaceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CurrentUser currentUser;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, workspaceRepository, new UserMapperImpl(), passwordEncoder, currentUser);
    }

    @Test
    void create_newEmail_savesUserInCurrentWorkspace() {
        when(currentUser.workspaceId()).thenReturn(TestData.WORKSPACE_ID);
        when(userRepository.existsByEmail("bia@acme.com")).thenReturn(false);
        when(workspaceRepository.getReferenceById(TestData.WORKSPACE_ID)).thenReturn(TestData.workspace(TestData.WORKSPACE_ID));
        when(passwordEncoder.encode("senha-segura")).thenReturn("{bcrypt}hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.create(new UserCreateRequest("Bia", "Bia@Acme.com", "senha-segura"));

        assertThat(response.email()).isEqualTo("bia@acme.com");
        assertThat(response.name()).isEqualTo("Bia");
    }

    @Test
    void create_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("bia@acme.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new UserCreateRequest("Bia", "bia@acme.com", "senha-segura")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void delete_ownUser_throwsBusinessException() {
        AppUser self = TestData.user(TestData.USER_ID, TestData.workspace(TestData.WORKSPACE_ID));
        when(currentUser.workspaceId()).thenReturn(TestData.WORKSPACE_ID);
        when(currentUser.userId()).thenReturn(TestData.USER_ID);
        when(userRepository.findByIdAndWorkspaceId(TestData.USER_ID, TestData.WORKSPACE_ID)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.delete(TestData.USER_ID)).isInstanceOf(BusinessException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void delete_userFromAnotherWorkspace_throwsNotFound() {
        UUID otherUser = UUID.randomUUID();
        when(currentUser.workspaceId()).thenReturn(TestData.WORKSPACE_ID);
        when(userRepository.findByIdAndWorkspaceId(otherUser, TestData.WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(otherUser)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_anotherUserOfSameWorkspace_deletesUser() {
        UUID otherUser = UUID.randomUUID();
        AppUser target = TestData.user(otherUser, TestData.workspace(TestData.WORKSPACE_ID));
        when(currentUser.workspaceId()).thenReturn(TestData.WORKSPACE_ID);
        when(currentUser.userId()).thenReturn(TestData.USER_ID);
        when(userRepository.findByIdAndWorkspaceId(otherUser, TestData.WORKSPACE_ID)).thenReturn(Optional.of(target));

        service.delete(otherUser);

        verify(userRepository).delete(target);
    }
}
