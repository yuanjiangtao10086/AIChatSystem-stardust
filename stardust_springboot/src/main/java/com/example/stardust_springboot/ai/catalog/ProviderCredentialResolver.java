package com.example.stardust_springboot.ai.catalog;

import java.util.Optional;

/**
 * Resolves a provider credential <em>reference</em> to its secret value at runtime.
 *
 * <p>The platform never persists provider API keys (ADR-016): {@code ai_provider.credential_ref}
 * holds a reference, and the secret itself lives in the deployment secret source. The console only
 * needs to answer "is this reference configured, and does the deployed value look right?", so the
 * resolved value is used to build a mask and is never returned, logged, or audited.
 */
public interface ProviderCredentialResolver {

    /**
     * @return the resolved secret, or empty when the reference is absent or this process cannot
     *         read it. Implementations must not throw and must not log the value.
     */
    Optional<String> resolve(String credentialRef);
}
