package com.tripflow.backend.mapper;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.FeedTripResponse;

/**
 * Trip-to-{@link FeedTripResponse} mapping for the authenticated discovery feed
 * (SOCIAL-01). Extracted from {@code TripService.listFeed} (was inline in the
 * tracer task) so the batched stop-photo lookup and the per-stop D-03 text-fallback
 * shape live in one place.
 */
@Component
public class FeedTripMapper {

    public List<FeedTripResponse> toFeedResponses(List<Trip> trips, Map<Long, List<StopPhoto>> photosByStopId) {
        return trips.stream().map(trip -> toFeedResponse(trip, photosByStopId)).toList();
    }

    private FeedTripResponse toFeedResponse(Trip trip, Map<Long, List<StopPhoto>> photosByStopId) {
        List<FeedTripResponse.FeedStop> stops = trip.getStops().stream()
                .map(stop -> toFeedStop(stop, photosByStopId))
                .toList();
        return new FeedTripResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getDescription(),
                trip.getTags(),
                trip.getUser().getUsername(),
                trip.getLikeCount(),
                trip.getCreatedAt(),
                stops
        );
    }

    private FeedTripResponse.FeedStop toFeedStop(Stop stop, Map<Long, List<StopPhoto>> photosByStopId) {
        List<String> photoUrls = photosByStopId.getOrDefault(stop.getId(), List.of()).stream()
                .map(StopPhoto::getUrl)
                .toList();
        return new FeedTripResponse.FeedStop(
                stop.getId(),
                stop.getPlace().getName(),
                stop.getPlace().getAddress(),
                stop.getStopOrder(),
                stop.getNotes(),
                photoUrls
        );
    }
}
