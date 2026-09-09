package com.example.stardust_springboot.auth;

import com.example.stardust_springboot.auth.security.ApiAuthenticationException;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.auth.security.JwtTokenService;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTests {

    @Test
    void expiredAccessTokenHasDedicatedErrorCode() {
        SecurityProperties properties = new SecurityProperties(
                "dGVzdC1vbmx5LWp3dC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==",
                "stardust-test",
                Duration.ofMinutes(1),
                Duration.ofDays(30),
                "test-only-refresh-token-pepper-at-least-32-bytes",
                false,
                "Lax");
        Instant issuedAt = Instant.parse("2026-09-07T10:00:00Z");
        JwtTokenService issuer = new JwtTokenService(properties,
                Clock.fixed(issuedAt, ZoneOffset.UTC));
        String token = issuer.issue(new AuthenticatedUser(
                1L, "01K00000000000000000000000", "user@example.com", "User", 0, Set.of("USER")))
                .value();
        JwtTokenService verifier = new JwtTokenService(properties,
                Clock.fixed(issuedAt.plus(Duration.ofMinutes(2)), ZoneOffset.UTC));

        assertThatThrownBy(() -> verifier.parseAndValidate(token))
                .isInstanceOfSatisfying(ApiAuthenticationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }
}
