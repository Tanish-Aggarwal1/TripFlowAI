package com.tripflow.backend.dto;

import com.tripflow.backend.domain.enums.StopStatus;

public record StopResponse(
        Long id,
        String name,
        Double latitude,
        Double longitude,
        String address,
        Integer stopOrder,
        StopStatus status,
        String notes
) {}
