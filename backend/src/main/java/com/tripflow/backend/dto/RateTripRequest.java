package com.tripflow.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** SOCIAL-07: bounds a rating to 1-5 before it ever reaches the database — the
 * {@code trip_ratings} CHECK constraint (V16) is the independent second layer. */
public record RateTripRequest(
        @NotNull @Min(1) @Max(5) Integer rating
) {
}
