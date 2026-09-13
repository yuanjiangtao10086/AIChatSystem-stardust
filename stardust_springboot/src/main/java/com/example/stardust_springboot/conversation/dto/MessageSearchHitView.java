package com.example.stardust_springboot.conversation.dto;

import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import com.example.stardust_springboot.conversation.entity.MessageStatus;

import java.time.Instant;

/**
 * One full-text hit returned by the conversation/message search endpoints.
 *
 * <p>Only a bounded snippet of the stored content is returned: the endpoint is reachable by the owner
 * only, but list views must never ship whole message bodies to the browser (the admin list follows the
 * same rule). The snippet is plain text and is rendered as text by Vue, never as HTML.
 */
public record MessageSearchHitView(
        String messageId,
        String conversationId,
        String conversationTitle,
        MessageRole role,
        MessageStatus status,
        long sequenceNo,
        int variantNo,
        String snippet,
        Instant createdAt
) {
    public static MessageSearchHitView of(ChatMessage message, String keyword) {
        return new MessageSearchHitView(
                message.getPublicId(),
                message.getConversation().getPublicId(),
                message.getConversation().getTitle(),
                message.getRole(),
                message.getStatus(),
                message.getSequenceNo(),
                message.getVariantNo(),
                snippetOf(message.getContentText(), keyword),
                message.getCreatedAt());
    }

    /**
     * Builds a single-line excerpt centred on the first keyword occurrence. The result is intentionally
     * short so the search list stays cheap to render and cheap to transfer.
     */
    static String snippetOf(String content, String keyword) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        int radius = 60;
        int maxLength = radius * 2 + 40;
        int at = keyword == null || keyword.isEmpty()
                ? -1
                : flat.toLowerCase(java.util.Locale.ROOT).indexOf(keyword.toLowerCase(java.util.Locale.ROOT));
        if (at < 0) {
            return flat.length() <= maxLength ? flat : flat.substring(0, maxLength) + "…";
        }
        int start = Math.max(0, at - radius);
        int end = Math.min(flat.length(), start + maxLength);
        if (end - start < maxLength) {
            start = Math.max(0, end - maxLength);
        }
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }
}
