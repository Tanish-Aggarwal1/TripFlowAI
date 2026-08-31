package com.tripflow.backend.dto;

/** SOCIAL-07: {@code averageRating} is a boxed {@code Double} (not primitive {@code double})
 * specifically so a trip with zero ratings is represented as {@code null} rather than a
 * misleading {@code 0.0}. {@code myRating} is the caller's own stored rating, or {@code null}
 * when they have not rated this trip. Never carries another user's rating or identity. */
public record TripRatingSummaryResponse(
        Double averageRating,
        long ratingCount,
        Integer myRating
) {
}
