package com.tripflow.backend.dto;

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
        List<String> tags,
        @NotNull TripVisibility visibility,
        @NotEmpty List<@Valid CreateStopRequest> stops
) {}
