package com.tripflow.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;

public record TripResponse(
        Long id,
        String title,
        String description,
        List<String> tags,
        TripVisibility visibility,
        TripStatus status,
        Long ownerId,
        List<StopResponse> stops,
        Instant createdAt,
        Instant updatedAt,
        String routeGeometry,
        LocalDate startDate,
        long likeCount,
        long visitedStopCount
) {
    // EXPORT-03/D-08: detail-view half of the completion feature. The denominator is this
    // DTO's own `stops` list rather than a separate total-count component, so the two can
    // never drift out of step.
    @JsonProperty("completionPercentage")
    public double completionPercentage() {
        return TripCompletion.percentage(visitedStopCount, stops() == null ? 0 : stops().size());
    }
}