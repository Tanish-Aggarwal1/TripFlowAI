package com.tripflow.backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final TripMapper tripMapper;
    private final StopMapper stopMapper;

    // ---------- Trips ----------

    @Transactional(readOnly = true)
    public List<TripResponse> listTrips(Long ownerId) {
        return tripRepository.findByUserIdOrderByCreatedAtDesc(ownerId).stream()
                .map(tripMapper::toResponse)
                .toList();
    }

    @Transactional
    public TripResponse createTrip(Long ownerId, CreateTripRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));

        Trip trip = tripMapper.toEntity(request, owner);

        int order = 0;
        for (CreateStopRequest stopRequest : request.stops()) {
            Stop stop = stopMapper.toEntity(stopRequest, resolvePlace(stopRequest), order++);
            stop.setTrip(trip);
            trip.getStops().add(stop);
        }
        
        Trip saved = tripRepository.save(trip);
        log.info("Trip created id={} ownerId={} stops={}", saved.getId(), ownerId, saved.getStops().size());

        return tripMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        boolean isOwner = trip.getUser().getId().equals(requesterId);
        if (trip.getVisibility() == TripVisibility.PRIVATE && !isOwner) {
        	log.debug("Private trip access denied tripId={} requesterId={}", tripId, requesterId);
            throw new ForbiddenException("You do not have access to this trip");
        }
        return tripMapper.toResponse(trip);
    }

    @Transactional
    public TripResponse updateTrip(Long tripId, Long requesterId, UpdateTripRequest request) {
        Trip trip = loadOwnedTrip(tripId, requesterId);

        trip.setTitle(request.title());
        trip.setDescription(request.description());
        trip.setTags(request.tags());
        trip.setVisibility(request.visibility());
        // status is server-owned lifecycle state — intentionally not touched here

        // Full itinerary replace. orphanRemoval deletes dropped stops; shared Places survive.
        trip.getStops().clear();
        int order = 0;
        for (CreateStopRequest stopRequest : request.stops()) {
            Stop stop = stopMapper.toEntity(stopRequest, resolvePlace(stopRequest), order++);
            stop.setTrip(trip);
            trip.getStops().add(stop);
        }

        Trip saved = tripRepository.save(trip);
        log.info("Trip updated id={} ownerId={} stops={}", saved.getId(), requesterId, saved.getStops().size());
        return tripMapper.toResponse(saved);
    }

    @Transactional
    public void deleteTrip(Long tripId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        tripRepository.delete(trip); // cascade + FK ON DELETE CASCADE remove stops; Places survive
        log.info("Trip deleted id={} ownerId={}", tripId, requesterId);
    }


    // ---------- Nested stops (owner-scoped) ----------

    @Transactional(readOnly = true)
    public List<StopResponse> listStops(Long tripId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        return trip.getStops().stream().map(stopMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StopResponse getStop(Long tripId, Long stopId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        return stopMapper.toResponse(findStop(trip, stopId));
    }

    @Transactional
    public StopResponse addStop(Long tripId, Long requesterId, CreateStopRequest request) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        int nextOrder = trip.getStops().stream().mapToInt(Stop::getStopOrder).max().orElse(-1) + 1;
        Stop stop = stopMapper.toEntity(request, resolvePlace(request), nextOrder);
        stop.setTrip(trip);
        trip.getStops().add(stop);
        tripRepository.save(trip);
        log.info("Stop added tripId={} stopId={} order={}", tripId, stop.getId(), nextOrder);
        return stopMapper.toResponse(stop);
    }

    @Transactional
    public StopResponse updateStop(Long tripId, Long stopId, Long requesterId, UpdateStopRequest request) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        Stop stop = findStop(trip, stopId);

        stop.setPlace(resolvePlace(request.name(), request.latitude(), request.longitude(),
                request.address(), request.externalPlaceId()));
        stop.setNotes(request.notes());
        if (request.status() != null) {
            stop.setStatus(request.status());
        }

        tripRepository.save(trip);
        log.info("Stop updated tripId={} stopId={}", tripId, stopId);
        return stopMapper.toResponse(stop);
    }

    @Transactional
    public void deleteStop(Long tripId, Long stopId, Long requesterId) {
        Trip trip = loadOwnedTrip(tripId, requesterId);
        Stop stop = findStop(trip, stopId);
        trip.getStops().remove(stop); // orphanRemoval deletes the row; Place survives
        renumber(trip.getStops());
        tripRepository.save(trip);
        log.info("Stop deleted tripId={} stopId={}", tripId, stopId);
    }

    // ---------- helpers ----------

    private Trip loadOwnedTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        if (!trip.getUser().getId().equals(requesterId)) {
        	log.debug("Trip ownership check failed tripId={} ownerId={} requesterId={}",
                    tripId, trip.getUser().getId(), requesterId);
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
        return resolvePlace(request.name(), request.latitude(), request.longitude(),
                request.address(), request.externalPlaceId());
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