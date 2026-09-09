package com.example.stardust_springboot.admin.dto;

import com.example.stardust_springboot.admin.audit.AdminAuditAction;
import com.example.stardust_springboot.ai.entity.*;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.knowledge.entity.KnowledgeBaseStatus;
import com.example.stardust_springboot.knowledge.entity.KnowledgeDocumentStatus;
import com.example.stardust_springboot.user.entity.UserStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public final class AdminDtos {
    private AdminDtos() {}

    public record Dashboard(long totalUsers, long todayNewUsers, long activeUsers,
                            long conversations, long messages, long aiRequests,
                            long totalTokens, long files, long storageBytes,
                            long ragDocuments, long systemErrors) {}

    public record UserView(String id, String email, String displayName, UserStatus status,
                           String banReason, Instant bannedUntil, Instant lastLoginAt,
                           Set<String> roles, long usedBytes, long quotaBytes,
                           long aiRequests, long aiTokens, Instant createdAt, Instant updatedAt) {}

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

    public record ConversationView(String id, String userId, String userEmail, String title,
                                   ConversationStatus status, long messageCount,
                                   Instant lastMessageAt, Instant createdAt) {}

    public record FileView(String id, String userId, String userEmail, String name,
                           String mime, long sizeBytes, UserFileStatus status,
                           boolean referenced, Instant createdAt) {}

    public record KnowledgeBaseView(String id, String userId, String userEmail, String name,
                                    KnowledgeBaseStatus status, Instant createdAt) {}
    public record KnowledgeDocumentView(String id, String knowledgeBaseId, String userId,
                                        String filename, KnowledgeDocumentStatus status,
                                        int chunkCount, String errorCode, Instant createdAt) {}

    public record ProviderView(String id, String code, String displayName, ProviderType type,
                               String baseUrl, boolean credentialConfigured, String configJson,
                               ProviderStatus status, ProviderHealthStatus health,
                               Instant updatedAt) {}
    public record ProviderRequest(@NotBlank @Pattern(regexp="[a-z0-9-]{2,64}") String code,
                                  @NotBlank @Size(max=100) String displayName,
                                  @NotNull ProviderType type,
                                  @NotBlank @Size(max=500) String baseUrl,
                                  @Size(max=255) String credentialRef,
                                  String configJson) {}
    public record StatusRequest(@NotNull Boolean enabled) {}

    public record ModelView(String id, String providerId, String code, String externalModelId,
                            String displayName, ModelType type, String capabilitiesJson,
                            Integer contextWindow, Integer maxOutputTokens,
                            BigDecimal inputPrice, BigDecimal outputPrice, String currency,
                            String parameterPolicyJson, ModelStatus status, int sortOrder,
                            Instant updatedAt) {}
    public record ModelRequest(@NotBlank String providerId,
                               @NotBlank @Pattern(regexp="[a-z0-9._-]{2,64}") String code,
                               @NotBlank @Size(max=128) String externalModelId,
                               @NotBlank @Size(max=100) String displayName,
                               @NotNull ModelType type, String capabilitiesJson,
                               @Positive Integer contextWindow, @Positive Integer maxOutputTokens,
                               @PositiveOrZero BigDecimal inputPrice,
                               @PositiveOrZero BigDecimal outputPrice,
                               @Size(min=3,max=3) String currency,
                               String parameterPolicyJson, int sortOrder) {}

    public record AiRequestView(String requestId, String userId, String conversationId,
                                String provider, String model, AiRequestStatus status,
                                Long latencyMs, Long promptTokens, Long completionTokens,
                                Long totalTokens, String errorCode, Instant createdAt) {}

    public record AuditView(String id, String adminId, String adminEmail,
                            AdminAuditAction action, String targetUserId,
                            String targetResourceType, String targetResourceId,
                            String ip, String userAgent, String requestId,
                            String metadataJson, Instant createdAt) {}
}
