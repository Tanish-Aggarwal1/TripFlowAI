package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		String token = jwtService.generateToken(99L, "user@example.com", 0);

		assertThat(jwtService.extractUserId(token)).isEqualTo(99L);
		assertThat(jwtService.isValid(token)).isTrue();
	}

	@Test
	void extractEmail_returnsEmailClaim() {
		String token = jwtService.generateToken(1L, "user@example.com", 0);

		assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
	}

	@Test
	void extractTokenVersion_returnsTokenVersionClaim() {
		String token = jwtService.generateToken(1L, "user@example.com", 3);

		assertThat(jwtService.extractTokenVersion(token)).isEqualTo(3);
	}

	@Test
	void getExpiry_returnsConfiguredOffset() {
		Instant before = Instant.now();
		String token = jwtService.generateToken(1L, "user@example.com", 0);
		Instant expiry = jwtService.getExpiry(token);

		assertThat(expiry).isAfter(before.plus(Duration.ofMillis(EXPIRY_MS - 1000)));
		assertThat(expiry).isBefore(before.plus(Duration.ofMillis(EXPIRY_MS + 5000)));
	}

	@Test
	void isValid_rejectsTamperedToken() {
		String token = jwtService.generateToken(1L, "user@example.com", 0);

		// Flip a character inside the signature segment, not at the very end of
		// the token. Base64url only guarantees every bit is significant within a
		// *full* 4-char/3-byte group — the final group of a 32-byte HMAC-SHA256
		// signature is partial (2 leftover bytes), so its last character carries
		// unused padding bits. Flipping exactly that last character can, purely
		// by chance depending on the token's actual bytes, decode to the same
		// signature and leave the token valid — a flaky false pass. Mutating the
		// first character of the signature segment is always inside a full
		// group, so the decoded bytes are guaranteed to change.
		int lastDot = token.lastIndexOf('.');
		char[] chars = token.toCharArray();
		int sigStart = lastDot + 1;
		chars[sigStart] = chars[sigStart] == 'A' ? 'B' : 'A';
		String tampered = new String(chars);

		assertThat(jwtService.isValid(tampered)).isFalse();
	}

	@Test
	void isValid_rejectsExpiredToken() throws InterruptedException {
		JwtService shortLived = new JwtService(new JwtProperties(SECRET, 1L));
		String token = shortLived.generateToken(1L, "user@example.com", 0);
		Thread.sleep(50);

		assertThat(shortLived.isValid(token)).isFalse();
	}

	@Test
	void constructor_rejectsSecretShorterThan32Bytes() {
		assertThatThrownBy(() -> new JwtProperties("too-short", EXPIRY_MS))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}

	@Test
	void constructor_rejectsNullSecret() {
		assertThatThrownBy(() -> new JwtProperties(null, EXPIRY_MS))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void constructor_rejectsLongButLowEntropySecret() {
		String repeatedChar = "a".repeat(36); // 36 bytes, satisfies length but not variety
		assertThatThrownBy(() -> new JwtProperties(repeatedChar, EXPIRY_MS))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("distinct characters");
	}

	@Test
	void toString_masksSecret() {
		String rendered = new JwtProperties(SECRET, EXPIRY_MS).toString();

		assertThat(rendered).doesNotContain(SECRET);
		assertThat(rendered).contains("****" + SECRET.substring(SECRET.length() - 4));
		assertThat(rendered).contains("expirationMs=" + EXPIRY_MS);
	}
}
