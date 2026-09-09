package com.example.stardust_springboot.auth.security;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}
