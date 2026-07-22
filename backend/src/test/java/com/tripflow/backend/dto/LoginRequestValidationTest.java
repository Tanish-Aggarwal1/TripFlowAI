package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class LoginRequestValidationTest {

	private final Validator validator;

	LoginRequestValidationTest() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
	}

	@Test
	void malformedEmail_isRejected() {
		LoginRequest req = new LoginRequest("not-an-email", "password123");

		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
	}

	@Test
	void blankEmail_isRejected() {
		LoginRequest req = new LoginRequest("", "password123");

		Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
	}

	@Test
	void validRequest_hasNoViolations() {
		LoginRequest req = new LoginRequest("user@example.com", "password123");

		assertThat(validator.validate(req)).isEmpty();
	}
}
