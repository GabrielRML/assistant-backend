package com.assistent.api.user;

import com.assistent.api.common.exception.BusinessException;
import com.assistent.api.common.exception.ConflictException;
import com.assistent.api.common.exception.ResourceNotFoundException;
import com.assistent.api.common.security.CurrentUser;
import com.assistent.api.user.dto.UserCreateRequest;
import com.assistent.api.user.dto.UserResponse;
import com.assistent.api.workspace.WorkspaceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public UserResponse me() {
        return userMapper.toResponse(find(currentUser.userId()));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findByWorkspaceId(currentUser.workspaceId(), pageable).map(userMapper::toResponse);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Já existe um usuário com este e-mail.");
        }
        AppUser user = AppUser.create(
                workspaceRepository.getReferenceById(currentUser.workspaceId()),
                request.name().trim(), email, passwordEncoder.encode(request.password()));
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        AppUser user = find(id);
        if (user.getId().equals(currentUser.userId())) {
            throw new BusinessException("Não é possível excluir o próprio usuário.");
        }
        userRepository.delete(user);
    }

    private AppUser find(UUID id) {
        return userRepository.findByIdAndWorkspaceId(id, currentUser.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }
}
