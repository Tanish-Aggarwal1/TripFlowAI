package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripflow.backend.domain.enums.TripVisibility;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class CreateTripRequestValidationTest {
	

	private ValidatorFactory factory;
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    void blankTitle_isRejected() {
        CreateTripRequest req = new CreateTripRequest();
        req.setTitle("");
        req.setVisibility(TripVisibility.PRIVATE);
        req.setStops(List.of(validStop()));

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
    }

    @Test
    void emptyStops_isRejected() {
        CreateTripRequest req = new CreateTripRequest();
        req.setTitle("Weekend Trip");
        req.setVisibility(TripVisibility.PRIVATE);
        req.setStops(List.of());

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stops"));
    }

    @Test
    void validRequest_hasNoViolations() {
        CreateTripRequest req = new CreateTripRequest();
        req.setTitle("Weekend Trip");
        req.setVisibility(TripVisibility.PRIVATE);
        req.setStops(List.of(validStop()));

        assertThat(validator.validate(req)).isEmpty();
    }

    private CreateStopRequest validStop() {
        CreateStopRequest stop = new CreateStopRequest();
        stop.setName("Cottage");
        stop.setLatitude(45.0);
        stop.setLongitude(-79.9);
        return stop;
    }
}
