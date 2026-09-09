package com.example.stardust_springboot.knowledge.dto;

import com.example.stardust_springboot.knowledge.entity.KnowledgeDocument;
import com.example.stardust_springboot.knowledge.entity.KnowledgeDocumentStatus;

import java.time.Instant;

public record KnowledgeDocumentView(
        String id,
        String knowledgeBaseId,
        String fileId,
        String fileName,
        String mimeType,
        KnowledgeDocumentStatus status,
        int chunkCount,
        int processingVersion,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
    public static KnowledgeDocumentView from(KnowledgeDocument value) {
        return new KnowledgeDocumentView(
                value.getPublicId(),
                value.getKnowledgeBase().getPublicId(),
                value.getUserFile().getPublicId(),
                value.getUserFile().getOriginalName(),
                value.getUserFile().getDetectedMime(),
                value.getStatus(),
                value.getChunkCount(),
                value.getProcessingVersion(),
                value.getErrorCode(),
                value.getErrorMessage(),
                value.getStartedAt(),
                value.getCompletedAt(),
                value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
