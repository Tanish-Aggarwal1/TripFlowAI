package com.tripflow.backend.dto;

import java.time.LocalDate;
import java.util.List;

import com.tripflow.backend.domain.enums.TripVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTripRequest(
        @NotBlank @Size(max = 150) String title,
        String description,
        @Size(max = 20) List<@Size(max = 50) String> tags,
        @NotNull TripVisibility visibility,
        @NotEmpty List<@Valid CreateStopRequest> stops,
        LocalDate startDate
) {
    // SCRUM-244a: startDate is new and optional — this overload keeps every existing
    // call site (tests especially) compiling without threading a null through each one.
    public CreateTripRequest(String title, String description, List<String> tags,
            TripVisibility visibility, List<CreateStopRequest> stops) {
        this(title, description, tags, visibility, stops, null);
    }
}
