package com.example.stardust_springboot.auth.security;

import java.time.Instant;

public record AccessTokenClaims(
        String subject,
        long authVersion,
        Instant issuedAt,
        Instant expiresAt
) {
}
