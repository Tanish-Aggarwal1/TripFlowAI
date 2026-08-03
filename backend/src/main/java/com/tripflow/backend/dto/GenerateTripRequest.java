package com.tripflow.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateTripRequest(
        @NotBlank @Size(max = 1000, message = "must be at most 1000 characters") String prompt,
        @Size(max = 150, message = "must be at most 150 characters") String title
) {}
