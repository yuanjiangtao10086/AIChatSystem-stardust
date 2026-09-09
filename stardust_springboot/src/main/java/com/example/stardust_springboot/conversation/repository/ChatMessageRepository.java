package com.example.stardust_springboot.conversation.repository;

import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findByPublicIdAndUserIdAndDeletedAtIsNull(String publicId, Long userId);

    Optional<ChatMessage> findByPublicIdAndConversationIdAndUserIdAndDeletedAtIsNull(
            String publicId, Long conversationId, Long userId);

    Page<ChatMessage> findByConversationIdAndUserIdAndDeletedAtIsNull(
            Long conversationId, Long userId, Pageable pageable);

    Optional<ChatMessage> findByUserIdAndClientRequestIdAndDeletedAtIsNull(Long userId, String clientRequestId);

    Optional<ChatMessage> findFirstByConversationIdAndUserIdAndSequenceNoAndRoleAndDeletedAtIsNullOrderByVariantNoDescIdDesc(
            Long conversationId, Long userId, long sequenceNo, MessageRole role);

    @Query("""
            select coalesce(max(message.variantNo), -1) from ChatMessage message
            where message.conversation.id = :conversationId
              and message.sequenceNo = :sequenceNo
              and message.deletedAt is null
            """)
    int findMaxVariantNo(@Param("conversationId") Long conversationId,
                         @Param("sequenceNo") long sequenceNo);

    @Modifying
    @Query("""
            update ChatMessage message
            set message.status = com.example.stardust_springboot.conversation.entity.MessageStatus.FAILED,
                message.finishReason = com.example.stardust_springboot.conversation.entity.FinishReason.ERROR,
                message.errorCode = 'SERVER_RESTART',
                message.errorMessage = 'AI generation interrupted by server restart',
                message.completedAt = :now,
                message.updatedAt = :now
            where message.status in (
                com.example.stardust_springboot.conversation.entity.MessageStatus.PENDING,
                com.example.stardust_springboot.conversation.entity.MessageStatus.STREAMING)
            """)
    int failInterruptedStreams(@Param("now") Instant now);

    Page<ChatMessage> findByConversationIdAndDeletedAtIsNull(Long conversationId, Pageable pageable);
}
