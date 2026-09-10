package com.example.stardust_springboot.auth.security;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.ratelimit.RateLimiter;
import com.example.stardust_springboot.config.RateLimitProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Brute-force protection for {@code POST /api/v1/auth/login}.
 *
 * <p>Failures are counted against both the client address and the normalized email, so neither a single
 * account being sprayed from many addresses nor one address enumerating many accounts can proceed
 * unchecked. Only the failure counter is kept; no credential, token or request body is ever stored.
 */
@Service
public class LoginAttemptGuard {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final Counter blockedCounter;

    public LoginAttemptGuard(RateLimiter limiter, RateLimitProperties properties, MeterRegistry meter) {
        this.limiter = limiter;
        this.properties = properties;
        this.blockedCounter = meter.counter("auth_login_rate_limited_total");
    }

    public void verifyNotBlocked(String clientIp, String normalizedEmail) {
        if (limiter.isBlocked(ipKey(clientIp)) || limiter.isBlocked(emailKey(normalizedEmail))) {
            blockedCounter.increment();
            log.warn("Login temporarily blocked ip={} emailKeyPresent={}", clientIp, normalizedEmail != null);
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }

    public void recordFailure(String clientIp, String normalizedEmail) {
        limiter.recordFailure(ipKey(clientIp), properties.loginMaxFailures(),
                properties.loginWindow(), properties.loginBlock());
        if (normalizedEmail != null) {
            limiter.recordFailure(emailKey(normalizedEmail), properties.loginMaxFailures(),
                    properties.loginWindow(), properties.loginBlock());
        }
    }

    public void reset(String clientIp, String normalizedEmail) {
        limiter.reset(ipKey(clientIp));
        if (normalizedEmail != null) {
            limiter.reset(emailKey(normalizedEmail));
        }
    }

    private String ipKey(String clientIp) {
        return "login:ip:" + (clientIp == null ? "unknown" : clientIp);
    }

    private String emailKey(String normalizedEmail) {
        return "login:email:" + normalizedEmail;
    }
}
