package com.tripflow.backend.client.cloudinary;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.cloudinary")
public record CloudinaryProperties(
        @NotBlank String cloudName,
        @NotBlank String apiKey,
        @NotBlank String apiSecret
) {
    // @NotBlank alone doesn't catch a missing env var here: Spring's @ConfigurationProperties
    // binder resolves ${VAR} placeholders with ignoreUnresolvablePlaceholders=true (verified
    // empirically against this Boot version), so an unset CLOUDINARY_* env var binds the
    // literal, non-blank text "${CLOUDINARY_CLOUD_NAME}" instead of failing (SCRUM-461).
    public CloudinaryProperties {
        requireResolved("cloud-name", cloudName);
        requireResolved("api-key", apiKey);
        requireResolved("api-secret", apiSecret);
    }

    private static void requireResolved(String property, String value) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            throw new IllegalStateException("app.cloudinary." + property
                    + " is an unresolved placeholder (" + value + ") — its environment variable is not set");
        }
    }

    @Override
    public String toString() {
        return "CloudinaryProperties[cloudName=" + cloudName
                + ", apiKey=***, apiSecret=***]";
    }
}