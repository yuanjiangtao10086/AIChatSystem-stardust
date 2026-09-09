package com.example.stardust_springboot.conversation.dto;

import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.MessageContentFormat;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.entity.MessageStatus;

import java.time.Instant;
import java.util.List;

public record MessageView(
        String id,
        String conversationId,
        String parentMessageId,
        String supersedesMessageId,
        MessageRole role,
        String content,
        MessageContentFormat contentType,
        String modelId,
        MessageStatus status,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        String errorCode,
        String errorMessage,
        String finishReason,
        long sequenceNo,
        int variantNo,
        Instant createdAt,
        List<MessageAttachmentView> attachments
) {
    public static MessageView from(ChatMessage message) {
        return from(message, List.of());
    }

    public static MessageView from(ChatMessage message, List<MessageAttachmentView> attachments) {
        return new MessageView(
                message.getPublicId(),
                message.getConversation().getPublicId(),
                message.getParentMessage() == null ? null : message.getParentMessage().getPublicId(),
                message.getSupersedesMessage() == null ? null : message.getSupersedesMessage().getPublicId(),
                message.getRole(),
                message.getContentText(),
                message.getContentFormat(),
                message.getModel() == null ? null : message.getModel().getPublicId(),
                message.getStatus(),
                message.getPromptTokens(),
                message.getCompletionTokens(),
                message.getTotalTokens(),
                message.getErrorCode(),
                message.getErrorMessage(),
                message.getFinishReason() == null ? null : message.getFinishReason().name(),
                message.getSequenceNo(),
                message.getVariantNo(),
                message.getCreatedAt(), attachments);
    }
}
