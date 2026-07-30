package com.tripflow.backend.dto;

import java.time.Instant;

public record StopPhotoResponse(
        Long id,
        Long stopId,
        String url,
        String cloudinaryPublicId,
        String caption,
        Instant createdAt
) {}