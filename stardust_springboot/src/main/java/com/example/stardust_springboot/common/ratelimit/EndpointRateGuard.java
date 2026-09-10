package com.example.stardust_springboot.common.ratelimit;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.config.RateLimitProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Abuse protection for the non-login entry points: registration, token refresh and file upload.
 *
 * <p>Each call first checks the block, then records the attempt, so a counted-out client is rejected on
 * the next request. Blocked attempts are counted as metrics for alerting.
 */
@Component
public class EndpointRateGuard {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final Counter registerBlocked;
    private final Counter refreshBlocked;
    private final Counter uploadBlocked;

    public EndpointRateGuard(RateLimiter limiter, RateLimitProperties properties, MeterRegistry meter) {
        this.limiter = limiter;
        this.properties = properties;
        this.registerBlocked = meter.counter("auth_register_rate_limited_total");
        this.refreshBlocked = meter.counter("auth_refresh_rate_limited_total");
        this.uploadBlocked = meter.counter("file_upload_rate_limited_total");
    }

    public void guardRegister(String clientIp) {
        checkAndRecord("register:" + clientIp, properties.registerMaxAttempts(),
                properties.registerWindow(), properties.registerBlock(), registerBlocked);
    }

    public void guardRefresh(String clientIp) {
        checkAndRecord("refresh:" + clientIp, properties.refreshMaxAttempts(),
                properties.refreshWindow(), properties.refreshBlock(), refreshBlocked);
    }

    public void guardUpload(Long userId, String clientIp) {
        checkAndRecord("upload:user:" + userId, properties.uploadMaxRequests(),
                properties.uploadWindow(), properties.uploadBlock(), uploadBlocked);
    }

    private void checkAndRecord(String key, int max, Duration window, Duration block, Counter counter) {
        if (limiter.isBlocked(key)) {
            counter.increment();
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        limiter.recordRequest(key, max, window, block);
    }
}
