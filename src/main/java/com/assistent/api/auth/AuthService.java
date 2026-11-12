package com.assistent.api.auth;

import com.assistent.api.auth.dto.AuthResponse;
import com.assistent.api.auth.dto.LoginRequest;
import com.assistent.api.auth.dto.RegisterRequest;
import com.assistent.api.common.exception.BusinessException;
import com.assistent.api.common.exception.ConflictException;
import com.assistent.api.config.SecurityProperties;
import com.assistent.api.user.AppUser;
import com.assistent.api.user.AppUserRepository;
import com.assistent.api.user.UserMapper;
import com.assistent.api.workspace.Workspace;
import com.assistent.api.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AppUserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final SecurityProperties securityProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!securityProperties.registrationEnabled()) {
            throw new BusinessException("O cadastro de novos workspaces está desabilitado.");
        }
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
        Workspace workspace = workspaceRepository.save(Workspace.create(request.workspaceName().trim()));
        AppUser user = userRepository.save(
                AppUser.create(workspace, request.name().trim(), email, passwordEncoder.encode(request.password())));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(AppUser user) {
        return new AuthResponse(tokenService.issue(user), TOKEN_TYPE, tokenService.expiresInSeconds(),
                userMapper.toResponse(user));
    }
}
