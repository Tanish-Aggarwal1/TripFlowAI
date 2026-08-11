package com.tripflow.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.UpdateStopRequest;
import com.tripflow.backend.dto.UpsertStopRequest;
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

    /**
     * Reconciles a trip's stops against a full-replacement payload <em>by identity</em>, and is
     * the update-path counterpart to {@link #buildStops}.
     *
     * <p>This exists because the previous implementation (clear the collection, rebuild every
     * stop from scratch) was silent data loss: {@code Trip.stops} is {@code orphanRemoval = true}
     * and {@code stop_photos.stop_id} is {@code ON DELETE CASCADE} (V8), so replacing the stops
     * deleted every photo on the trip — and reset each stop's server-owned {@code status},
     * {@code dayNumber}, {@code plannedTime} and {@code stopType} — even for an edit that only
     * changed the trip title.
     *
     * <p>A request item carrying the id of a stop already on this trip updates that stop in
     * place; a null id inserts; an existing stop absent from the payload is dropped and
     * orphan-removed. Only genuinely dropped stops are deleted, so survivors keep their ids,
     * their scheduling, and their photos.
     *
     * <p>{@code stopOrder} follows payload order. Reassigning indices across surviving stops can
     * transiently collide on {@code uq_stops_trip_id_stop_order}, which is why V6 declares that
     * constraint {@code DEFERRABLE INITIALLY DEFERRED} — it is checked at commit, by which point
     * the whole payload has been applied.
     */
    void mergeStops(List<UpsertStopRequest> requests, Trip trip) {
        // Consumed by remove() below, so what remains after the loop is exactly the set the
        // payload dropped, and an id can't be claimed twice by one payload.
        Map<Long, Stop> unclaimed = trip.getStops().stream()
                .collect(Collectors.toMap(Stop::getId, Function.identity()));

        List<Place> places = placeResolutionService.resolvePlaces(
                requests.stream().map(UpsertStopRequest::toCreateRequest).toList());

        List<Stop> inserted = new ArrayList<>();
        for (int order = 0; order < requests.size(); order++) {
            UpsertStopRequest request = requests.get(order);
            Place place = places.get(order);

            if (request.id() == null) {
                Stop stop = stopMapper.toEntity(request.toCreateRequest(), place, order);
                stop.setTrip(trip);
                inserted.add(stop);
                continue;
            }

            // Not on this trip (or already claimed earlier in this payload). Never adopt it —
            // silently re-parenting another trip's stop would be a cross-tenant write. Reported
            // as not-found rather than forbidden so the id's existence elsewhere isn't disclosed.
            Stop existing = unclaimed.remove(request.id());
            if (existing == null) {
                throw new ResourceNotFoundException(
                        "Stop not found: " + request.id() + " in trip " + trip.getId());
            }
            existing.setPlace(place);
            existing.setNotes(request.notes());
            existing.setStopOrder(order);
            // status/dayNumber/plannedTime/stopType deliberately untouched — server-owned.
        }

        // Mutate in place rather than clear()+addAll(): only the stops the payload actually
        // dropped should reach orphanRemoval.
        if (!unclaimed.isEmpty()) {
            // Implicit deletion is the one remaining destructive path here — a client that omits
            // an id (a frontend bug, say) deletes that stop and cascades to its stop_photos.
            // Correct PUT semantics, but leave an audit trail so it isn't silent.
            log.info("Trip update dropping stops tripId={} stopIds={}", trip.getId(), unclaimed.keySet());
        }
        trip.getStops().removeAll(unclaimed.values());
        trip.getStops().addAll(inserted);
        trip.getStops().sort(Comparator.comparingInt(Stop::getStopOrder));
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
