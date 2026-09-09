package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("app.storage")
public record StorageProperties(
        String provider,
        Path localRoot,
        long maxFileBytes,
        long defaultQuotaBytes
) {
    public StorageProperties {
        provider = provider == null ? "local" : provider;
        localRoot = localRoot == null ? Path.of("./data/files") : localRoot;
        maxFileBytes = maxFileBytes <= 0 ? 25L * 1024 * 1024 : maxFileBytes;
        defaultQuotaBytes = defaultQuotaBytes <= 0 ? 1024L * 1024 * 1024 : defaultQuotaBytes;
    }
}
