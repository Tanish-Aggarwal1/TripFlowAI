package com.tripflow.backend.dto;

import java.time.LocalDate;

import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;

/**
 * SEARCH-01/D-11: filter set for {@code TripSearchRepository#searchOwnedTrips}. Every
 * component is nullable, meaning "unconstrained" — all supplied filters AND together (D-12).
 */
public record TripSearchFilters(
        TripStatus status,
        TripVisibility visibility,
        LocalDate startDateFrom,
        LocalDate startDateTo,
        Integer durationDays
) {
    public static TripSearchFilters none() {
        return new TripSearchFilters(null, null, null, null, null);
    }
}
