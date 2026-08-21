package com.tripflow.backend.client.mapbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.tripflow.backend.config.SecretMask;

/**
 * Mapbox Static Images API client configuration (EXPORT-02 / D-03).
 * Backend-scoped access token lives in backend/.env (MAPBOX_TOKEN) — never committed,
 * and distinct from the frontend's browser-scoped Mapbox token of the same name.
 */
@ConfigurationProperties(prefix = "mapbox")
public record MapboxProperties(
        String baseUrl,
        String accessToken,
        String style,
        Duration connectTimeout,
        Duration readTimeout
) {

    /**
     * Overridden so the access token can never leak via logs, actuator, or any
     * future {@code log.debug("{}", mapboxProperties)} call — records
     * auto-generate a toString() that includes every field verbatim otherwise.
     */
    @Override
    public String toString() {
        return "MapboxProperties[baseUrl=" + baseUrl
                + ", accessToken=" + SecretMask.mask(accessToken)
                + ", style=" + style
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
