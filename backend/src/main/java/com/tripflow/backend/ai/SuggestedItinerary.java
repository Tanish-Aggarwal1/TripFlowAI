package com.tripflow.backend.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strict schema for the JSON itinerary suggestion Gemini must return.
 * Parsed with FAIL_ON_UNKNOWN_PROPERTIES forced on (see GeminiResponseParser)
 * regardless of whatever leniency the app-wide ObjectMapper bean allows for
 * other DTOs — a field Gemini adds that we haven't declared here should fail
 * loudly (→ GeminiParsingException → 502) rather than get silently dropped.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SuggestedItinerary(String summary, List<SuggestedStop> stops) {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SuggestedStop(Integer order, String name, Double latitude, Double longitude, String reason) {}
}