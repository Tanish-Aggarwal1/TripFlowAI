package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripflow.backend.config.JwtProperties;

class JwtServiceTest {

	private static final String SECRET =
			"test-jwt-secret-must-be-at-least-256-bits-long-for-hmac-sha256";
	private static final long EXPIRY_MS = 3_600_000L;

	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		jwtService = new JwtService(new JwtProperties(SECRET, EXPIRY_MS));
	}

	@Test
	void generateToken_roundTripsUserIdAndEmail() {
		String token = jwtService.generateToken(99L, "user@example.com");

		assertThat(jwtService.extractUserId(token)).isEqualTo(99L);
		assertThat(jwtService.isValid(token)).isTrue();
	}

	@Test
	void getExpiry_returnsConfiguredOffset() {
		Instant before = Instant.now();
		String token = jwtService.generateToken(1L, "user@example.com");
		Instant expiry = jwtService.getExpiry(token);

		assertThat(expiry).isAfter(before.plus(Duration.ofMillis(EXPIRY_MS - 1000)));
		assertThat(expiry).isBefore(before.plus(Duration.ofMillis(EXPIRY_MS + 5000)));
	}

	@Test
	void isValid_rejectsTamperedToken() {
		String token = jwtService.generateToken(1L, "user@example.com");
		String tampered = token.substring(0, token.length() - 1) + "X";

		assertThat(jwtService.isValid(tampered)).isFalse();
	}

	@Test
	void isValid_rejectsExpiredToken() throws InterruptedException {
		JwtService shortLived = new JwtService(new JwtProperties(SECRET, 1L));
		String token = shortLived.generateToken(1L, "user@example.com");
		Thread.sleep(50);

		assertThat(shortLived.isValid(token)).isFalse();
	}
}