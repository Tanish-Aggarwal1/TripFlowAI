package com.tripflow.backend.dto;

import java.util.List;

public record SuggestedItineraryResponse(
        Long tripId,
        String summary,
        List<SuggestedStopResponse> stops
) {
    public record SuggestedStopResponse(Integer order, String name, Double latitude, Double longitude, String reason) {}
}