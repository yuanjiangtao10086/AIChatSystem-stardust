package com.example.stardust_springboot.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Shared, fixed-window limiter backed by Redis.
 *
 * <p>State lives in Redis so every instance of the service sees the same counters; this is what makes
 * horizontal scaling safe. Redis is deliberately used only as a shared counter — never as the source of
 * truth for authorization (ADR-011) — and the limiter fails open if Redis is unreachable.
 *
 * <p>The per-event update is a single Lua script so the increment / threshold / block transition is atomic
 * across concurrent requests.
 */
public class RedisRateLimiter implements RateLimiter {

    private static final RedisScript<Long> EVENT_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('incr', KEYS[1])\n" +
            "if c == 1 then redis.call('expire', KEYS[1], ARGV[1]) end\n" +
            "if c >= tonumber(ARGV[3]) then\n" +
            "  redis.call('set', KEYS[2], '1', 'EX', ARGV[2])\n" +
            "  redis.call('del', KEYS[1])\n" +
            "  return 1\n" +
            "end\n" +
            "return 0\n", Long.class);

    private static final RedisScript<Long> BLOCKED_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('exists', KEYS[1])", Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isBlocked(String key) {
        Long exists = redis.execute(BLOCKED_SCRIPT, List.of(blockKey(key)));
        return exists != null && exists == 1L;
    }

    @Override
    public void recordFailure(String key, int maxFailures, Duration window, Duration block) {
        recordEvent(key, maxFailures, window, block);
    }

    @Override
    public void recordRequest(String key, int maxRequests, Duration window, Duration block) {
        recordEvent(key, maxRequests, window, block);
    }

    private void recordEvent(String key, int max, Duration window, Duration block) {
        redis.execute(EVENT_SCRIPT, List.of(countKey(key), blockKey(key)),
                String.valueOf(window.getSeconds()),
                String.valueOf(block.getSeconds()),
                String.valueOf(max));
    }

    @Override
    public void reset(String key) {
        redis.delete(List.of(countKey(key), blockKey(key)));
    }

    @Override
    public void resetAll() {
        // Shared store: clearing all keys is an operational action, not a code path. No-op here.
    }

    private String countKey(String key) {
        return "rl:count:" + key;
    }

    private String blockKey(String key) {
        return "rl:block:" + key;
    }
}
