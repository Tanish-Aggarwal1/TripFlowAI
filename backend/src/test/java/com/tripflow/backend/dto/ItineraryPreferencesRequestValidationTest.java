package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ItineraryPreferencesRequestValidationTest {

	private final Validator validator;

	ItineraryPreferencesRequestValidationTest() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
	}

	@Test
	void validRequest_hasNoViolations() {
		ItineraryPreferencesRequest req = new ItineraryPreferencesRequest(
				List.of("food", "history"), "moderate", "relaxed");

		assertThat(validator.validate(req)).isEmpty();
	}

	@Test
	void allFieldsNull_hasNoViolations() {
		ItineraryPreferencesRequest req = new ItineraryPreferencesRequest(null, null, null);

		assertThat(validator.validate(req)).isEmpty();
	}

	@Test
	void moreThan10Interests_isRejected() {
		List<String> tooMany = IntStream.range(0, 11).mapToObj(i -> "interest" + i).toList();
		ItineraryPreferencesRequest req = new ItineraryPreferencesRequest(tooMany, null, null);

		Set<ConstraintViolation<ItineraryPreferencesRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("interests"));
	}

	@Test
	void exactly10Interests_isAccepted() {
		List<String> tenInterests = IntStream.range(0, 10).mapToObj(i -> "interest" + i).toList();
		ItineraryPreferencesRequest req = new ItineraryPreferencesRequest(tenInterests, null, null);

		assertThat(validator.validate(req)).isEmpty();
	}

	@Test
	void interestOver50Chars_isRejected() {
		ItineraryPreferencesRequest req = new ItineraryPreferencesRequest(
				List.of("x".repeat(51)), null, null);

		Set<ConstraintViolation<ItineraryPreferencesRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("interests"));
	}

	@Test
	void budgetOver50Chars_isRejected() {
		ItineraryPreferencesRequest req = new ItineraryPreferencesRequest(null, "x".repeat(51), null);

		Set<ConstraintViolation<ItineraryPreferencesRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("budget"));
	}

	@Test
	void paceOver50Chars_isRejected() {
		ItineraryPreferencesRequest req = new ItineraryPreferencesRequest(null, null, "x".repeat(51));

		Set<ConstraintViolation<ItineraryPreferencesRequest>> violations = validator.validate(req);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("pace"));
	}
}
