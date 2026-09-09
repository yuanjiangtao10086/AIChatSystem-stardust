package com.example.stardust_springboot.conversation.dto;

import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;

import java.time.Instant;

public record ConversationView(
        String id,
        String title,
        ConversationStatus status,
        Instant lastMessageAt,
        long messageCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConversationView from(Conversation conversation) {
        return new ConversationView(
                conversation.getPublicId(),
                conversation.getTitle(),
                conversation.getStatus(),
                conversation.getLastMessageAt(),
                conversation.getMessageCount(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
