package com.example.stardust_springboot.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process fixed-window limiter with a blocking cooldown.
 *
 * <p>State lives in the JVM heap only: it is lost on restart and is not shared between instances. This is
 * the default single-instance limiter; switch to {@link RedisRateLimiter} before horizontal scaling. Redis
 * must never become the source of truth for authorization decisions (ADR-011).
 */
public class InMemoryRateLimiter implements RateLimiter {
    private static final int CLEANUP_INTERVAL = 256;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicLong accesses = new AtomicLong();

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Instant now = clock.instant();
        Window window = windows.get(key);
        if (window == null) {
            return false;
        }
        if (window.blockedUntil != null && now.isBefore(window.blockedUntil)) {
            return true;
        }
        if (window.blockedUntil != null) {
            windows.remove(key, window);
        }
        return false;
    }

    /** Records one failure and blocks the key once {@code maxFailures} is reached inside {@code window}. */
    @Override
    public void recordFailure(String key, int maxFailures, Duration window, Duration block) {
        Instant now = clock.instant();
        windows.compute(key, (ignored, current) -> {
            Window next = current == null || current.windowStart.plus(window).isBefore(now)
                    ? new Window(now, 0, null)
                    : current;
            next.failures++;
            if (next.failures >= maxFailures) {
                next.blockedUntil = now.plus(block);
            }
            return next;
        });
        purgeExpired(now);
    }

    @Override
    public void recordRequest(String key, int maxRequests, Duration window, Duration block) {
        recordFailure(key, maxRequests, window, block);
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    @Override
    public void resetAll() {
        windows.clear();
    }

    private void purgeExpired(Instant now) {
        if (accesses.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Window> entry = iterator.next();
            Window window = entry.getValue();
            boolean blockExpired = window.blockedUntil != null && !now.isBefore(window.blockedUntil);
            if (blockExpired) {
                iterator.remove();
            }
        }
    }

    private static final class Window {
        private final Instant windowStart;
        private int failures;
        private Instant blockedUntil;

        private Window(Instant windowStart, int failures, Instant blockedUntil) {
            this.windowStart = windowStart;
            this.failures = failures;
            this.blockedUntil = blockedUntil;
        }
    }
}
