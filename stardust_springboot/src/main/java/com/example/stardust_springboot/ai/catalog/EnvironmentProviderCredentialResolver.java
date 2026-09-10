package com.example.stardust_springboot.ai.catalog;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves {@code env:NAME} references against the Spring environment, which includes the process
 * environment variables shared with the AI service.
 *
 * <p>Other schemes ({@code vault:}, {@code sm:}, ...) are accepted as references but cannot be read
 * from this process, so they resolve to empty: the provider still counts as "credential
 * configured", it simply has no mask to show. Adding a scheme means adding a secret client, not
 * inventing new crypto.
 */
@Component
public class EnvironmentProviderCredentialResolver implements ProviderCredentialResolver {

    private static final String ENV_PREFIX = "env:";

    private final Environment environment;

    public EnvironmentProviderCredentialResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolve(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Optional.empty();
        }
        String reference = credentialRef.trim();
        if (!reference.regionMatches(true, 0, ENV_PREFIX, 0, ENV_PREFIX.length())) {
            return Optional.empty();
        }
        String name = reference.substring(ENV_PREFIX.length()).trim();
        if (name.isEmpty()) {
            return Optional.empty();
        }
        try {
            String value = environment.getProperty(name);
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
