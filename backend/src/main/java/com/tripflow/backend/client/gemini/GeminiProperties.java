package com.tripflow.backend.client.gemini;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Gemini client configuration.
 * API key lives in backend/.env (GEMINI_API_KEY) — never committed.
 */
@Validated
@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String model,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    /**
     * Overridden so the API key can never leak via logs, actuator, or any
     * future {@code log.debug("{}", geminiProperties)} call — records
     * auto-generate a toString() that includes every field verbatim otherwise.
     */
    @Override
    public String toString() {
        return "GeminiProperties[baseUrl=" + baseUrl
                + ", apiKey=" + mask(apiKey)
                + ", model=" + model
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