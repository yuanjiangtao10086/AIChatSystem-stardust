package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.rag")
public record RagProperties(
        String providerKey,
        String embeddingModel,
        int retrievalTopK,
        int retrievalTokenBudget) {
    public RagProperties {
        providerKey = providerKey == null || providerKey.isBlank()
                ? "openai-compatible" : providerKey.trim();
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
        retrievalTopK = retrievalTopK > 0 ? retrievalTopK : 6;
        retrievalTokenBudget = retrievalTokenBudget > 0 ? retrievalTokenBudget : 1200;
    }
}
