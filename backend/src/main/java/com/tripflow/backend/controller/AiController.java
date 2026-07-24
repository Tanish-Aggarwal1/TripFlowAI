package com.tripflow.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.ai.SuggestedItinerary;
import com.tripflow.backend.dto.ItineraryPreferencesRequest;
import com.tripflow.backend.dto.SuggestedItineraryResponse;
import com.tripflow.backend.mapper.AiItineraryMapper;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.AiItineraryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AI-assisted itinerary suggestions. Kept separate from TripController
 * (SCRUM-64d) so Gemini-specific error handling stays isolated from core
 * trip CRUD — confirm this split with Neel before frontend wiring.
 *
 * <p>Does not persist anything — the frontend accepts individual suggested
 * stops via the existing POST /api/trips/{tripId}/stops endpoint.
 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class AiController {

	private final AiItineraryService aiItineraryService;
    private final AiItineraryMapper aiItineraryMapper;

    @PostMapping("/{id}/ai-suggest")
    public ResponseEntity<SuggestedItineraryResponse> suggestItinerary(
            @PathVariable Long id,
            @RequestBody @Valid ItineraryPreferencesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        SuggestedItinerary suggestion = aiItineraryService.suggestItinerary(id, principal.userId(), request);
        return ResponseEntity.ok(aiItineraryMapper.toResponse(id, suggestion));
    }
}
