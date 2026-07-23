package com.tripflow.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class JwtPropertiesTest {

	private static final String VALID_SECRET =
			"test-jwt-secret-must-be-at-least-256-bits-long-for-hmac-sha256";
	private static final long VALID_EXPIRY_MS = 3_600_000L;

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void validSecretAndExpiry_constructsSuccessfully() {
		JwtProperties props = new JwtProperties(VALID_SECRET, VALID_EXPIRY_MS);

		assertThat(props.secret()).isEqualTo(VALID_SECRET);
		assertThat(props.expirationMs()).isEqualTo(VALID_EXPIRY_MS);
		assertThat(validator.validate(props)).isEmpty();
	}

	@Test
	void secretUnder32Bytes_throwsIllegalStateException() {
		assertThatThrownBy(() -> new JwtProperties("too-short-secret", VALID_EXPIRY_MS))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 32 bytes");
	}

	@Test
	void nullSecret_failsBeanValidation() {
		// The compact constructor skips the byte-length check on null;
		// @NotBlank (as Spring applies it during real property binding) is
		// what rejects a missing secret. Verified directly against the
		// validator here, since a bare `new JwtProperties(...)` call doesn't
		// trigger it.
		JwtProperties props = new JwtProperties(null, VALID_EXPIRY_MS);

		Set<ConstraintViolation<JwtProperties>> violations = validator.validate(props);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("secret"));
	}

	@Test
	void nonPositiveExpiry_failsBeanValidation() {
		JwtProperties props = new JwtProperties(VALID_SECRET, 0L);

		Set<ConstraintViolation<JwtProperties>> violations = validator.validate(props);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("expirationMs"));
	}
}