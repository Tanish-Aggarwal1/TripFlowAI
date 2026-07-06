package com.tripflow.backend.dto;

import java.time.Instant;

public record AuthResponse(
		String token,
        String tokenType,
        Long userId,
        String username,
        Instant expiresAt

		) {

}
