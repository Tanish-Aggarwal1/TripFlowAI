package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateStopPhotoRequestValidationTest {

	private final Validator validator;

	CreateStopPhotoRequestValidationTest() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
	}

	@Test
	void validRequest_hasNoViolations() {
		CreateStopPhotoRequest req = new CreateStopPhotoRequest("https://example.com/photo.jpg", "public-id", "caption");

		assertThat(validator.validate(req)).isEmpty();
	}

	@Test
	void urlOver2048Chars_isRejected() {
		CreateStopPhotoRequest req = new CreateStopPhotoRequest("x".repeat(2049), null, null);

		Set<ConstraintViolation<CreateStopPhotoRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("url"));
	}

	@Test
	void urlExactly2048Chars_isAccepted() {
		CreateStopPhotoRequest req = new CreateStopPhotoRequest("x".repeat(2048), null, null);

		assertThat(validator.validate(req)).isEmpty();
	}

	@Test
	void cloudinaryPublicIdOver255Chars_isRejected() {
		CreateStopPhotoRequest req = new CreateStopPhotoRequest("https://example.com/photo.jpg", "x".repeat(256), null);

		Set<ConstraintViolation<CreateStopPhotoRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("cloudinaryPublicId"));
	}

	@Test
	void captionOver500Chars_isRejected() {
		CreateStopPhotoRequest req = new CreateStopPhotoRequest("https://example.com/photo.jpg", null, "x".repeat(501));

		Set<ConstraintViolation<CreateStopPhotoRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("caption"));
	}
}
