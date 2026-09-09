package com.example.stardust_springboot.conversation.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, Long> {
    Optional<ConversationSummary> findByCoveredThroughMessageIdAndConversationIdAndUserIdAndStatus(
            Long messageId, Long conversationId, Long userId, ConversationSummaryStatus status);

    boolean existsByConversationIdAndCoveredThroughMessageId(Long conversationId, Long messageId);

    @Query("""
            select coalesce(max(summary.summaryVersion), 0)
            from ConversationSummary summary
            where summary.conversation.id = :conversationId
            """)
    long findMaxVersion(@Param("conversationId") Long conversationId);
}
