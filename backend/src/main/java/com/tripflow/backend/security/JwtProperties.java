package com.tripflow.backend.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.tripflow.backend.config.SecretMask;

/**
 * JWT signing configuration. Bound from app.jwt.secret / app.jwt.expiration-ms,
 * which resolve from the JWT_SECRET / JWT_EXPIRY_MS env vars (see backend/.env).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs) {
	private static final int MIN_SECRET_BYTES = 32; // HS256 requires a >= 256-bit key
	// A generated secret (e.g. `openssl rand -base64 48`) uses most of its alphabet; a typed,
	// memorable passphrase reuses a handful of characters even when it's 32+ bytes long. This
	// doesn't measure real entropy, but it catches the realistic failure mode cheaply.
	private static final int MIN_DISTINCT_CHARS = 16;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be set and at least 32 bytes (256 bits) long for HS256 "
                            + "— check JWT_SECRET in backend/.env");
        }
        long distinctChars = secret.chars().distinct().count();
        if (distinctChars < MIN_DISTINCT_CHARS) {
            throw new IllegalStateException(
                    "app.jwt.secret has too little variety (" + distinctChars + " distinct characters) "
                            + "to be a real secret — generate one with `openssl rand -base64 48`, don't type one");
        }
    }

    /**
     * Overridden so the HMAC signing secret can never leak via logs, actuator, or any
     * future {@code log.debug("{}", jwtProperties)} call — records auto-generate a
     * toString() that includes every field verbatim otherwise.
     */
    @Override
    public String toString() {
        return "JwtProperties[secret=" + SecretMask.mask(secret) + ", expirationMs=" + expirationMs + "]";
    }
}
