package com.example.stardust_springboot.knowledge.dto;

import com.example.stardust_springboot.knowledge.entity.KnowledgeBase;
import com.example.stardust_springboot.knowledge.entity.KnowledgeBaseStatus;

import java.time.Instant;

public record KnowledgeBaseView(
        String id,
        String name,
        String description,
        KnowledgeBaseStatus status,
        Instant createdAt,
        Instant updatedAt) {
    public static KnowledgeBaseView from(KnowledgeBase source) {
        return new KnowledgeBaseView(source.getPublicId(), source.getName(),
                source.getDescription(), source.getStatus(),
                source.getCreatedAt(), source.getUpdatedAt());
    }
}
