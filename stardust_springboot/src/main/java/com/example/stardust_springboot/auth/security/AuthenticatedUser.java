package com.example.stardust_springboot.auth.security;

import java.util.Set;

public record AuthenticatedUser(
        Long id,
        String publicId,
        String email,
        String displayName,
        long authVersion,
        Set<String> roles
) {
}
