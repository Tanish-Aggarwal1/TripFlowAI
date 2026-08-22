package com.tripflow.backend.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;

/**
 * Owner-only sibling of {@link TripSummaryResponse} (D-08). Backs {@code GET /api/trips}
 * exclusively — {@code TripSummaryResponse} stays byte-for-byte unchanged and keeps backing
 * the public discovery feed ({@code findSummariesByVisibility}, {@code searchPublicTrips}),
 * so a stranger's PUBLIC trip never exposes another user's completion progress.
 *
 * <p>{@code completionPercentage} is a 0.0-1.0 fraction, computed via
 * {@link TripCompletion#percentage(long, long)} so the D-06/D-07 rule lives in one place.
 */
public record TripOwnerSummaryResponse(
        Long id,
        String title,
        TripVisibility visibility,
        TripStatus status,
        Instant createdAt,
        Instant updatedAt,
        long stopCount,
        String coverPhotoUrl,
        long visitedStopCount
) {
    @JsonProperty("completionPercentage")
    public double completionPercentage() {
        return TripCompletion.percentage(visitedStopCount, stopCount);
    }
}
