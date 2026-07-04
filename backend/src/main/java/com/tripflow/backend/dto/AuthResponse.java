package com.tripflow.backend.dto;

public record AuthResponse(
		String token,
        String tokenType,
        Long userId,
        String username
		) {

}
