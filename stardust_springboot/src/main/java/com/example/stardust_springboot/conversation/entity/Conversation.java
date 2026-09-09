package com.example.stardust_springboot.conversation.entity;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.common.persistence.SoftDeleteEntity;
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

import java.time.Instant;

@Entity
@Table(name = "conversation",
        uniqueConstraints = @UniqueConstraint(name = "uk_conversation_id_user", columnNames = {"id", "user_id"}),
        indexes = {
                @Index(name = "idx_conversation_user_status_last", columnList = "user_id, status, last_message_at, id"),
                @Index(name = "idx_conversation_user_updated", columnList = "user_id, updated_at, id")
        })
public class Conversation extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_model_id")
    private AiModel defaultModel;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "message_count", nullable = false)
    private long messageCount;

    @Column(name = "next_sequence_no", nullable = false)
    private long nextSequenceNo = 1;

    protected Conversation() {
    }

    public Conversation(AppUser user, String title) {
        this.user = user;
        this.title = title;
    }

    public void softDelete() {
        status = ConversationStatus.DELETED;
        markDeletedAt(Instant.now());
    }

    public void update(String title, ConversationStatus status) {
        if (title != null) {
            this.title = title;
        }
        if (status != null) {
            if (status == ConversationStatus.DELETED) {
                throw new IllegalArgumentException("Use softDelete for deleted conversations");
            }
            this.status = status;
        }
    }

    public long appendMessage(Instant occurredAt) {
        long allocated = nextSequenceNo;
        nextSequenceNo++;
        messageCount++;
        lastMessageAt = occurredAt;
        return allocated;
    }

    public void appendVariant(Instant occurredAt) {
        messageCount++;
        lastMessageAt = occurredAt;
    }

    public void touchLastMessageAt(Instant occurredAt) {
        lastMessageAt = occurredAt;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public long getMessageCount() {
        return messageCount;
    }
}
