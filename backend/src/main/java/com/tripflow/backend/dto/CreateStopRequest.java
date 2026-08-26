package com.tripflow.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStopRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 300) String address,
        @Size(max = 150) String externalPlaceId,
        @Size(max = 2000) String notes
) {}
