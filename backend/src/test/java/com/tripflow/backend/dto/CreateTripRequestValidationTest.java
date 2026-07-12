package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tripflow.backend.domain.enums.TripVisibility;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class CreateTripRequestValidationTest {
	
	private final Validator validator;

    CreateTripRequestValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Test
    void blankTitle_isRejected() {
        CreateTripRequest req = new CreateTripRequest(
                "", null, null, TripVisibility.PRIVATE, List.of(validStop()));

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
    }

    @Test
    void emptyStops_isRejected() {
        CreateTripRequest req = new CreateTripRequest(
                "Weekend Trip", null, null, TripVisibility.PRIVATE, List.of());

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stops"));
    }

    @Test
    void validRequest_hasNoViolations() {
        CreateTripRequest req = new CreateTripRequest(
                "Weekend Trip", null, null, TripVisibility.PRIVATE, List.of(validStop()));

        assertThat(validator.validate(req)).isEmpty();
    }

    private CreateStopRequest validStop() {
        return new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
    }
}
