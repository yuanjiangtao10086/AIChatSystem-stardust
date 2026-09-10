package com.example.stardust_springboot.common.ratelimit;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.config.RateLimitProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EndpointRateGuardTests {

    private EndpointRateGuard guard() {
        RateLimiter limiter = new InMemoryRateLimiter(Clock.systemUTC());
        RateLimitProperties properties = new RateLimitProperties(
                "memory", 5, Duration.ofMinutes(10), Duration.ofMinutes(15),
                8, Duration.ofMinutes(10), Duration.ofMinutes(30),
                20, Duration.ofMinutes(5), Duration.ofMinutes(15),
                30, Duration.ofMinutes(1), Duration.ofMinutes(5), List.of());
        return new EndpointRateGuard(limiter, properties, new SimpleMeterRegistry());
    }

    @Test
    void registerIsBlockedAfterMaxAttempts() {
        EndpointRateGuard guard = guard();
        for (int i = 0; i < 8; i++) {
            guard.guardRegister("203.0.113.9");
        }
        assertThatThrownBy(() -> guard.guardRegister("203.0.113.9"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void uploadIsScopedPerUserNotPerIp() {
        EndpointRateGuard guard = guard();
        for (int i = 0; i < 30; i++) {
            guard.guardUpload(42L, "203.0.113.9");
        }
        assertThatThrownBy(() -> guard.guardUpload(42L, "203.0.113.9"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        // A different user from the same address is unaffected.
        assertThatCode(() -> guard.guardUpload(43L, "203.0.113.9")).doesNotThrowAnyException();
    }
}
