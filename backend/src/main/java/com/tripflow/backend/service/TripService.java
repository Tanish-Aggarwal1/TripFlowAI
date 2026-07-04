package com.tripflow.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.beans.Place;
import com.tripflow.backend.beans.Stop;
import com.tripflow.backend.beans.Trip;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.PlaceRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class TripService {

	private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;


    @Transactional
    public TripResponse createTrip(Long ownerId, CreateTripRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));

        Trip trip = TripMapper.toEntity(request, owner);

        List<Stop> stops = new ArrayList<>();
        int order = 0;
        for (CreateStopRequest stopRequest : request.getStops()) {
            Place place = resolvePlace(stopRequest);
            Stop stop = StopMapper.toEntity(stopRequest, place, order);
            stop.setTrip(trip);
            stops.add(stop);
            order++;
        }
        trip.setStops(stops);

        Trip saved = tripRepository.save(trip);
        return TripMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        boolean isOwner = trip.getUser().getId().equals(requesterId);
        if (trip.getVisibility() == TripVisibility.PRIVATE && !isOwner) {
            throw new ForbiddenException("You do not have access to this trip");
        }

        return TripMapper.toResponse(trip);
    }

    private Place resolvePlace(CreateStopRequest request) {
        Optional<Place> existing = Optional.empty();

        if (request.getExternalPlaceId() != null && !request.getExternalPlaceId().isBlank()) {
            existing = placeRepository.findByExternalPlaceId(request.getExternalPlaceId());
        }

        if (existing.isEmpty()) {
            existing = placeRepository.findByNameAndLatitudeAndLongitude(
                    request.getName(), request.getLatitude(), request.getLongitude());
        }

        if (existing.isPresent()) {
            return existing.get();
        }

        Place place = new Place();
        place.setName(request.getName());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setAddress(request.getAddress());
        place.setExternalPlaceId(request.getExternalPlaceId());
        return placeRepository.save(place);
    }
}
