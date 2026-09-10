package com.example.stardust_springboot.usage.dto;

import com.example.stardust_springboot.usage.entity.AiUsageAccount;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageView(long quotaTokens, long usedTokens, long reservedTokens, long availableTokens,
                        BigDecimal quotaCost, BigDecimal usedCost, BigDecimal reservedCost,
                        String currency, Instant periodStart, Instant periodEnd) {
    public static UsageView from(AiUsageAccount account) {
        return new UsageView(account.getQuotaTokens(), account.getUsedTokens(), account.getReservedTokens(),
                account.getAvailableTokens(), account.getQuotaCost(), account.getUsedCost(),
                account.getReservedCost(), account.getCurrency(), account.getPeriodStart(), account.getPeriodEnd());
    }
}
