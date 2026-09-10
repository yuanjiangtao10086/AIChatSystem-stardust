package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(
        @DefaultValue("memory") String store,
        @DefaultValue("5") int loginMaxFailures,
        @DefaultValue("PT10M") Duration loginWindow,
        @DefaultValue("PT15M") Duration loginBlock,
        @DefaultValue("8") int registerMaxAttempts,
        @DefaultValue("PT10M") Duration registerWindow,
        @DefaultValue("PT30M") Duration registerBlock,
        @DefaultValue("20") int refreshMaxAttempts,
        @DefaultValue("PT5M") Duration refreshWindow,
        @DefaultValue("PT15M") Duration refreshBlock,
        @DefaultValue("30") int uploadMaxRequests,
        @DefaultValue("PT1M") Duration uploadWindow,
        @DefaultValue("PT5M") Duration uploadBlock,
        List<String> trustedProxies) {
}
