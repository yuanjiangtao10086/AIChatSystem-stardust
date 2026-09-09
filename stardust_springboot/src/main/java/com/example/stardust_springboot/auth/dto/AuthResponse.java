package com.example.stardust_springboot.auth.dto;

import com.example.stardust_springboot.user.dto.UserView;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserView user
) {
}
