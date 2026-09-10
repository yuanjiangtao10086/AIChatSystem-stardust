package com.example.stardust_springboot.ai.entity;

import com.example.stardust_springboot.common.persistence.PublicIdEntity;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_model",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_model_provider_external",
                columnNames = {"provider_id", "external_model_id"}),
        indexes = {
                @Index(name = "idx_ai_model_status_type_sort", columnList = "status, model_type, sort_order"),
                @Index(name = "idx_ai_model_updated", columnList = "updated_at, id")
        })
public class AiModel extends PublicIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "external_model_id", nullable = false, length = 128)
    private String externalModelId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 24)
    private ModelType modelType;

    @Column(name = "capabilities_json", nullable = false, columnDefinition = "TEXT")
    private String capabilitiesJson = "[]";

    @Column(name = "context_window")
    private Integer contextWindow;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Column(name = "input_price", precision = 19, scale = 8)
    private BigDecimal inputPrice;

    @Column(name = "output_price", precision = 19, scale = 8)
    private BigDecimal outputPrice;

    @Column(length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "price_effective_from")
    private Instant priceEffectiveFrom;

    @Column(name = "parameter_policy_json", columnDefinition = "TEXT")
    private String parameterPolicyJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModelStatus status = ModelStatus.DISABLED;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Platform default for this {@link ModelType}; at most one row per type may be {@code true}. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected AiModel() {
    }

    public AiModel(AiProvider provider, String code, String externalModelId,
                   String displayName, ModelType modelType) {
        this.provider = provider;
        this.code = code;
        this.externalModelId = externalModelId;
        this.displayName = displayName;
        this.modelType = modelType;
    }

    public String getCode() {
        return code;
    }

    public String getExternalModelId() {
        return externalModelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ModelType getModelType() {
        return modelType;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public ModelStatus getStatus() {
        return status;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void enable() {
        status = ModelStatus.ENABLED;
    }

    public String getCapabilitiesJson() { return capabilitiesJson; }
    public BigDecimal getInputPrice() { return inputPrice; }
    public BigDecimal getOutputPrice() { return outputPrice; }
    public String getCurrency() { return currency; }
    public String getParameterPolicyJson() { return parameterPolicyJson; }
    public int getSortOrder() { return sortOrder; }

    public void update(String displayName, String externalModelId, ModelType modelType,
                       String capabilitiesJson, Integer contextWindow, Integer maxOutputTokens,
                       BigDecimal inputPrice, BigDecimal outputPrice, String currency,
                       String parameterPolicyJson, int sortOrder) {
        this.displayName = displayName;
        this.externalModelId = externalModelId;
        this.modelType = modelType;
        this.capabilitiesJson = capabilitiesJson;
        this.contextWindow = contextWindow;
        this.maxOutputTokens = maxOutputTokens;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
        this.currency = currency;
        this.parameterPolicyJson = parameterPolicyJson;
        this.sortOrder = sortOrder;
    }

    public void disable() { status = ModelStatus.DISABLED; }

    public boolean isDefault() { return isDefault; }

    public void markDefault() { isDefault = true; }

    public void clearDefault() { isDefault = false; }

    /** Used by the console reorder action to swap positions with a sibling model. */
    public void moveTo(int sortOrder) { this.sortOrder = sortOrder; }
}
