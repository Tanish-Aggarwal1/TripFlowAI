package com.tripflow.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStopPhotoRequest(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 255) String cloudinaryPublicId,
        @Size(max = 500) String caption
) {}