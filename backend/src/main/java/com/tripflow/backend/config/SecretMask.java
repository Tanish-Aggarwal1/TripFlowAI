package com.tripflow.backend.config;

/**
 * Shared last-4-characters masking for {@code @ConfigurationProperties} records that hold
 * an API key (e.g. {@code OrsProperties}, {@code GeminiProperties}). Extracted because the
 * same logic was previously copy-pasted into each record's {@code toString()} override.
 */
public final class SecretMask {

    private SecretMask() {
    }

    public static String mask(String key) {
        if (key == null || key.length() < 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }
}
