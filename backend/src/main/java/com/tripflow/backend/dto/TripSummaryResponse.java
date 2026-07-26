package com.tripflow.backend.dto;

import java.time.Instant;

import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;

/**
 * Card-sized projection for list views (GET /api/trips) — deliberately excludes the
 * full {@code stops} collection so paginating this query never needs a collection
 * fetch join (see REF-21 audit notes: fetch-joined + Pageable triggers Hibernate's
 * in-memory pagination, defeating the point of paging).
 * {@code coverPhotoUrl} is always null until the Cloudinary photo feature (SCRUM-66) lands.
 */
public record TripSummaryResponse(
        Long id,
        String title,
        TripVisibility visibility,
        TripStatus status,
        Instant createdAt,
        Instant updatedAt,
        long stopCount,
        String coverPhotoUrl
) {}
