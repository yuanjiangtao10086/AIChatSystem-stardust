package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecretBase64,
        String jwtIssuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String refreshTokenPepper,
        boolean refreshCookieSecure,
        String refreshCookieSameSite
) {
    public SecurityProperties {
        if (jwtSecretBase64 == null || jwtSecretBase64.isBlank()) {
            throw new IllegalArgumentException("app.security.jwt-secret-base64 is required");
        }
        if (jwtIssuer == null || jwtIssuer.isBlank()) {
            throw new IllegalArgumentException("app.security.jwt-issuer is required");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("app.security.access-token-ttl must be positive");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("app.security.refresh-token-ttl must be positive");
        }
        if (refreshTokenPepper == null || refreshTokenPepper.length() < 32) {
            throw new IllegalArgumentException("app.security.refresh-token-pepper must contain at least 32 characters");
        }
        if (!"Lax".equals(refreshCookieSameSite) && !"Strict".equals(refreshCookieSameSite)) {
            throw new IllegalArgumentException("app.security.refresh-cookie-same-site must be Lax or Strict");
        }
    }
}
