package com.tripflow.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.ai.SuggestedItinerary;
import com.tripflow.backend.dto.GenerateTripRequest;
import com.tripflow.backend.dto.ItineraryPreferencesRequest;
import com.tripflow.backend.dto.SuggestedItineraryResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.mapper.AiItineraryMapper;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.AiItineraryService;
import com.tripflow.backend.service.AiTripGenerationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * AI-assisted itinerary suggestions and trip generation. Kept separate from
 * TripController (SCRUM-64d) so Gemini-specific error handling stays isolated
 * from core trip CRUD — confirm this split with Neel before frontend wiring.
 *
 * <p>suggestItinerary does not persist anything — the frontend accepts
 * individual suggested stops via the existing POST /api/trips/{tripId}/stops
 * endpoint. generateTrip is the exception: it persists a brand-new trip in
 * the same call (see {@link AiTripGenerationService}).
 */
@Tag(name = "AI Itinerary", description = "Gemini-assisted stop suggestions and trip generation")
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class AiController {

	private final AiItineraryService aiItineraryService;
    private final AiItineraryMapper aiItineraryMapper;
    private final AiTripGenerationService aiTripGenerationService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;

    @Operation(summary = "Suggest an itinerary", description = "Sends the trip's existing stops plus interests/budget/pace preferences to Gemini and returns suggested stops with reasoning. Nothing is persisted.")
    @PostMapping("/{id}/ai-suggest")
    public ResponseEntity<SuggestedItineraryResponse> suggestItinerary(
            @PathVariable Long id,
            @RequestBody @Valid ItineraryPreferencesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        rateLimiterService.checkLimit("ai-suggest:" + principal.userId(), rateLimitProperties.aiSuggest());

        SuggestedItinerary suggestion = aiItineraryService.suggestItinerary(id, principal.userId(), request);
        return ResponseEntity.ok(aiItineraryMapper.toResponse(id, suggestion));
    }

    @Operation(summary = "Generate a new trip with AI", description = "Sends a free-text prompt to Gemini and creates a brand-new trip with AI-suggested stops in one call.")
    @PostMapping("/ai-generate")
    public ResponseEntity<TripResponse> generateTrip(
            @RequestBody @Valid GenerateTripRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        rateLimiterService.checkLimit("ai-generate:" + principal.userId(), rateLimitProperties.aiGenerate());

        TripResponse created = aiTripGenerationService.generateTrip(principal.userId(), request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
