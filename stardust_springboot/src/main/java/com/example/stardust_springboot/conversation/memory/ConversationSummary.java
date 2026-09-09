package com.example.stardust_springboot.conversation.memory;

import com.example.stardust_springboot.common.persistence.PublicIdEntity;
import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "conversation_summary",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conversation_summary_version",
                        columnNames = {"conversation_id", "summary_version"}),
                @UniqueConstraint(name = "uk_conversation_summary_anchor",
                        columnNames = {"conversation_id", "covered_through_message_id"})
        }, indexes = {
        @Index(name = "idx_summary_conversation_status_version",
                columnList = "conversation_id, status, summary_version, id"),
        @Index(name = "idx_summary_owner_updated", columnList = "user_id, updated_at, id"),
        @Index(name = "idx_summary_anchor_status", columnList = "covered_through_message_id, status, id")
})
public class ConversationSummary extends PublicIdEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "summary_version", nullable = false)
    private long summaryVersion;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "covered_through_message_id", nullable = false)
    private ChatMessage coveredThroughMessage;

    @Column(name = "covered_through_sequence_no", nullable = false)
    private long coveredThroughSequenceNo;

    @Column(name = "source_message_count", nullable = false)
    private int sourceMessageCount;

    @Column(name = "estimated_tokens", nullable = false)
    private int estimatedTokens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationSummaryStatus status = ConversationSummaryStatus.ACTIVE;

    protected ConversationSummary() {
    }

    public ConversationSummary(Conversation conversation, AppUser user, long summaryVersion,
                               String summaryText, ChatMessage coveredThroughMessage,
                               int sourceMessageCount, int estimatedTokens) {
        this.conversation = conversation;
        this.user = user;
        this.summaryVersion = summaryVersion;
        this.summaryText = summaryText;
        this.coveredThroughMessage = coveredThroughMessage;
        this.coveredThroughSequenceNo = coveredThroughMessage.getSequenceNo();
        this.sourceMessageCount = sourceMessageCount;
        this.estimatedTokens = estimatedTokens;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public int getSourceMessageCount() {
        return sourceMessageCount;
    }

    public ChatMessage getCoveredThroughMessage() {
        return coveredThroughMessage;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }
}
