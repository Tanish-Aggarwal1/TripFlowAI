package com.tripflow.backend.client.ors;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.tripflow.backend.config.SecretMask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * OpenRouteService client configuration.
 * API key lives in backend/.env (ORS_API_KEY) — never committed.
 */
@Validated
@ConfigurationProperties(prefix = "ors")
public record OrsProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    /**
     * Overridden so the API key can never leak via logs, actuator, or any
     * future {@code log.debug("{}", orsProperties)} call — records
     * auto-generate a toString() that includes every field verbatim otherwise.
     */
    @Override
    public String toString() {
        return "OrsProperties[baseUrl=" + baseUrl
                + ", apiKey=" + SecretMask.mask(apiKey)
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
