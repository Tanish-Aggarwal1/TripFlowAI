package com.tripflow.backend.dto;

import java.time.Instant;
import java.util.List;

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
        String routeGeometry
) {}