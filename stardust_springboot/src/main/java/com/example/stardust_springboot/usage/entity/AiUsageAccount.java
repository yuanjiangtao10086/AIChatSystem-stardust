package com.example.stardust_springboot.usage.entity;

import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/** Per-user AI usage account. Concurrency view only; {@code ai_usage_ledger} is the reconciliation fact. */
@Entity
@Table(name = "ai_usage_account")
@EntityListeners(AuditingEntityListener.class)
public class AiUsageAccount {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "quota_tokens", nullable = false)
    private long quotaTokens;

    @Column(name = "used_tokens", nullable = false)
    private long usedTokens;

    @Column(name = "reserved_tokens", nullable = false)
    private long reservedTokens;

    @Column(name = "quota_cost", nullable = false, precision = 19, scale = 8)
    private BigDecimal quotaCost;

    @Column(name = "used_cost", nullable = false, precision = 19, scale = 8)
    private BigDecimal usedCost;

    @Column(name = "reserved_cost", nullable = false, precision = 19, scale = 8)
    private BigDecimal reservedCost;

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AiUsageAccount() {
    }

    public AiUsageAccount(AppUser user, long quotaTokens, BigDecimal quotaCost, String currency,
                          Instant periodStart, Instant periodEnd) {
        this.user = user;
        this.quotaTokens = quotaTokens;
        this.quotaCost = quotaCost;
        this.currency = currency;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.usedCost = BigDecimal.ZERO;
        this.reservedCost = BigDecimal.ZERO;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(periodEnd);
    }

    /** Starts a new quota period. Consumed totals are derived data and are rebuilt from the ledger when needed. */
    public void rollover(Instant start, Instant end) {
        this.usedTokens = 0;
        this.reservedTokens = 0;
        this.usedCost = BigDecimal.ZERO;
        this.reservedCost = BigDecimal.ZERO;
        this.periodStart = start;
        this.periodEnd = end;
    }

    public boolean canReserve(long tokens, BigDecimal cost) {
        return usedTokens + reservedTokens + tokens <= quotaTokens
                && usedCost.add(reservedCost).add(cost).compareTo(quotaCost) <= 0;
    }

    public void reserve(long tokens, BigDecimal cost) {
        this.reservedTokens += tokens;
        this.reservedCost = this.reservedCost.add(cost);
    }

    /** Converts a reservation into real consumption; a smaller actual usage refunds the difference. */
    public void settle(long reservedTokens, BigDecimal reservedCost, long actualTokens, BigDecimal actualCost) {
        this.reservedTokens = Math.max(0, this.reservedTokens - reservedTokens);
        this.reservedCost = this.reservedCost.subtract(reservedCost).max(BigDecimal.ZERO);
        this.usedTokens += Math.max(0, actualTokens);
        this.usedCost = this.usedCost.add(actualCost.max(BigDecimal.ZERO));
    }

    /** Administrative correction of already consumed usage; a negative delta returns quota. */
    public void adjust(long tokens, BigDecimal cost) {
        this.usedTokens = Math.max(0, this.usedTokens + tokens);
        this.usedCost = this.usedCost.add(cost).max(BigDecimal.ZERO);
    }

    public void release(long tokens, BigDecimal cost) {
        this.reservedTokens = Math.max(0, this.reservedTokens - tokens);
        this.reservedCost = this.reservedCost.subtract(cost).max(BigDecimal.ZERO);
    }

    public long getQuotaTokens() {
        return quotaTokens;
    }

    public long getUsedTokens() {
        return usedTokens;
    }

    public long getReservedTokens() {
        return reservedTokens;
    }

    public long getAvailableTokens() {
        return Math.max(0, quotaTokens - usedTokens - reservedTokens);
    }

    public BigDecimal getQuotaCost() {
        return quotaCost;
    }

    public BigDecimal getUsedCost() {
        return usedCost;
    }

    public BigDecimal getReservedCost() {
        return reservedCost;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public AppUser getUser() {
        return user;
    }
}
