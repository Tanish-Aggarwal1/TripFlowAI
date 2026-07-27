package com.tripflow.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tripflow.backend.ai.GeminiResponseParser;
import com.tripflow.backend.ai.ItineraryPromptInput;
import com.tripflow.backend.ai.ItineraryPromptTemplate;
import com.tripflow.backend.ai.SuggestedItinerary;
import com.tripflow.backend.client.gemini.GeminiClient;
import com.tripflow.backend.client.gemini.GeminiGenerateContentResponse;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.ItineraryPreferencesRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates AI itinerary suggestions via Gemini for an existing, owned trip.
 *
 * <p>Ownership check happens here (mirrors {@link RouteOptimizationService#optimize},
 * not pushed to the controller layer — every service in this codebase is
 * self-contained on that point.
 *
 * <p>Deliberately NOT {@code @Transactional} (SCRUM-210): {@link TripOwnershipService#loadOwnedTrip}
 * is transactional on its own bean and returns before this method calls {@link GeminiClient},
 * so no database connection is held for the ~30s Gemini read-timeout window. Wrapping this
 * method itself in {@code @Transactional} would reintroduce the bug — self-invocation aside,
 * it would hold the connection open across the entire Gemini call again.
 *
 * <p>Error handling: {@link com.tripflow.backend.exception.GeminiClientException}
 * (from {@link GeminiClient}) and {@link com.tripflow.backend.exception.GeminiParsingException}
 * (from {@link GeminiResponseParser}) both propagate to
 * {@link com.tripflow.backend.exception.GlobalExceptionHandler}, which maps both to 502.
 * This service does not catch either.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiItineraryService {

    private final TripOwnershipService tripOwnershipService;
    private final GeminiClient geminiClient;
    private final ItineraryPromptTemplate promptTemplate;
    private final GeminiResponseParser responseParser;

    public SuggestedItinerary suggestItinerary(Long tripId, Long requesterId, ItineraryPreferencesRequest preferences) {
        Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);

        List<String> destinations = trip.getStops().stream()
                .map(stop -> stop.getPlace().getName())
                .toList();

        ItineraryPromptInput promptInput = new ItineraryPromptInput(
                preferences.interests(), preferences.budget(), preferences.pace(), destinations);

        String renderedPrompt = promptTemplate.render(promptInput);

        GeminiGenerateContentResponse geminiResponse = geminiClient.generateContent(renderedPrompt);
        SuggestedItinerary suggestion = responseParser.parse(geminiResponse.firstCandidateText());

        log.info("AI itinerary generated tripId={} suggestedStops={}", tripId, suggestion.stops().size());
        return suggestion;
    }
}