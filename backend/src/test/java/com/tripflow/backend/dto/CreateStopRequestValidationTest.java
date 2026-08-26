package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateStopRequestValidationTest {

	private final Validator validator;

	CreateStopRequestValidationTest() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
	}

	@Test
	void validRequest_hasNoViolations() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);

		assertThat(validator.validate(req)).isEmpty();
	}

	@Test
	void latitudeAbove90_isRejected() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 90.1, -79.9, null, null, null);

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("latitude"));
	}

	@Test
	void latitudeBelowNegative90_isRejected() {
		CreateStopRequest req = new CreateStopRequest("Cottage", -90.1, -79.9, null, null, null);

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("latitude"));
	}

	@Test
	void longitudeAbove180_isRejected() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 45.0, 180.1, null, null, null);

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("longitude"));
	}

	@Test
	void longitudeBelowNegative180_isRejected() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 45.0, -180.1, null, null, null);

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("longitude"));
	}

	@Test
	void boundaryCoordinates_areAccepted() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 90.0, 180.0, null, null, null);

		assertThat(validator.validate(req)).isEmpty();

		CreateStopRequest reqNegative = new CreateStopRequest("Cottage", -90.0, -180.0, null, null, null);

		assertThat(validator.validate(reqNegative)).isEmpty();
	}

	@Test
	void nameOver200Chars_isRejected() {
		CreateStopRequest req = new CreateStopRequest("x".repeat(201), 45.0, -79.9, null, null, null);

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
	}

	@Test
	void addressOver300Chars_isRejected() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 45.0, -79.9, "x".repeat(301), null, null);

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("address"));
	}

	@Test
	void externalPlaceIdOver150Chars_isRejected() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 45.0, -79.9, null, "x".repeat(151), null);

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("externalPlaceId"));
	}

	@Test
	void notesOver2000Chars_isRejected() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, "x".repeat(2001));

		Set<ConstraintViolation<CreateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("notes"));
	}

	@Test
	void notesExactly2000Chars_isAccepted() {
		CreateStopRequest req = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, "x".repeat(2000));

		assertThat(validator.validate(req)).isEmpty();
	}

	@Test
	void updateStopRequest_notesOver2000Chars_isRejected() {
		UpdateStopRequest req = new UpdateStopRequest("Cottage", 45.0, -79.9, null, null, "x".repeat(2001), null);

		Set<ConstraintViolation<UpdateStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("notes"));
	}

	@Test
	void upsertStopRequest_notesOver2000Chars_isRejected() {
		UpsertStopRequest req = new UpsertStopRequest(1L, "Cottage", 45.0, -79.9, null, null, "x".repeat(2001));

		Set<ConstraintViolation<UpsertStopRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("notes"));
	}
}
