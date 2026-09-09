package com.example.stardust_springboot.ai.entity;

import com.example.stardust_springboot.common.persistence.PublicIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_provider", indexes = {
        @Index(name = "idx_ai_provider_status_type", columnList = "status, provider_type"),
        @Index(name = "idx_ai_provider_updated", columnList = "updated_at, id")
})
public class AiProvider extends PublicIdEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "credential_ref", length = 255)
    private String credentialRef;

    @Column(name = "non_secret_config_json", columnDefinition = "TEXT")
    private String nonSecretConfigJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProviderStatus status = ProviderStatus.DISABLED;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 16)
    private ProviderHealthStatus healthStatus = ProviderHealthStatus.UNKNOWN;

    @Column(name = "last_health_checked_at")
    private Instant lastHealthCheckedAt;

    protected AiProvider() {
    }

    public AiProvider(String code, String displayName, ProviderType providerType,
                      String baseUrl, String credentialRef) {
        this.code = code;
        this.displayName = displayName;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.credentialRef = credentialRef;
    }

    public String getCode() {
        return code;
    }

    public ProviderStatus getStatus() {
        return status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ProviderType getProviderType() { return providerType; }
    public String getBaseUrl() { return baseUrl; }
    public boolean hasCredential() { return credentialRef != null && !credentialRef.isBlank(); }
    public String getNonSecretConfigJson() { return nonSecretConfigJson; }
    public ProviderHealthStatus getHealthStatus() { return healthStatus; }
    public Instant getLastHealthCheckedAt() { return lastHealthCheckedAt; }

    public void update(String displayName, ProviderType providerType, String baseUrl,
                       String credentialRef, String nonSecretConfigJson) {
        this.displayName = displayName;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        if (credentialRef != null) this.credentialRef = credentialRef;
        this.nonSecretConfigJson = nonSecretConfigJson;
    }

    public void enable() {
        status = ProviderStatus.ENABLED;
    }

    public void disable() { status = ProviderStatus.DISABLED; }
}
