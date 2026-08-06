package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import com.tripflow.backend.ai.GeminiResponseParser;
import com.tripflow.backend.ai.GeneratedTripPlan;
import com.tripflow.backend.ai.GeneratedTripPlan.GeneratedStop;
import com.tripflow.backend.ai.TripGenerationPromptTemplate;
import com.tripflow.backend.client.gemini.GeminiClient;
import com.tripflow.backend.client.gemini.GeminiGenerateContentResponse;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.GenerateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.GeminiClientException;
import com.tripflow.backend.exception.GeminiParsingException;
import com.tripflow.backend.exception.InsufficientStopsException;

@ExtendWith(MockitoExtension.class)
class AiTripGenerationServiceTest {

    @Mock private TripGenerationPromptTemplate promptTemplate;
    @Mock private GeminiClient geminiClient;
    @Mock private GeminiResponseParser responseParser;
    @Mock private TripService tripService;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AiTripGenerationService service;

    private GeminiGenerateContentResponse geminiResponseWithText(String text) {
        return new GeminiGenerateContentResponse(List.of(
                new GeminiGenerateContentResponse.Candidate(
                        new GeminiGenerateContentResponse.Content(
                                List.of(new GeminiGenerateContentResponse.Part(text)), "model"),
                        "STOP")));
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AiTripGenerationService(promptTemplate, geminiClient, responseParser, tripService, validator);
    }

    @Test
    void generateTrip_happyPath_mapsStopsInOrderAndPersistsViaTripService() {
        GenerateTripRequest request = new GenerateTripRequest("3 days in Kyoto", null);
        GeneratedTripPlan plan = new GeneratedTripPlan("Kyoto Trip", "A great trip", List.of(
                new GeneratedStop(1, "Second Stop", 2.0, 2.0, "reason 2"),
                new GeneratedStop(0, "First Stop", 1.0, 1.0, "reason 1")));

        when(promptTemplate.render(any())).thenReturn("rendered prompt");
        when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponseWithText("{}"));
        when(responseParser.parse("{}", GeneratedTripPlan.class)).thenReturn(plan);
        TripResponse expected = new TripResponse(
                1L, "Kyoto Trip", "A great trip", null, TripVisibility.PRIVATE, null, 1L,
                List.of(), null, null, null, null);
        when(tripService.createTrip(eq(1L), any())).thenReturn(expected);

        TripResponse result = service.generateTrip(1L, request);

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<CreateTripRequest> captor = ArgumentCaptor.forClass(CreateTripRequest.class);
        verify(tripService).createTrip(eq(1L), captor.capture());
        CreateTripRequest sent = captor.getValue();
        assertThat(sent.title()).isEqualTo("Kyoto Trip");
        assertThat(sent.visibility()).isEqualTo(TripVisibility.PRIVATE);
        assertThat(sent.stops()).hasSize(2);
        assertThat(sent.stops().get(0).name()).isEqualTo("First Stop");
        assertThat(sent.stops().get(1).name()).isEqualTo("Second Stop");
    }

    @Test
    void generateTrip_requestTitleProvided_overridesGeminisTitle() {
        GenerateTripRequest request = new GenerateTripRequest("a trip", "My Custom Title");
        GeneratedTripPlan plan = new GeneratedTripPlan("Gemini's Title", "summary",
                List.of(new GeneratedStop(0, "Stop", 1.0, 1.0, "reason")));

        when(promptTemplate.render(any())).thenReturn("rendered prompt");
        when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponseWithText("{}"));
        when(responseParser.parse("{}", GeneratedTripPlan.class)).thenReturn(plan);
        when(tripService.createTrip(any(), any())).thenReturn(
                new TripResponse(1L, "My Custom Title", null, null, TripVisibility.PRIVATE, null, 1L, List.of(), null, null, null, null));

        service.generateTrip(1L, request);

        ArgumentCaptor<CreateTripRequest> captor = ArgumentCaptor.forClass(CreateTripRequest.class);
        verify(tripService).createTrip(eq(1L), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("My Custom Title");
    }

    @Test
    void generateTrip_emptyStops_throwsInsufficientStops_neverCallsTripService() {
        GenerateTripRequest request = new GenerateTripRequest("a trip", null);
        GeneratedTripPlan plan = new GeneratedTripPlan("Title", "summary", List.of());

        when(promptTemplate.render(any())).thenReturn("rendered prompt");
        when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponseWithText("{}"));
        when(responseParser.parse("{}", GeneratedTripPlan.class)).thenReturn(plan);

        assertThatThrownBy(() -> service.generateTrip(1L, request))
                .isInstanceOf(InsufficientStopsException.class);

        verifyNoInteractions(tripService);
    }

    @Test
    void generateTrip_nullStops_throwsInsufficientStops() {
        GenerateTripRequest request = new GenerateTripRequest("a trip", null);
        GeneratedTripPlan plan = new GeneratedTripPlan("Title", "summary", null);

        when(promptTemplate.render(any())).thenReturn("rendered prompt");
        when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponseWithText("{}"));
        when(responseParser.parse("{}", GeneratedTripPlan.class)).thenReturn(plan);

        assertThatThrownBy(() -> service.generateTrip(1L, request))
                .isInstanceOf(InsufficientStopsException.class);

        verifyNoInteractions(tripService);
    }

    @Test
    void generateTrip_geminiClientFailure_bubblesUp_neverCallsTripService() {
        GenerateTripRequest request = new GenerateTripRequest("a trip", null);

        when(promptTemplate.render(any())).thenReturn("rendered prompt");
        when(geminiClient.generateContent("rendered prompt")).thenThrow(new GeminiClientException("down"));

        assertThatThrownBy(() -> service.generateTrip(1L, request))
                .isInstanceOf(GeminiClientException.class);

        verifyNoInteractions(tripService);
    }

    @Test
    void generateTrip_parsingFailure_bubblesUp_neverCallsTripService() {
        GenerateTripRequest request = new GenerateTripRequest("a trip", null);

        when(promptTemplate.render(any())).thenReturn("rendered prompt");
        when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponseWithText("not json"));
        when(responseParser.parse("not json", GeneratedTripPlan.class)).thenThrow(new GeminiParsingException("bad json"));

        assertThatThrownBy(() -> service.generateTrip(1L, request))
                .isInstanceOf(GeminiParsingException.class);

        verifyNoInteractions(tripService);
    }

    @Test
    void generateTrip_outOfRangeCoordinates_throwsGeminiParsingException_neverCallsTripService() {
        GenerateTripRequest request = new GenerateTripRequest("a trip", null);
        GeneratedTripPlan plan = new GeneratedTripPlan("Title", "summary",
                List.of(new GeneratedStop(0, "Impossible Stop", 999.0, 1.0, "hallucinated coordinates")));

        when(promptTemplate.render(any())).thenReturn("rendered prompt");
        when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponseWithText("{}"));
        when(responseParser.parse("{}", GeneratedTripPlan.class)).thenReturn(plan);

        assertThatThrownBy(() -> service.generateTrip(1L, request))
                .isInstanceOf(GeminiParsingException.class);

        verifyNoInteractions(tripService);
    }
}
