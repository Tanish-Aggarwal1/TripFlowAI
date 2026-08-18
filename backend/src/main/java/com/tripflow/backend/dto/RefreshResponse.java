package com.tripflow.backend.dto;

import java.time.Instant;

/** Body of a successful {@code POST /api/auth/refresh}. Deliberately narrower than
 * {@link AuthResponse}: the SPA already holds the user id and username from login. */
public record RefreshResponse(String token, String tokenType, Instant expiresAt) {
}
