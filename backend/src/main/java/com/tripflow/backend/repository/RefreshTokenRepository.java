package com.tripflow.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every still-live refresh token a user holds, returning the number of rows
     * affected. Runs on a compromise signal (D-03 reuse detection), where a partial revoke
     * would be worse than none — hence one bulk statement rather than a load-and-loop that
     * could fail halfway through and leave some device still signed in.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :revokedAt "
            + "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
