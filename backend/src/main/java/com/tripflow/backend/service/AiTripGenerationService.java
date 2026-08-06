package com.tripflow.backend.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.tripflow.backend.exception.GeminiParsingException;
import com.tripflow.backend.exception.InsufficientStopsException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
    private final Validator validator;

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

        validateGenerated(createRequest);

        TripResponse created = tripService.createTrip(ownerId, createRequest);
        log.info("AI-generated trip created id={} ownerId={} stops={}", created.id(), ownerId, stops.size());
        return created;
    }

    /**
     * Bean Validation only fires automatically on {@code @RequestBody} controller
     * parameters via {@code @Valid} — this path builds the request programmatically
     * from Gemini's parsed JSON and calls {@link TripService} directly, so the same
     * constraints (lat/lng ranges, field lengths, etc.) must be checked explicitly
     * before anything is persisted. A hallucinated out-of-range coordinate should
     * fail here as a clean 502, not silently reach the database.
     */
    private void validateGenerated(CreateTripRequest request) {
        Set<ConstraintViolation<CreateTripRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new GeminiParsingException("Gemini returned an invalid trip: " + detail);
        }
    }
}
