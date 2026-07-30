package com.tripflow.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateStopPhotoRequest(
        @NotBlank String url,
        String cloudinaryPublicId,
        String caption
) {}