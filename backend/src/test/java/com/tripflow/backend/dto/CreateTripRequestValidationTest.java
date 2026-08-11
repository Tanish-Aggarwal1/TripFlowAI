package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

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

    @Test
    void titleOver150Chars_isRejected() {
        CreateTripRequest req = new CreateTripRequest(
                "x".repeat(151), null, null, TripVisibility.PRIVATE, List.of(validStop()));

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
    }

    @Test
    void tagsOver20Elements_isRejected() {
        List<String> tooManyTags = IntStream.range(0, 21)
                .mapToObj(i -> "tag" + i).toList();
        CreateTripRequest req = new CreateTripRequest(
                "Weekend Trip", null, tooManyTags, TripVisibility.PRIVATE, List.of(validStop()));

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tags"));
    }

    @Test
    void tagOver50Chars_isRejected() {
        CreateTripRequest req = new CreateTripRequest(
                "Weekend Trip", null, List.of("x".repeat(51)), TripVisibility.PRIVATE, List.of(validStop()));

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("tags"));
    }

    @Test
    void exactly20Tags_isAccepted() {
        List<String> tags = IntStream.range(0, 20).mapToObj(i -> "tag" + i).toList();
        CreateTripRequest req = new CreateTripRequest(
                "Weekend Trip", null, tags, TripVisibility.PRIVATE, List.of(validStop()));

        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void stopsOverMaxStops_isRejected() {
        List<CreateStopRequest> tooManyStops = IntStream.range(0, UpdateTripRequest.MAX_STOPS + 1)
                .mapToObj(i -> validStop()).toList();
        CreateTripRequest req = new CreateTripRequest(
                "Weekend Trip", null, null, TripVisibility.PRIVATE, tooManyStops);

        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stops"));
    }

    @Test
    void exactlyMaxStops_isAccepted() {
        List<CreateStopRequest> stops = IntStream.range(0, UpdateTripRequest.MAX_STOPS)
                .mapToObj(i -> validStop()).toList();
        CreateTripRequest req = new CreateTripRequest(
                "Weekend Trip", null, null, TripVisibility.PRIVATE, stops);

        assertThat(validator.validate(req)).isEmpty();
    }

    /** The same ceiling applies to the update path, so a trip can't grow past it either. */
    @Test
    void updateRequest_stopsOverMaxStops_isRejected() {
        List<UpsertStopRequest> tooManyStops = IntStream.range(0, UpdateTripRequest.MAX_STOPS + 1)
                .mapToObj(i -> new UpsertStopRequest(null, "Cottage", 45.0, -79.9, null, null, null))
                .toList();
        UpdateTripRequest req = new UpdateTripRequest(
                "Weekend Trip", null, null, TripVisibility.PRIVATE, tooManyStops);

        Set<ConstraintViolation<UpdateTripRequest>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stops"));
    }

    private CreateStopRequest validStop() {
        return new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
    }
}
