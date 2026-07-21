package com.tripflow.backend.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.TripResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TripMapper {
	private final StopMapper stopMapper;

    public Trip toEntity(CreateTripRequest request, User owner) {
        Trip trip = new Trip();
        trip.setUser(owner);
        trip.setTitle(request.title());
        trip.setDescription(request.description());
        trip.setTags(request.tags());
        trip.setVisibility(request.visibility());
        trip.setStatus(TripStatus.DRAFT);
        // id, routeGeometry intentionally NOT set from request — server-owned
        return trip;
    }

    public TripResponse toResponse(Trip trip) {
        List<StopResponse> stopResponses = trip.getStops().stream()
                .map(stopMapper::toResponse)
                .toList();

        return new TripResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getDescription(),
                trip.getTags(),
                trip.getVisibility(),
                trip.getStatus(),
                trip.getUser().getId(),
                stopResponses,
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                trip.getRouteGeometry()
        );
    }
}
