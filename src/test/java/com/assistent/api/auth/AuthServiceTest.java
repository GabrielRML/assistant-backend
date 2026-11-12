package com.assistent.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistent.api.TestData;
import com.assistent.api.auth.dto.AuthResponse;
import com.assistent.api.auth.dto.LoginRequest;
import com.assistent.api.auth.dto.RegisterRequest;
import com.assistent.api.common.exception.BusinessException;
import com.assistent.api.common.exception.ConflictException;
import com.assistent.api.config.SecurityProperties;
import com.assistent.api.user.AppUser;
import com.assistent.api.user.AppUserRepository;
import com.assistent.api.user.UserMapperImpl;
import com.assistent.api.workspace.Workspace;
import com.assistent.api.workspace.WorkspaceRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokenService;

    AuthService service;

    @BeforeEach
    void setUp() {
        service = service(true);
    }

    private AuthService service(boolean registrationEnabled) {
        SecurityProperties properties = new SecurityProperties("secret", Duration.ofHours(8), "http://localhost", registrationEnabled);
        return new AuthService(userRepository, workspaceRepository, passwordEncoder, tokenService,
                new UserMapperImpl(), properties);
    }

    @Test
    void register_validRequest_createsWorkspaceAndUserAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("Acme", "Ana", "Ana@Acme.com", "senha-segura");
        Workspace workspace = TestData.workspace(TestData.WORKSPACE_ID);
        when(userRepository.existsByEmail("ana@acme.com")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(workspace);
        when(passwordEncoder.encode("senha-segura")).thenReturn("{bcrypt}hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser user = inv.getArgument(0);
            user.setId(TestData.USER_ID);
            return user;
        });
        when(tokenService.issue(any(AppUser.class))).thenReturn("jwt-token");
        when(tokenService.expiresInSeconds()).thenReturn(28800L);

        AuthResponse response = service.register(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("ana@acme.com");
    }

    @Test
    void register_emailAlreadyExists_throwsConflict() {
        when(userRepository.existsByEmail("ana@acme.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Acme", "Ana", "ana@acme.com", "senha-segura")))
                .isInstanceOf(ConflictException.class);
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void register_registrationDisabled_throwsBusinessException() {
        AuthService disabled = service(false);

        assertThatThrownBy(() -> disabled.register(new RegisterRequest("Acme", "Ana", "ana@acme.com", "senha-segura")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_validCredentials_returnsToken() {
        AppUser user = TestData.user(TestData.USER_ID, TestData.workspace(TestData.WORKSPACE_ID));
        when(userRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-segura", "{bcrypt}hash")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn("jwt-token");

        AuthResponse response = service.login(new LoginRequest("ana@acme.com", "senha-segura"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(TestData.USER_ID);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        AppUser user = TestData.user(UUID.randomUUID(), TestData.workspace(TestData.WORKSPACE_ID));
        when(userRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@acme.com", "errada")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail("x@acme.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("x@acme.com", "senha")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
