package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("app.ai.usage")
public record AiUsageProperties(long quotaTokens, BigDecimal quotaCost, String currency,
                                int outputReserveTokens, int periodDays,
                                long reconcileInitialDelayMs, long reconcileIntervalMs,
                                int reserveSweepWindowHours) {
}
