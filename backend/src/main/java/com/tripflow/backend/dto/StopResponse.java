package com.tripflow.backend.dto;

import java.time.LocalTime;

import com.tripflow.backend.domain.enums.StopStatus;
import com.tripflow.backend.domain.enums.StopType;

public record StopResponse(
        Long id,
        String name,
        Double latitude,
        Double longitude,
        String address,
        Integer stopOrder,
        StopStatus status,
        String notes,
        Integer dayNumber,
        LocalTime plannedTime,
        StopType stopType
) {}
