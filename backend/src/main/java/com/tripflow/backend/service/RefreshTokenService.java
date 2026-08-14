package com.tripflow.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.config.RefreshTokenProperties;
import com.tripflow.backend.domain.RefreshToken;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.exception.InvalidRefreshTokenException;
import com.tripflow.backend.repository.RefreshTokenRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Issues and rotates refresh tokens. The raw token value leaves this class exactly once, on
 * the return path to the controller that puts it in a Set-Cookie header — it is never logged,
 * and only its SHA-256 digest is persisted.
 *
 * <p>Deliberately free of servlet and Spring HTTP types (ArchUnit
 * {@code services_must_not_have_http_concerns}), which is why cookie construction lives in
 * {@code AuthController} rather than here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenProperties properties;

    public record IssuedToken(String rawToken, Instant expiresAt) {
    }

    public record RotatedSession(String accessToken, Instant accessExpiresAt, String rawRefreshToken,
            Instant refreshExpiresAt) {
    }

    @Transactional
    public IssuedToken issue(Long userId) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(properties.expirationDays(), ChronoUnit.DAYS);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(expiresAt);
        refreshTokenRepository.save(entity);

        log.info("Refresh token issued userId={}", userId);
        return new IssuedToken(rawToken, expiresAt);
    }

    @Transactional
    public RotatedSession rotate(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        // A row whose usedAt is already set is rejected as invalid here. Reuse of a redeemed
        // token is a compromise signal and gets the D-03 mass-revoke response in plan 01-03;
        // until then this branch is the fail-closed placeholder, never fail-open.
        if (stored.getUsedAt() != null || stored.getRevokedAt() != null
                || stored.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Refresh token rejected userId={} (already redeemed, revoked, or expired)", stored.getUserId());
            throw new InvalidRefreshTokenException();
        }

        stored.setUsedAt(Instant.now());
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail());
        IssuedToken replacement = issue(user.getId());

        log.info("Refresh token rotated userId={}", user.getId());
        return new RotatedSession(accessToken, jwtService.getExpiry(accessToken), replacement.rawToken(),
                replacement.expiresAt());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Hex-formatted SHA-256, exactly 64 characters to match the CHAR(64) column. */
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }
}
