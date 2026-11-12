package com.assistent.api.common.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public static final String WORKSPACE_CLAIM = "workspace_id";

    public UUID userId() {
        return UUID.fromString(jwt().getSubject());
    }

    public UUID workspaceId() {
        return UUID.fromString(jwt().getClaimAsString(WORKSPACE_CLAIM));
    }

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("No authenticated JWT in security context");
    }
}
