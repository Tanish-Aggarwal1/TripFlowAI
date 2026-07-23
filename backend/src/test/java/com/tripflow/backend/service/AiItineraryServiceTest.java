package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.ai.GeminiResponseParser;
import com.tripflow.backend.ai.ItineraryPromptTemplate;
import com.tripflow.backend.ai.SuggestedItinerary;
import com.tripflow.backend.client.gemini.GeminiClient;
import com.tripflow.backend.client.gemini.GeminiGenerateContentResponse;
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.dto.ItineraryPreferencesRequest;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.GeminiClientException;
import com.tripflow.backend.exception.GeminiParsingException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.TripRepository;

@ExtendWith(MockitoExtension.class)
public class AiItineraryServiceTest {
	
	@Mock private TripRepository tripRepository;
	@Mock private GeminiClient geminiClient;
	@Mock private ItineraryPromptTemplate promptTemplate;
	@Mock private GeminiResponseParser responseParser;

	private AiItineraryService service;

	@BeforeEach
	void setUp() {
		service = new AiItineraryService(tripRepository, geminiClient, promptTemplate, responseParser);
	}

	private Trip ownedTrip(Long ownerId) {
		User owner = new User();
		owner.setId(ownerId);

		Place place = new Place();
		place.setName("Ottawa");

		Stop stop = new Stop();
		stop.setPlace(place);
		stop.setStopOrder(0);

		Trip trip = new Trip();
		trip.setId(50L);
		trip.setUser(owner);
		trip.setStops(new ArrayList<>(List.of(stop)));
		return trip;
	}

	@Test
	void suggestItinerary_ownerHappyPath_returnsParsedSuggestion() {
		Trip trip = ownedTrip(1L);
		ItineraryPreferencesRequest prefs = new ItineraryPreferencesRequest(List.of("food"), "moderate", "slow");
		SuggestedItinerary expected = new SuggestedItinerary("summary", List.of());
		GeminiGenerateContentResponse geminiResponse = new GeminiGenerateContentResponse(List.of(
				new GeminiGenerateContentResponse.Candidate(
						new GeminiGenerateContentResponse.Content(
								List.of(new GeminiGenerateContentResponse.Part("{}")), "model"),
						"STOP")));

		when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
		when(promptTemplate.render(any())).thenReturn("rendered prompt");
		when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponse);
		when(responseParser.parse("{}")).thenReturn(expected);

		SuggestedItinerary result = service.suggestItinerary(50L, 1L, prefs);

		assertThat(result).isEqualTo(expected);
	}

	@Test
	void suggestItinerary_nonOwner_throwsForbidden_neverCallsGemini() {
		Trip trip = ownedTrip(1L);
		when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

		assertThatThrownBy(() -> service.suggestItinerary(50L, 2L, new ItineraryPreferencesRequest(null, null, null)))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void suggestItinerary_missingTrip_throwsNotFound() {
		when(tripRepository.findWithStopsById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.suggestItinerary(999L, 1L, new ItineraryPreferencesRequest(null, null, null)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void suggestItinerary_geminiClientFailure_bubblesUp() {
		Trip trip = ownedTrip(1L);
		when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
		when(promptTemplate.render(any())).thenReturn("rendered prompt");
		when(geminiClient.generateContent("rendered prompt")).thenThrow(new GeminiClientException("down"));

		assertThatThrownBy(() -> service.suggestItinerary(50L, 1L, new ItineraryPreferencesRequest(null, null, null)))
				.isInstanceOf(GeminiClientException.class);
	}

	@Test
	void suggestItinerary_parsingFailure_bubblesUp() {
		Trip trip = ownedTrip(1L);
		GeminiGenerateContentResponse geminiResponse = new GeminiGenerateContentResponse(List.of(
				new GeminiGenerateContentResponse.Candidate(
						new GeminiGenerateContentResponse.Content(
								List.of(new GeminiGenerateContentResponse.Part("not json")), "model"),
						"STOP")));

		when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
		when(promptTemplate.render(any())).thenReturn("rendered prompt");
		when(geminiClient.generateContent("rendered prompt")).thenReturn(geminiResponse);
		when(responseParser.parse("not json")).thenThrow(new GeminiParsingException("bad json"));

		assertThatThrownBy(() -> service.suggestItinerary(50L, 1L, new ItineraryPreferencesRequest(null, null, null)))
				.isInstanceOf(GeminiParsingException.class);
	}


}
