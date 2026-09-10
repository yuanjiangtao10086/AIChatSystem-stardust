package com.example.stardust_springboot.usage.breakdown;

/**
 * One row of a usage breakdown. {@code key} is the bucket label: an ISO date for {@code DAY},
 * the model code for {@code MODEL}, or the provider code for {@code PROVIDER}.
 */
public record UsageBreakdownItem(String key, long requestCount, long promptTokens,
                                 long completionTokens, long totalTokens) {
}
