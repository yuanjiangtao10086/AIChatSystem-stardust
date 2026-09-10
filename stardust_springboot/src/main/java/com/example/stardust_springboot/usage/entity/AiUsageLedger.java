package com.example.stardust_springboot.usage.entity;

import com.example.stardust_springboot.common.persistence.PublicIdEntity;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Immutable usage ledger entry. Rows are append-only: the repository exposes no update or delete method.
 * {@code tokenDelta}/{@code costDelta} express the change of total committed usage ({@code used + reserved}):
 * RESERVE adds the hold, SETTLE replaces the hold with real usage, RELEASE and negative ADJUST return quota.
 */
@Entity
@Table(name = "ai_usage_ledger")
public class AiUsageLedger extends PublicIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "ai_request_id", length = 26, columnDefinition = "CHAR(26)")
    private String aiRequestId;

    @Column(name = "operation_key", nullable = false, length = 80)
    private String operationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private UsageLedgerEntryType entryType;

    @Column(name = "token_delta", nullable = false)
    private long tokenDelta;

    @Column(name = "cost_delta", nullable = false, precision = 19, scale = 8)
    private BigDecimal costDelta;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    protected AiUsageLedger() {
    }

    public AiUsageLedger(AppUser user, String aiRequestId, String operationKey, UsageLedgerEntryType entryType,
                         long tokenDelta, BigDecimal costDelta, String currency) {
        this.user = user;
        this.aiRequestId = aiRequestId;
        this.operationKey = operationKey;
        this.entryType = entryType;
        this.tokenDelta = tokenDelta;
        this.costDelta = costDelta;
        this.currency = currency;
    }

    public AppUser getUser() {
        return user;
    }

    public String getAiRequestId() {
        return aiRequestId;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public UsageLedgerEntryType getEntryType() {
        return entryType;
    }

    public long getTokenDelta() {
        return tokenDelta;
    }

    public BigDecimal getCostDelta() {
        return costDelta;
    }

    public String getCurrency() {
        return currency;
    }
}
