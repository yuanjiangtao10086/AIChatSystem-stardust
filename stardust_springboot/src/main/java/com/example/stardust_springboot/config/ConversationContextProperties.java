package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.context")
public record ConversationContextProperties(
        String systemPrompt,
        int defaultContextWindow,
        int defaultOutputReserve,
        int safetyReserve,
        int maxRecentMessages,
        int summaryTriggerMessages,
        int summaryTriggerTokens,
        int recentMessageBudget,
        int keepRecentMessages,
        int maxSummaryTokens
) {
    public ConversationContextProperties {
        systemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? "You are a helpful AI assistant." : systemPrompt.trim();
        defaultContextWindow = positive(defaultContextWindow, 16_384);
        defaultOutputReserve = positive(defaultOutputReserve, 2_048);
        safetyReserve = positive(safetyReserve, 512);
        maxRecentMessages = positive(maxRecentMessages, 24);
        summaryTriggerMessages = positive(summaryTriggerMessages, 16);
        summaryTriggerTokens = positive(summaryTriggerTokens, 8_192);
        recentMessageBudget = positive(recentMessageBudget, 4_096);
        keepRecentMessages = positive(keepRecentMessages, 8);
        maxSummaryTokens = positive(maxSummaryTokens, 1_024);
        if (keepRecentMessages >= summaryTriggerMessages) {
            throw new IllegalArgumentException("keepRecentMessages must be less than summaryTriggerMessages");
        }
        if (summaryTriggerMessages > maxRecentMessages) {
            throw new IllegalArgumentException("summaryTriggerMessages must not exceed maxRecentMessages");
        }
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
