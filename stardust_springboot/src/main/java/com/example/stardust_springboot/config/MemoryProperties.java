package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.memory")
public record MemoryProperties(int retrievalLimit, int retrievalCandidateLimit, int tokenBudget) {
    public MemoryProperties {
        retrievalLimit = positive(retrievalLimit, 5);
        retrievalCandidateLimit = positive(retrievalCandidateLimit, 100);
        tokenBudget = positive(tokenBudget, 512);
        if (retrievalLimit > retrievalCandidateLimit) {
            throw new IllegalArgumentException("retrievalLimit must not exceed candidate limit");
        }
    }
    private static int positive(int value, int fallback) { return value > 0 ? value : fallback; }
}
