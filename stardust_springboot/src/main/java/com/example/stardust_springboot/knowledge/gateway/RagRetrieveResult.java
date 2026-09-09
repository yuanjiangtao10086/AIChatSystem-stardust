package com.example.stardust_springboot.knowledge.gateway;

import java.util.List;
import java.util.Map;

public record RagRetrieveResult(List<Source> sources) {
    public RagRetrieveResult {
        sources = List.copyOf(sources);
    }

    public record Source(
            String documentId,
            String knowledgeBaseId,
            int chunkIndex,
            String content,
            int tokenCount,
            double score,
            Integer page,
            Map<String, Object> sourceMetadata) {
    }
}
