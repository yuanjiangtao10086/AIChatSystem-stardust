package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.ai.service")
public record AiServiceProperties(URI baseUrl, String token, Duration connectTimeout,
                                  Duration requestTimeout) {
    public URI streamUri() {
        return baseUrl.resolve("/internal/chat/stream");
    }
}
