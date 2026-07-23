package com.tripflow.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.ai.GeminiResponseParser;
import com.tripflow.backend.ai.ItineraryPromptInput;
import com.tripflow.backend.ai.ItineraryPromptTemplate;
import com.tripflow.backend.ai.SuggestedItinerary;
import com.tripflow.backend.client.gemini.GeminiClient;
import com.tripflow.backend.client.gemini.GeminiGenerateContentResponse;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.ItineraryPreferencesRequest;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates AI itinerary suggestions via Gemini for an existing, owned trip.
 *
 * <p>Ownership check happens here (mirrors {@link RouteOptimizationService#optimize},
 * not pushed to the controller layer — every service in this codebase is
 * self-contained on that point.
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

    private final TripRepository tripRepository;
    private final GeminiClient geminiClient;
    private final ItineraryPromptTemplate promptTemplate;
    private final GeminiResponseParser responseParser;

    @Transactional(readOnly = true)
    public SuggestedItinerary suggestItinerary(Long tripId, Long requesterId, ItineraryPreferencesRequest preferences) {
        Trip trip = loadOwnedTrip(tripId, requesterId);

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

    private Trip loadOwnedTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findWithStopsById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        if (!trip.getUser().getId().equals(requesterId)) {
            log.debug("AI-suggest ownership check failed tripId={} ownerId={} requesterId={}",
                    tripId, trip.getUser().getId(), requesterId);
            throw new ForbiddenException("You do not have access to this trip");
        }
        return trip;
    }
}