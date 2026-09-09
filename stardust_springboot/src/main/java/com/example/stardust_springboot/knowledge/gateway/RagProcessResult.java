package com.example.stardust_springboot.knowledge.gateway;

import java.util.List;
import java.util.Map;

public record RagProcessResult(
        String documentId,
        String knowledgeBaseId,
        List<Chunk> chunks,
        String embeddingModel) {
    public RagProcessResult {
        chunks = List.copyOf(chunks);
    }

    public record Chunk(
            int chunkIndex,
            String content,
            int tokenCount,
            Integer page,
            Map<String, Object> sourceMetadata) {
    }
}
