package com.tripflow.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.UpdateStopRequest;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nested stop CRUD under a trip, owner-scoped. Extracted out of {@link TripService}
 * (SCRUM-215) — {@link com.tripflow.backend.controller.StopController} injects this
 * directly instead of reaching into a different resource's service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopService {

    private final TripRepository tripRepository;
    private final TripOwnershipService tripOwnershipService;
    private final PlaceResolutionService placeResolutionService;
    private final StopMapper stopMapper;

    @Transactional(readOnly = true)
    public List<StopResponse> listStops(Long tripId, Long requesterId) {
        Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);
        return trip.getStops().stream().map(stopMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StopResponse getStop(Long tripId, Long stopId, Long requesterId) {
        Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);
        return stopMapper.toResponse(findStop(trip, stopId));
    }

    @Transactional
    public StopResponse addStop(Long tripId, Long requesterId, CreateStopRequest request) {
        Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);
        int nextOrder = trip.getStops().stream().mapToInt(Stop::getStopOrder).max().orElse(-1) + 1;
        Stop stop = stopMapper.toEntity(request, placeResolutionService.resolvePlace(request), nextOrder);
        stop.setTrip(trip);
        trip.getStops().add(stop);
        tripRepository.save(trip);
        log.info("Stop added tripId={} stopId={} order={}", tripId, stop.getId(), nextOrder);
        return stopMapper.toResponse(stop);
    }

    @Transactional
    public StopResponse updateStop(Long tripId, Long stopId, Long requesterId, UpdateStopRequest request) {
        Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);
        Stop stop = findStop(trip, stopId);

        stop.setPlace(placeResolutionService.resolvePlace(request.name(), request.latitude(), request.longitude(),
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
        Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);
        Stop stop = findStop(trip, stopId);
        trip.getStops().remove(stop); // orphanRemoval deletes the row; Place survives
        renumber(trip.getStops());
        tripRepository.save(trip);
        log.info("Stop deleted tripId={} stopId={}", tripId, stopId);
    }

    /**
     * Builds a fresh, ordered {@link Stop} list from create requests, resolving each stop's
     * {@link com.tripflow.backend.domain.Place} along the way. Shared by {@link TripService}'s
     * {@code createTrip}/{@code updateTrip} (full itinerary replace) and this class's own
     * {@link #addStop} — the one place stop-ordering + place-lookup logic exists.
     */
    List<Stop> buildStops(List<CreateStopRequest> requests, Trip trip) {
        List<Place> places = placeResolutionService.resolvePlaces(requests);
        List<Stop> stops = new ArrayList<>();
        for (int order = 0; order < requests.size(); order++) {
            Stop stop = stopMapper.toEntity(requests.get(order), places.get(order), order);
            stop.setTrip(trip);
            stops.add(stop);
        }
        return stops;
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
}
