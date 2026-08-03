package com.tripflow.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strict schema for the JSON trip plan Gemini must return for a from-scratch
 * "generate a whole trip" prompt — distinct from {@link SuggestedItinerary},
 * which augments a trip's existing stops and never carries a title. Parsed
 * with FAIL_ON_UNKNOWN_PROPERTIES forced on (see GeminiResponseParser).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record GeneratedTripPlan(String title, String summary, List<GeneratedStop> stops) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record GeneratedStop(Integer order, String name, Double latitude, Double longitude, String reason) {}
}
