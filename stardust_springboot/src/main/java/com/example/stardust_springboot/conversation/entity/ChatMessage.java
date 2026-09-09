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
@Table(name = "chat_message",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_message_sequence_variant",
                        columnNames = {"conversation_id", "sequence_no", "variant_no"}),
                @UniqueConstraint(name = "uk_message_user_client_request",
                        columnNames = {"user_id", "client_request_id"})
        },
        indexes = {
                @Index(name = "idx_message_conversation_sequence", columnList = "conversation_id, sequence_no, id"),
                @Index(name = "idx_message_user_status_created", columnList = "user_id, status, created_at, id"),
                @Index(name = "idx_message_parent_variant", columnList = "parent_message_id, variant_no"),
                @Index(name = "idx_message_updated", columnList = "updated_at, id")
        })
public class ChatMessage extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    private ChatMessage parentMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_message_id")
    private ChatMessage supersedesMessage;

    @Column(name = "variant_no", nullable = false)
    private int variantNo;

    @Column(name = "content_text", nullable = false, columnDefinition = "LONGTEXT")
    private String contentText;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_format", nullable = false, length = 16)
    private MessageContentFormat contentFormat = MessageContentFormat.MARKDOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageStatus status = MessageStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "finish_reason", length = 24)
    private FinishReason finishReason;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private AiModel model;

    @Column(name = "prompt_tokens")
    private Long promptTokens;

    @Column(name = "completion_tokens")
    private Long completionTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "checkpoint_seq")
    private Long checkpointSeq;

    @Column(name = "checkpoint_at")
    private Instant checkpointAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    @Column(name = "content_hash", length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    protected ChatMessage() {
    }

    public ChatMessage(Conversation conversation, AppUser user, MessageRole role,
                       long sequenceNo, int variantNo, String contentText) {
        this.conversation = conversation;
        this.user = user;
        this.role = role;
        this.sequenceNo = sequenceNo;
        this.variantNo = variantNo;
        this.contentText = contentText;
    }

    public void setParentMessage(ChatMessage parentMessage) {
        this.parentMessage = parentMessage;
    }

    public void setSupersedesMessage(ChatMessage supersedesMessage) {
        this.supersedesMessage = supersedesMessage;
    }

    public void setContentFormat(MessageContentFormat contentFormat) {
        this.contentFormat = contentFormat;
    }

    public void setModel(AiModel model) {
        this.model = model;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public void startStreaming(Instant startedAt) {
        if (status != MessageStatus.PENDING) {
            throw new IllegalStateException("Only pending messages can start streaming");
        }
        status = MessageStatus.STREAMING;
        this.startedAt = startedAt;
    }

    public void completeStreaming(String content, Instant completedAt, FinishReason finishReason,
                                  Long promptTokens, Long completionTokens, Long totalTokens) {
        requireActiveStream();
        this.contentText = content;
        this.status = MessageStatus.COMPLETED;
        this.completedAt = completedAt;
        this.finishReason = finishReason;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        clearError();
    }

    public void stopStreaming(String content, Instant completedAt) {
        requireActiveStream();
        this.contentText = content;
        this.status = MessageStatus.STOPPED;
        this.completedAt = completedAt;
        this.finishReason = FinishReason.USER_CANCELLED;
        clearError();
    }

    public void failStreaming(String content, Instant completedAt, String errorCode, String errorMessage) {
        requireActiveStream();
        this.contentText = content;
        this.status = MessageStatus.FAILED;
        this.completedAt = completedAt;
        this.finishReason = FinishReason.ERROR;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    private void requireActiveStream() {
        if (status != MessageStatus.PENDING && status != MessageStatus.STREAMING) {
            throw new IllegalStateException("Message stream has already finished");
        }
    }

    private void clearError() {
        errorCode = null;
        errorMessage = null;
    }

    public void complete(Instant completedAt, Long promptTokens, Long completionTokens) {
        this.status = MessageStatus.COMPLETED;
        this.completedAt = completedAt;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens == null || completionTokens == null
                ? null : promptTokens + completionTokens;
    }

    public void softDelete() {
        status = MessageStatus.DELETED;
        markDeletedAt(Instant.now());
    }

    public Conversation getConversation() {
        return conversation;
    }

    public AppUser getUser() {
        return user;
    }

    public MessageRole getRole() {
        return role;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public ChatMessage getParentMessage() {
        return parentMessage;
    }

    public ChatMessage getSupersedesMessage() {
        return supersedesMessage;
    }

    public int getVariantNo() {
        return variantNo;
    }

    public String getContentText() {
        return contentText;
    }

    public MessageContentFormat getContentFormat() {
        return contentFormat;
    }

    public AiModel getModel() {
        return model;
    }

    public Long getPromptTokens() {
        return promptTokens;
    }

    public Long getCompletionTokens() {
        return completionTokens;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }
}
