package com.tripflow.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
	
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = jwtProperties.expirationMs();
    }

    public String generateToken(Long userId, String email, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public Instant getExpiry(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    /** Null means the token predates this claim (M-7 rollout) — callers must treat that as invalid. */
    public Integer extractTokenVersion(String token) {
        return parseClaims(token).get("tv", Integer.class);
    }

    public boolean isValid(String token) {
        return parseIfValid(token).isPresent();
    }

    /**
     * Parses and signature-verifies the token exactly once, for callers (namely
     * JwtAuthFilter) that would otherwise re-verify the same token multiple times
     * per request via isValid/extractUserId/extractEmail.
     */
    public Optional<Claims> parseIfValid(String token) {
        try {
            return Optional.of(parseClaims(token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
