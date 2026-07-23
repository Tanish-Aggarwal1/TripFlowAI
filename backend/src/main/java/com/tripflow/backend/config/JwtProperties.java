package com.tripflow.backend.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * JWT signing configuration, bound from app.jwt.* (see application.properties,
 * which maps app.jwt.secret -> JWT_SECRET and app.jwt.expiration-ms -> JWT_EXPIRY_MS,
 * both set in backend/.env — never committed).
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "app.jwt.secret is missing — set JWT_SECRET in backend/.env")
        String secret,

        @Positive(message = "app.jwt.expiration-ms must be a positive number of milliseconds")
        long expirationMs

) {
    private static final int MIN_SECRET_BYTES = 32; // HS256 requires a >=256-bit key

    public JwtProperties {
        if (secret != null) {
            int secretBytes = secret.getBytes(StandardCharsets.UTF_8).length;
            if (secretBytes < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "app.jwt.secret must be at least " + MIN_SECRET_BYTES
                                + " bytes for HS256 signing (got " + secretBytes
                                + " bytes). Generate one in PowerShell: "
                                + "[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 })) "
                                + "and set it as JWT_SECRET in backend/.env.");
            }
        }
        // null/blank secret is caught by @NotBlank at Spring's bean-validation
        // step, which runs after this constructor during property binding.
    }
}