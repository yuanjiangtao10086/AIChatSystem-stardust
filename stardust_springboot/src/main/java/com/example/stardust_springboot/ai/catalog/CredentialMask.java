package com.example.stardust_springboot.ai.catalog;

/**
 * Builds the only representation of a provider secret that may leave the server:
 * {@code sk-****abcd}.
 *
 * <p>Short values degrade to {@code ****} so a mask can never reconstruct most of a secret, and the
 * input is never written to logs, audit metadata or error messages.
 */
public final class CredentialMask {

    private static final int MIN_REVEALING_LENGTH = 12;

    private CredentialMask() {
    }

    public static String of(String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        String value = secret.strip();
        if (value.length() < MIN_REVEALING_LENGTH) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
