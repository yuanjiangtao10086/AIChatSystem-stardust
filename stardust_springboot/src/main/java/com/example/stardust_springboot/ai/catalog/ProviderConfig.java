package com.example.stardust_springboot.ai.catalog;

/**
 * Non-secret provider options, persisted as {@code ai_provider.non_secret_config_json} (ADR-016).
 *
 * <p>Timeouts are deliberately kept here instead of in a new column: they are non-secret provider
 * metadata. Runtime enforcement still belongs to the AI service settings (ADR-032); the console
 * maintains the declared contract so operators can compare it with the deployed value.
 */
public record ProviderConfig(Integer timeoutSeconds, Integer connectTimeoutSeconds) {

    public static final ProviderConfig NONE = new ProviderConfig(null, null);
}
