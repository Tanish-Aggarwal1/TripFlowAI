package com.tripflow.backend.dto;

import com.tripflow.backend.domain.enums.StopStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateStopRequest {
    @NotBlank
    private String name;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private String address;

    private String externalPlaceId;

    private String notes;

    private StopStatus status; // optional — null keeps the existing status
}