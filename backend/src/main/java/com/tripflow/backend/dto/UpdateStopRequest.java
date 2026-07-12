package com.tripflow.backend.dto;

import com.tripflow.backend.domain.enums.StopStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public record UpdateStopRequest(
        @NotBlank String name,
        @NotNull Double latitude,
        @NotNull Double longitude,
        String address,
        String externalPlaceId,
        String notes,
        StopStatus status // optional — null keeps the existing status
) {}