package com.tripflow.backend.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing configuration. Bound from app.jwt.secret / app.jwt.expiration-ms,
 * which resolve from the JWT_SECRET / JWT_EXPIRY_MS env vars (see backend/.env).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs) {
	private static final int MIN_SECRET_BYTES = 32; // HS256 requires a >= 256-bit key

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be set and at least 32 bytes (256 bits) long for HS256 "
                            + "— check JWT_SECRET in backend/.env");
        }
    }
}
