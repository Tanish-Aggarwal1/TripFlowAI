package com.tripflow.backend.client.cloudinary;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cloudinary account credentials, bound from {@code app.cloudinary.*}.
 * <p>
 * Values come from environment variables in production
 * (see {@code application-prod.properties}). The API secret is never
 * logged and never returned in an HTTP response.
 */
@Validated
@ConfigurationProperties("app.cloudinary")
public record CloudinaryProperties(
        @NotBlank String cloudName,
        @NotBlank String apiKey,
        @NotBlank String apiSecret
) {
    @Override
    public String toString() {
        // Defensive: never leak the secret if this record ends up in a log line.
        return "CloudinaryProperties[cloudName=" + cloudName
                + ", apiKey=***, apiSecret=***]";
    }
}