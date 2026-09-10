package com.example.stardust_springboot.config;

import com.example.stardust_springboot.common.ratelimit.InMemoryRateLimiter;
import com.example.stardust_springboot.common.ratelimit.RateLimiter;
import com.example.stardust_springboot.common.ratelimit.RedisRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.Base64;

/**
 * Selects the limiter implementation from {@code app.security.rate-limit.store}.
 *
 * <p>Defaults to the in-process memory limiter. Switching to {@code redis} introduces a shared counter so
 * multiple instances agree on limits; the Redis connection is built here (instead of relying solely on
 * auto-configuration) so an optionally base64-encoded password can be decoded first. The connection-factory bean
 * is named {@code redisConnectionFactory} so it also satisfies any {@code @ConditionalOnMissingBean} Redis auto-config.
 */
@Configuration
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "redis")
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${app.redis.password-encoded:false}") boolean passwordEncoded) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        if (username != null && !username.isEmpty()) {
            configuration.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            configuration.setPassword(
                    passwordEncoded ? new String(Base64.getDecoder().decode(password)) : password);
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "redis")
    public RateLimiter redisRateLimiter(RedisConnectionFactory factory) {
        return new RedisRateLimiter(new StringRedisTemplate(factory));
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "memory", matchIfMissing = true)
    public RateLimiter inMemoryRateLimiter(Clock clock) {
        return new InMemoryRateLimiter(clock);
    }
}
