package com.tripflow.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.beans.Place;
import com.tripflow.backend.beans.Stop;
import com.tripflow.backend.beans.Trip;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.dto.UpdateStopRequest;
import com.tripflow.backend.dto.UpdateTripRequest;
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

    // ---------- Trips ----------

    @Transactional(readOnly = true)
    public List<TripResponse> listTrips(Long ownerId) {
        return tripRepository.findByUserIdOrderByCreatedAtDesc(ownerId).stream()
                .map(TripMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TripResponse createTrip(Long ownerId, CreateTripRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));

        Trip trip = TripMapper.toEntity(request, owner);

        int order = 0;
        for (CreateStopRequest stopRequest : request.getStops()) {
            Stop stop = StopMapper.toEntity(stopRequest, resolvePlace(stopRequest), order++);
            stop.setTrip(trip);
            trip.getStops().add(stop);
        }

        return TripMapper.toResponse(tripRepository.save(trip));
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

    @Transactional
    public TripResponse updateTrip(Long tripId, Long requesterId, UpdateTripRequest request) {
        Trip trip = loadOwnedTrip(tripId, requesterId);

        trip.setTitle(request.getTitle());
        trip.setDescription(request.getDescription());
        trip.setTags(request.getTags());
        trip.setVisibility(request.getVisibility());
        // status is server-owned lifecycle state — intentionally not touched here

        // Full itinerary replace. orphanRemoval deletes dropped stops; shared Places survive.
        trip.getStops().clear();
        int order = 0;
        for (CreateStopRequest stopRequest : request.getStops()) {
            Stop stop = StopMapper.toEntity(stopRequest, resolvePlace(stopRequest), order++);
            stop.setTrip(trip);
            trip.getStops().add(stop);
        }

        return TripMapper.toResponse(tripRepository.save(trip));
    }

    @Transactional
    public void deleteTrip(Long tripId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        tripRepository.delete(trip); // cascade + FK ON DELETE CASCADE remove stops; Places survive
    }

    // ---------- Nested stops (owner-scoped) ----------

    @Transactional(readOnly = true)
    public List<StopResponse> listStops(Long tripId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        return trip.getStops().stream().map(StopMapper::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StopResponse getStop(Long tripId, Long stopId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        return StopMapper.toResponse(findStop(trip, stopId));
    }

    @Transactional
    public StopResponse addStop(Long tripId, Long requesterId, CreateStopRequest request) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        int nextOrder = trip.getStops().stream().mapToInt(Stop::getStopOrder).max().orElse(-1) + 1;
        Stop stop = StopMapper.toEntity(request, resolvePlace(request), nextOrder);
        stop.setTrip(trip);
        trip.getStops().add(stop);
        tripRepository.save(trip);
        return StopMapper.toResponse(stop);
    }

    @Transactional
    public StopResponse updateStop(Long tripId, Long stopId, Long requesterId, UpdateStopRequest request) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        Stop stop = findStop(trip, stopId);

        stop.setPlace(resolvePlace(request.getName(), request.getLatitude(), request.getLongitude(),
                request.getAddress(), request.getExternalPlaceId()));
        stop.setNotes(request.getNotes());
        if (request.getStatus() != null) {
            stop.setStatus(request.getStatus());
        }

        tripRepository.save(trip);
        return StopMapper.toResponse(stop);
    }

    @Transactional
    public void deleteStop(Long tripId, Long stopId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        Stop stop = findStop(trip, stopId);
        trip.getStops().remove(stop); // orphanRemoval deletes the row; Place survives
        renumber(trip.getStops());
        tripRepository.save(trip);
    }

    // ---------- helpers ----------

    private Trip loadOwnedTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        if (!trip.getUser().getId().equals(requesterId)) {
            throw new ForbiddenException("You do not have access to this trip");
        }
        return trip;
    }

    private Stop findStop(Trip trip, Long stopId) {
        return trip.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stop not found: " + stopId + " in trip " + trip.getId()));
    }

    private void renumber(List<Stop> stops) {
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).setStopOrder(i);
        }
    }

    private Place resolvePlace(CreateStopRequest request) {
        return resolvePlace(request.getName(), request.getLatitude(), request.getLongitude(),
                request.getAddress(), request.getExternalPlaceId());
    }

    private Place resolvePlace(String name, Double latitude, Double longitude, String address, String externalPlaceId) {
        Optional<Place> existing = Optional.empty();
        if (externalPlaceId != null && !externalPlaceId.isBlank()) {
            existing = placeRepository.findByExternalPlaceId(externalPlaceId);
        }
        if (existing.isEmpty()) {
            existing = placeRepository.findByNameAndLatitudeAndLongitude(name, latitude, longitude);
        }
        if (existing.isPresent()) {
            return existing.get();
        }
        Place place = new Place();
        place.setName(name);
        place.setLatitude(latitude);
        place.setLongitude(longitude);
        place.setAddress(address);
        place.setExternalPlaceId(externalPlaceId);
        return placeRepository.save(place);
    }
}