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
    @Override
    public String toString() {
        return "CloudinaryProperties[cloudName=" + cloudName
                + ", apiKey=***, apiSecret=***]";
    }
}