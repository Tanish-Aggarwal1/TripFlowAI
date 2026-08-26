package com.tripflow.backend.dto;

import java.time.LocalDate;
import java.util.List;

import com.tripflow.backend.domain.enums.TripVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTripRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 5000) String description,
        @Size(max = 20) List<@Size(max = 50) String> tags,
        @NotNull TripVisibility visibility,
        @NotEmpty @Size(max = MAX_STOPS) List<@Valid UpsertStopRequest> stops,
        LocalDate startDate
) {
    /**
     * Ceiling on a single trip's stop count, mirrored on {@link CreateTripRequest}. Every
     * stop in the payload costs a VROOM job on {@code POST /trips/{id}/optimize} and a
     * waypoint on the follow-up directions call, both against a 500 requests/day ORS quota,
     * and widens the {@code IN} clause in the batched place lookup. 50 comfortably covers a
     * real multi-week itinerary while keeping all of those bounded.
     */
    public static final int MAX_STOPS = 50;

    // SCRUM-244a: startDate is new and optional — this overload keeps every existing
    // call site (tests especially) compiling without threading a null through each one.
    // Note this passes startDate = null, which updateTrip treats as "leave unchanged",
    // not "clear it" — see TripService.updateTrip.
    public UpdateTripRequest(String title, String description, List<String> tags,
            TripVisibility visibility, List<UpsertStopRequest> stops) {
        this(title, description, tags, visibility, stops, null);
    }
}