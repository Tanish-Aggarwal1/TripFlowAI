package com.tripflow.backend.client.ors;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenRouteService client configuration.
 * API key lives in backend/.env (ORS_API_KEY) — never committed.
 */
@ConfigurationProperties(prefix = "ors")
public record OrsProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    /**
     * Overridden so the API key can never leak via logs, actuator, or any
     * future {@code log.debug("{}", orsProperties)} call — records
     * auto-generate a toString() that includes every field verbatim otherwise.
     */
    @Override
    public String toString() {
        return "OrsProperties[baseUrl=" + baseUrl
                + ", apiKey=" + mask(apiKey)
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }

    private static String mask(String key) {
        if (key == null || key.length() < 4) {
            return "****";
        }
        return "****" + key.substring(key.length() - 4);
    }
}
