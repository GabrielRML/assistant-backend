package com.assistent.api.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<AppUser> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<AppUser> findByWorkspaceId(UUID workspaceId, Pageable pageable);
}
