package com.tripflow.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStopRequest(
        @NotBlank String name,
        @NotNull Double latitude,
        @NotNull Double longitude,
        String address,
        String externalPlaceId,
        String notes
) {}