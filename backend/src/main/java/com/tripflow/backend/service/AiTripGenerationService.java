package com.tripflow.backend.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripflow.backend.ai.GeminiResponseParser;
import com.tripflow.backend.ai.GeneratedTripPlan;
import com.tripflow.backend.ai.GeneratedTripPlan.GeneratedStop;
import com.tripflow.backend.ai.TripGenerationPromptInput;
import com.tripflow.backend.ai.TripGenerationPromptTemplate;
import com.tripflow.backend.client.gemini.GeminiClient;
import com.tripflow.backend.client.gemini.GeminiGenerateContentResponse;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.GenerateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.InsufficientStopsException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates a brand-new trip from a free-text prompt via Gemini and persists
 * it in the same call. Distinct from {@link AiItineraryService}, whose whole
 * contract is "existing, owned trip" — this flow has no trip yet, so it has
 * no ownership check to perform and persists via {@link TripService#createTrip}
 * instead of returning an unpersisted suggestion.
 *
 * <p>Deliberately NOT {@code @Transactional}, same reasoning as
 * {@link AiItineraryService#suggestItinerary}: the Gemini call runs first with
 * no database connection held; {@link TripService#createTrip} opens its own
 * short transaction after Gemini returns, so the ~30s Gemini read-timeout
 * window never holds a DB connection open.
 *
 * <p>If Gemini returns zero stops (or the response fails to parse), this method
 * throws before {@link TripService#createTrip} is ever called, so a bad or
 * empty Gemini response never persists a stopless or partial trip.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripGenerationService {

    private final TripGenerationPromptTemplate promptTemplate;
    private final GeminiClient geminiClient;
    private final GeminiResponseParser responseParser;
    private final TripService tripService;

    public TripResponse generateTrip(Long ownerId, GenerateTripRequest request) {
        String renderedPrompt = promptTemplate.render(new TripGenerationPromptInput(request.prompt()));

        GeminiGenerateContentResponse geminiResponse = geminiClient.generateContent(renderedPrompt);
        GeneratedTripPlan plan = responseParser.parse(geminiResponse.firstCandidateText(), GeneratedTripPlan.class);

        if (plan.stops() == null || plan.stops().isEmpty()) {
            throw new InsufficientStopsException("Gemini did not return any stops for this prompt");
        }

        List<CreateStopRequest> stops = plan.stops().stream()
                .sorted(Comparator.comparing(GeneratedStop::order, Comparator.nullsLast(Integer::compareTo)))
                .map(s -> new CreateStopRequest(s.name(), s.latitude(), s.longitude(), null, null, s.reason()))
                .toList();

        String title = (request.title() != null && !request.title().isBlank()) ? request.title() : plan.title();

        CreateTripRequest createRequest = new CreateTripRequest(
                title, plan.summary(), null, TripVisibility.PRIVATE, stops, null);

        TripResponse created = tripService.createTrip(ownerId, createRequest);
        log.info("AI-generated trip created id={} ownerId={} stops={}", created.id(), ownerId, stops.size());
        return created;
    }
}
