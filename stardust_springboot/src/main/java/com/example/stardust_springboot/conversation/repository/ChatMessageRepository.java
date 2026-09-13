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

    Optional<ChatMessage> findFirstByConversationIdAndUserIdAndRoleAndDeletedAtIsNullOrderBySequenceNoAscIdAsc(
            Long conversationId, Long userId, MessageRole role);

    long countByConversationIdAndRoleAndDeletedAtIsNull(Long conversationId, MessageRole role);

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

    /**
     * Full-text (substring) search across every conversation owned by the user. Callers must pass an
     * already lower-cased and wildcard-escaped {@code pattern}; the escape character is {@code !} because
     * a backslash would itself be an escape character inside MySQL string literals.
     *
     * <p>Soft-deleted messages and messages of soft-deleted conversations are never returned, so the
     * search can never resurrect content the user has already removed.
     */
    @Query(value = """
            select message from ChatMessage message
            join message.conversation conversation
            where message.user.id = :userId
              and message.deletedAt is null
              and conversation.deletedAt is null
              and lower(message.contentText) like :pattern escape '!'
            """,
            countQuery = """
            select count(message) from ChatMessage message
            where message.user.id = :userId
              and message.deletedAt is null
              and message.conversation.deletedAt is null
              and lower(message.contentText) like :pattern escape '!'
            """)
    Page<ChatMessage> searchOwned(@Param("userId") Long userId, @Param("pattern") String pattern,
                                  Pageable pageable);

    /**
     * Same substring search scoped to a single owned conversation. The caller is responsible for proving
     * ownership of the conversation first; this query repeats the {@code user.id} predicate anyway so a
     * wrong identifier can never widen the result set.
     */
    @Query(value = """
            select message from ChatMessage message
            where message.conversation.id = :conversationId
              and message.user.id = :userId
              and message.deletedAt is null
              and lower(message.contentText) like :pattern escape '!'
            """,
            countQuery = """
            select count(message) from ChatMessage message
            where message.conversation.id = :conversationId
              and message.user.id = :userId
              and message.deletedAt is null
              and lower(message.contentText) like :pattern escape '!'
            """)
    Page<ChatMessage> searchOwnedInConversation(@Param("conversationId") Long conversationId,
                                                @Param("userId") Long userId,
                                                @Param("pattern") String pattern,
                                                Pageable pageable);

    Page<ChatMessage> findByConversationIdAndDeletedAtIsNull(Long conversationId, Pageable pageable);

    Optional<ChatMessage> findByPublicIdAndDeletedAtIsNull(String publicId);
}
