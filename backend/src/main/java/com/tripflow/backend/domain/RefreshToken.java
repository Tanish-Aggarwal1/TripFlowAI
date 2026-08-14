package com.tripflow.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One issued refresh token, stored as its SHA-256 hex digest only — the presentable value
 * exists solely in the httpOnly cookie handed to the browser.
 *
 * <p>{@code userId} is a plain id column rather than a {@code @ManyToOne}: the only access
 * patterns are a hash lookup and a bulk update keyed by user id, so an association would buy
 * nothing and cost a lazy proxy.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
