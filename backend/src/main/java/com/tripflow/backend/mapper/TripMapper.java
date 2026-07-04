package com.tripflow.backend.mapper;

import java.util.List;
import java.util.stream.Collectors;

import com.tripflow.backend.beans.Trip;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.beans.enums.TripStatus;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class TripMapper {
	public static Trip toEntity(CreateTripRequest request, User owner) {
        Trip trip = new Trip();
        trip.setUser(owner);
        trip.setTitle(request.getTitle());
        trip.setDescription(request.getDescription());
        trip.setTags(request.getTags());
        trip.setVisibility(request.getVisibility());
        trip.setStatus(TripStatus.DRAFT);
        // id, routeGeometry intentionally NOT set from request — server-owned
        return trip;
    }

    public static TripResponse toResponse(Trip trip) {
        TripResponse response = new TripResponse();
        response.setId(trip.getId());
        response.setTitle(trip.getTitle());
        response.setDescription(trip.getDescription());
        response.setTags(trip.getTags());
        response.setVisibility(trip.getVisibility());
        response.setStatus(trip.getStatus());
        response.setOwnerId(trip.getUser().getId());
        response.setCreatedAt(trip.getCreatedAt());
        response.setUpdatedAt(trip.getUpdatedAt());

        List<com.tripflow.backend.dto.StopResponse> stopResponses = trip.getStops().stream()
                .map(StopMapper::toResponse)
                .collect(Collectors.toList());
        response.setStops(stopResponses);

        return response;
    }
}
