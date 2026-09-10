package com.example.stardust_springboot.common.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Exercises the Redis limiter against a real Redis. Skipped automatically when no Docker daemon is available.
 */
class RedisRateLimiterTests {

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @BeforeAll
    static void beforeAll() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "RedisRateLimiter test requires a Docker daemon");
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        if (redis.isRunning()) {
            redis.stop();
        }
    }

    @Test
    void blocksAfterMaxEventsAndReportsBlockedState() {
        Assumptions.assumeTrue(redis.isRunning());
        StringRedisFactory factory = new StringRedisFactory(
                redis.getHost(), redis.getMappedPort(6379));
        RateLimiter limiter = new RedisRateLimiter(factory.template());

        String key = "test:" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.isBlocked(key)).isFalse();
            limiter.recordRequest(key, 5, Duration.ofMinutes(1), Duration.ofMinutes(5));
        }
        assertThat(limiter.isBlocked(key)).isTrue();

        limiter.reset(key);
        assertThat(limiter.isBlocked(key)).isFalse();
    }

    /** Tiny local helper to build a {@link org.springframework.data.redis.core.StringRedisTemplate}. */
    static final class StringRedisFactory {
        private final org.springframework.data.redis.core.StringRedisTemplate template;

        StringRedisFactory(String host, int port) {
            org.springframework.data.redis.connection.RedisStandaloneConfiguration cfg =
                    new org.springframework.data.redis.connection.RedisStandaloneConfiguration(host, port);
            template = new org.springframework.data.redis.core.StringRedisTemplate(
                    new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(cfg));
            template.afterPropertiesSet();
        }

        org.springframework.data.redis.core.StringRedisTemplate template() {
            return template;
        }
    }
}
