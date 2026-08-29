package com.tripflow.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    /**
     * Single-column lookup for {@code JwtAuthFilter}'s per-request revocation check — avoids
     * hydrating the whole entity on the hot path. Empty means the user no longer exists, which
     * the filter treats the same as a version mismatch (M-7): reject.
     */
    @Query("select u.tokenVersion from User u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);

    /**
     * Invalidates every access token already issued to this user. Wired into
     * {@code RefreshTokenService.rotate}'s reuse-detection branch (D-03), the one existing
     * "treat this user as compromised" event in the codebase.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);
}

