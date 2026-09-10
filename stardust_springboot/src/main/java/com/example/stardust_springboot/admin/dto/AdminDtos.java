package com.example.stardust_springboot.admin.dto;

import com.example.stardust_springboot.admin.audit.AdminAuditAction;
import com.example.stardust_springboot.admin.audit.AdminAuditLog;
import com.example.stardust_springboot.ai.catalog.ModelCapabilities;
import com.example.stardust_springboot.ai.entity.*;
import com.example.stardust_springboot.ai.request.AiRequestLog;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.conversation.dto.MessageView;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.knowledge.entity.KnowledgeBaseStatus;
import com.example.stardust_springboot.knowledge.entity.KnowledgeDocumentStatus;
import com.example.stardust_springboot.usage.dto.UsageView;
import com.example.stardust_springboot.user.entity.UserStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AdminDtos {
    private AdminDtos() {}

    /**
     * One hourly bucket of the dashboard trend. Buckets are UTC, oldest first, and always 24 of
     * them so the console can render a stable width chart without guessing.
     */
    public record HourlyPoint(Instant bucketStart, long requests, long failures) {}

    public record Dashboard(long totalUsers, long todayNewUsers, long activeUsers,
                            long conversations, long messages, long aiRequests,
                            long totalTokens, long files, long storageBytes,
                            long ragDocuments, long systemErrors,
                            long enabledProviders, long enabledModels,
                            List<HourlyPoint> hourly, List<AuditView> recentAudits,
                            List<AiRequestView> recentFailures) {}

    public record UserView(String id, String email, String displayName, UserStatus status,
                           String banReason, Instant bannedUntil, Instant lastLoginAt,
                           Set<String> roles, long usedBytes, long quotaBytes,
                           long aiRequests, long aiTokens, UsageView usage,
                           Instant createdAt, Instant updatedAt) {}

    /** Administrative quota correction. {@code reason} is required and is stored in the audit metadata. */
    public record UsageAdjust(long tokenDelta, @NotNull BigDecimal costDelta,
                              @NotBlank @Size(max = 500) String reason) {}

    public record CreateUser(@Email @NotBlank @Size(max=320) String email,
                             @NotBlank @Size(min=2,max=100) String displayName,
                             @NotBlank @Size(min=12,max=128) String password,
                             @NotEmpty Set<String> roles) {}
    public record UpdateUser(@Email @NotBlank @Size(max=320) String email,
                             @NotBlank @Size(min=2,max=100) String displayName) {}
    public record UserStatusChange(@NotNull UserStatus status, @Size(max=500) String reason,
                                   Instant bannedUntil) {}
    public record ResetPassword(@NotBlank @Size(min=12,max=128) String password) {}
    public record RoleChange(@NotEmpty Set<String> roles) {}

    /**
     * Conversation row of the admin console. {@code userName} is part of the contract because the
     * list renders the owner; every other admin list projection exposes it too, so omitting it
     * here is the kind of drift that hides a broken projection behind a blank cell.
     */
    public record ConversationView(String id, String userId, String userEmail, String userName,
                                   String title, ConversationStatus status, long messageCount,
                                   Instant lastMessageAt, Instant createdAt) {}

    /** Conversation detail shown to an administrator, embedding the first page of messages. */
    public record ConversationDetailView(String id, String userId, String userEmail, String userName,
                                         String title, ConversationStatus status, long messageCount,
                                         Instant lastMessageAt, Instant createdAt, Instant updatedAt,
                                         PageResult<MessageView> messages) {}

    public record FileView(String id, String userId, String userEmail, String userName, String name,
                           String mime, long sizeBytes, UserFileStatus status,
                           boolean referenced, Instant createdAt) {}

    /**
     * File metadata shown to an administrator. The server-side {@code object_key} and the real
     * absolute storage path are deliberately never exposed; downloads must go through
     * {@code GET /api/v1/admin/files/{id}/download}.
     */
    public record FileDetailView(String id, String userId, String userEmail, String userName,
                                 String name, String declaredMime, String detectedMime,
                                 String extension, long sizeBytes, String sha256,
                                 String storageProvider, UserFileStatus status, String metadataJson,
                                 long attachmentCount, long knowledgeDocumentCount,
                                 boolean referenced, Instant createdAt, Instant updatedAt) {}

    public record KnowledgeBaseView(String id, String userId, String userEmail, String userName,
                                    String name, KnowledgeBaseStatus status,
                                    Instant createdAt, Instant updatedAt) {}

    /** Knowledge base detail: owner, description, pipeline statistics and the first page of documents. */
    public record KnowledgeBaseDetailView(String id, String userId, String userEmail, String userName,
                                          String name, String description, KnowledgeBaseStatus status,
                                          long documentCount, long readyDocumentCount,
                                          long failedDocumentCount, long totalChunks,
                                          Instant createdAt, Instant updatedAt,
                                          PageResult<KnowledgeDocumentView> documents) {}

    /** Document row exposed to an administrator, including state, chunk count and failure reason. */
    public record KnowledgeDocumentView(String id, String knowledgeBaseId, String knowledgeBaseName,
                                        String userId, String userEmail, String userName,
                                        String fileId, String filename, String mimeType,
                                        KnowledgeDocumentStatus status, int chunkCount,
                                        int processingVersion, String parserType,
                                        String embeddingProvider, String embeddingModel,
                                        String errorCode, String errorMessage,
                                        Instant startedAt, Instant completedAt,
                                        Instant createdAt, Instant updatedAt) {}

    /**
     * Provider row for the console. The stored {@code credential_ref} is never returned — only
     * whether a credential is configured and a mask of the <em>deployed</em> secret value
     * ({@code sk-****abcd}). No response of this API contains a full API key.
     */
    public record ProviderView(String id, String code, String displayName, ProviderType type,
                               String baseUrl, boolean hasApiKey, String maskedApiKey,
                               Integer timeoutSeconds, Integer connectTimeoutSeconds,
                               ProviderStatus status, ProviderHealthStatus health,
                               Instant healthCheckedAt, long modelCount,
                               Instant createdAt, Instant updatedAt) {}

    /**
     * Provider write payload. {@code credentialRef} must be a secret <em>reference</em>
     * ({@code env:NAME}, {@code vault:...}) because this architecture stores references, not keys
     * (ADR-016); a raw API key is rejected by validation so plaintext can never reach the database.
     * Leaving it blank on update keeps the stored reference.
     */
    public record ProviderRequest(@NotBlank @Pattern(regexp="[a-z0-9-]{2,64}") String code,
                                  @NotBlank @Size(max=100) String displayName,
                                  @NotNull ProviderType type,
                                  @NotBlank @Size(max=500) String baseUrl,
                                  @Pattern(regexp="^$|[a-z][a-z0-9-]{1,15}:[A-Za-z0-9][A-Za-z0-9_.:/@-]{0,200}")
                                  String credentialRef,
                                  @Min(1) @Max(300) Integer timeoutSeconds,
                                  @Min(1) @Max(60) Integer connectTimeoutSeconds) {}
    public record StatusRequest(@NotNull Boolean enabled) {}

    /** Model row for the console, with typed capabilities and generation defaults. */
    public record ModelView(String id, String providerId, String providerName, String code,
                            String externalModelId, String displayName, ModelType type,
                            ModelCapabilities capabilities, Integer contextWindow,
                            Integer maxOutputTokens, BigDecimal defaultTemperature,
                            BigDecimal defaultTopP, Integer defaultMaxOutputTokens,
                            BigDecimal inputPrice, BigDecimal outputPrice, String currency,
                            ModelStatus status, boolean defaultModel, int sortOrder,
                            Instant createdAt, Instant updatedAt) {}

    public record ModelRequest(@NotBlank String providerId,
                               @NotBlank @Pattern(regexp="[a-z0-9._-]{2,64}") String code,
                               @NotBlank @Size(max=128) String externalModelId,
                               @NotBlank @Size(max=100) String displayName,
                               @NotNull ModelType type,
                               ModelCapabilities capabilities,
                               @Positive Integer contextWindow, @Positive Integer maxOutputTokens,
                               @DecimalMin("0") @DecimalMax("2") BigDecimal defaultTemperature,
                               @DecimalMin("0") @DecimalMax("1") BigDecimal defaultTopP,
                               @Positive Integer defaultMaxOutputTokens,
                               @PositiveOrZero BigDecimal inputPrice,
                               @PositiveOrZero BigDecimal outputPrice,
                               @Size(min=3,max=3) String currency,
                               int sortOrder) {}

    /** Flips the platform default flag of one model inside its own {@link ModelType}. */
    public record ModelDefaultRequest(@NotNull Boolean defaultModel) {}

    /** Moves a model one position up or down inside its provider's ordered list. */
    public record ModelReorderRequest(@NotBlank @Pattern(regexp="UP|DOWN") String direction) {}

    public record AiRequestView(String requestId, String userId, String conversationId,
                                String provider, String model, AiRequestStatus status,
                                Long latencyMs, Long promptTokens, Long completionTokens,
                                Long totalTokens, String errorCode, Instant createdAt) {}

    public record AuditView(String id, String adminId, String adminEmail,
                            AdminAuditAction action, String targetUserId,
                            String targetResourceType, String targetResourceId,
                            String ip, String userAgent, String requestId,
                            String metadataJson, Instant createdAt) {}

    /**
     * Shared mappers so the dashboard, the audit query and the request-log query all expose the
     * same projection instead of three drifting copies.
     */
    public static AuditView of(AdminAuditLog log) {
        return new AuditView(log.getPublicId(), log.getAdmin().getPublicId(), log.getAdmin().getEmailNormalized(),
                log.getAction(), log.getTargetUser() == null ? null : log.getTargetUser().getPublicId(),
                log.getTargetResourceType(), log.getTargetResourceId(), log.getIp(), log.getUserAgent(),
                log.getRequestId(), log.getMetadataJson(), log.getCreatedAt());
    }

    public static AiRequestView of(AiRequestLog log) {
        return new AiRequestView(log.getRequestId(), log.getUser().getPublicId(), log.getConversation().getPublicId(),
                log.getProviderCode(), log.getModelCode(), log.getStatus(), log.getLatencyMs(), log.getPromptTokens(),
                log.getCompletionTokens(), log.getTotalTokens(), log.getErrorCode(), log.getCreatedAt());
    }
}
