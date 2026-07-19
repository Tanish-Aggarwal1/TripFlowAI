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
) {}
