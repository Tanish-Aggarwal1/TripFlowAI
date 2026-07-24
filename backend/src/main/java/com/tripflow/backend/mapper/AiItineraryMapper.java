package com.tripflow.backend.mapper;

import org.springframework.stereotype.Component;

import com.tripflow.backend.ai.SuggestedItinerary;
import com.tripflow.backend.dto.SuggestedItineraryResponse;

@Component
public class AiItineraryMapper {

    public SuggestedItineraryResponse toResponse(Long tripId, SuggestedItinerary itinerary) {
        var stops = itinerary.stops().stream()
                .map(stop -> new SuggestedItineraryResponse.SuggestedStopResponse(
                        stop.order(), stop.name(), stop.latitude(), stop.longitude(), stop.reason()))
                .toList();
        return new SuggestedItineraryResponse(tripId, itinerary.summary(), stops);
    }
}