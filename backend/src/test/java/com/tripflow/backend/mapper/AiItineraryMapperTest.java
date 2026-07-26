package com.tripflow.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripflow.backend.ai.SuggestedItinerary;
import com.tripflow.backend.ai.SuggestedItinerary.SuggestedStop;
import com.tripflow.backend.dto.SuggestedItineraryResponse;

public class AiItineraryMapperTest {

	private final AiItineraryMapper aiItineraryMapper = new AiItineraryMapper();

	@Test
	void toResponse_happyPath_mapsSummaryTripIdAndStops() {
		SuggestedStop stop1 = new SuggestedStop(1, "St. Lawrence Market", 43.6487, -79.3715,
				"Historic market with local food vendors — fits your interest in food and history.");
		SuggestedStop stop2 = new SuggestedStop(2, "CN Tower", 43.6426, -79.3871, "Iconic city view.");
		SuggestedItinerary itinerary = new SuggestedItinerary(
				"A 3-day cultural and culinary tour of Toronto.", List.of(stop1, stop2));

		SuggestedItineraryResponse response = aiItineraryMapper.toResponse(50L, itinerary);

		assertThat(response.tripId()).isEqualTo(50L);
		assertThat(response.summary()).isEqualTo("A 3-day cultural and culinary tour of Toronto.");
		assertThat(response.stops()).hasSize(2);

		SuggestedItineraryResponse.SuggestedStopResponse first = response.stops().get(0);
		assertThat(first.order()).isEqualTo(1);
		assertThat(first.name()).isEqualTo("St. Lawrence Market");
		assertThat(first.latitude()).isEqualTo(43.6487);
		assertThat(first.longitude()).isEqualTo(-79.3715);
		assertThat(first.reason()).isEqualTo(
				"Historic market with local food vendors — fits your interest in food and history.");
	}

	@Test
	void toResponse_emptyStops_mapsToEmptyListNotNull() {
		SuggestedItinerary itinerary = new SuggestedItinerary("Nothing to suggest right now.", List.of());

		SuggestedItineraryResponse response = aiItineraryMapper.toResponse(50L, itinerary);

		assertThat(response.stops()).isNotNull().isEmpty();
	}

	@Test
	void toResponse_nullReason_mapsCleanlyWithoutThrowing() {
		SuggestedStop stop = new SuggestedStop(1, "Mystery Spot", 0.0, 0.0, null);
		SuggestedItinerary itinerary = new SuggestedItinerary("Summary", List.of(stop));

		SuggestedItineraryResponse response = aiItineraryMapper.toResponse(50L, itinerary);

		assertThat(response.stops().get(0).reason()).isNull();
	}
}
