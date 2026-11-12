package com.assistent.api.auth.dto;

import com.assistent.api.user.dto.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
