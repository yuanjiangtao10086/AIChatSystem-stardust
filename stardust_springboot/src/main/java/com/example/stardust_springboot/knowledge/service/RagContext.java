package com.example.stardust_springboot.knowledge.service;

import java.util.List;
import java.util.Map;

public record RagContext(List<Source> sources) {
    public RagContext {
        sources = List.copyOf(sources);
    }

    public static RagContext empty() {
        return new RagContext(List.of());
    }

    public record Source(String documentId, String knowledgeBaseId, int chunkIndex,
                         String content, int tokenCount, double score, Integer page,
                         Map<String, Object> metadata) {
    }
}
