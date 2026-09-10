package com.example.stardust_springboot.common.ratelimit;

import java.time.Duration;

/**
 * Pluggable brute-force / abuse limiter.
 *
 * <p>Two event kinds share one fixed-window counter: {@link #recordFailure} counts authentication
 * failures (login), {@link #recordRequest} counts raw attempts (register / refresh / upload). When the
 * count reaches the configured maximum inside the window, the key is blocked for the cooldown.
 *
 * <p>Implementations must never become the source of truth for authorization (ADR-011): a limiter
 * failing open is preferable to one that can lock out legitimate users.
 */
public interface RateLimiter {

    /** Returns {@code true} while the key is inside its cooldown. */
    boolean isBlocked(String key);

    /** Records one authentication failure and blocks the key once {@code maxFailures} is reached. */
    void recordFailure(String key, int maxFailures, Duration window, Duration block);

    /** Records one attempt and blocks the key once {@code maxRequests} is reached. */
    void recordRequest(String key, int maxRequests, Duration window, Duration block);

    /** Clears a single key (used after a successful authentication). */
    void reset(String key);

    /** Clears all state. Memory-only implementations use this; shared stores are best cleared out-of-band. */
    void resetAll();
}
