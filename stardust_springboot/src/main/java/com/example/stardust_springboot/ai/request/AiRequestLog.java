package com.example.stardust_springboot.ai.request;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.entity.AiProvider;
import com.example.stardust_springboot.common.persistence.BaseEntity;
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

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "ai_request_log", indexes = {
        @Index(name = "idx_ai_request_user_created", columnList = "user_id, created_at, id"),
        @Index(name = "idx_ai_request_conversation_created", columnList = "conversation_id, created_at, id"),
        @Index(name = "idx_ai_request_status_created", columnList = "status, created_at, id"),
        @Index(name = "idx_ai_request_provider_model_created", columnList = "provider_id, model_id, created_at, id"),
        @Index(name = "idx_ai_request_updated", columnList = "updated_at, id")
})
public class AiRequestLog extends BaseEntity {

    @Column(name = "request_id", nullable = false, updatable = false, length = 26, columnDefinition = "CHAR(26)")
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assistant_message_id", nullable = false)
    private ChatMessage assistantMessage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private AiModel model;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "model_code", nullable = false, length = 64)
    private String modelCode;

    @Column(name = "external_model_id", nullable = false, length = 128)
    private String externalModelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiRequestStatus status = AiRequestStatus.PENDING;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "prompt_tokens")
    private Long promptTokens;

    @Column(name = "completion_tokens")
    private Long completionTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiRequestLog() {
    }

    public AiRequestLog(String requestId, AppUser user, Conversation conversation,
                        ChatMessage assistantMessage, AiModel model) {
        this.requestId = requestId;
        this.user = user;
        this.conversation = conversation;
        this.assistantMessage = assistantMessage;
        this.provider = model.getProvider();
        this.model = model;
        this.providerCode = model.getProvider().getCode();
        this.modelCode = model.getCode();
        this.externalModelId = model.getExternalModelId();
    }

    public void start(Instant at) {
        status = AiRequestStatus.STREAMING;
        startedAt = at;
    }

    public void complete(Instant at, Long prompt, Long completion, Long total) {
        finish(AiRequestStatus.COMPLETED, at, null, prompt, completion, total);
    }

    public void stop(Instant at) {
        finish(AiRequestStatus.STOPPED, at, null, null, null, null);
    }

    public void fail(Instant at, String code) {
        finish(AiRequestStatus.FAILED, at, code, null, null, null);
    }

    private void finish(AiRequestStatus finalStatus, Instant at, String code,
                        Long prompt, Long completion, Long total) {
        status = finalStatus;
        completedAt = at;
        errorCode = code;
        promptTokens = prompt;
        completionTokens = completion;
        totalTokens = total;
        Instant began = startedAt == null ? getCreatedAt() : startedAt;
        latencyMs = began == null ? null : Math.max(0, Duration.between(began, at).toMillis());
    }

    public String getRequestId() {
        return requestId;
    }

    public AiRequestStatus getStatus() {
        return status;
    }

    public AppUser getUser() {
        return user;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Conversation getConversation() { return conversation; }
    public AiProvider getProvider() { return provider; }
    public AiModel getModel() { return model; }
    public String getProviderCode() { return providerCode; }
    public String getModelCode() { return modelCode; }
    public Long getPromptTokens() { return promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
}
