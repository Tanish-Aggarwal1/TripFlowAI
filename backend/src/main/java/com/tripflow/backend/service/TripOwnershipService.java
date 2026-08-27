package com.tripflow.backend.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared trip existence + ownership check, previously copy-pasted (find trip → check
 * owner → throw 404/403) into {@link TripService}, {@link RouteOptimizationService}, and
 * {@link AiItineraryService}. Extracted so the access-control logic can't silently
 * diverge across services (REF-40).
 *
 * <p>{@code @Transactional} lives here rather than on the calling services (SCRUM-210):
 * this is a genuinely different bean, so calling it from a non-transactional service
 * method opens and commits a short read-only transaction for just the load, then
 * returns — no database connection is held across whatever the caller does next (e.g.
 * an external HTTP call). Callers that are already transactional (e.g. {@code TripService})
 * simply have this join their existing transaction, unchanged.
 *
 * <p>The owner-or-public "visible" variant (SCRUM-419) was independently re-duplicated
 * into {@code TripService}, {@code TripCloneService}, {@code TripLikeService}, and
 * {@code StopPhotoService} after this class was extracted for the owner-only case —
 * {@link #isVisible} centralises the rule itself, with {@link #loadVisibleTrip} and
 * {@link #loadVisibleTripLite} as fetch-strategy variants for callers that do/don't need
 * stops eagerly loaded, and {@link #isVisible} exposed directly for callers (like
 * {@code StopPhotoService}) that already hold a {@link Trip} loaded via a different path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripOwnershipService {

    private final TripRepository tripRepository;

    /**
     * Loads a trip (with stops) and verifies the requester owns it.
     *
     * @throws ResourceNotFoundException if the trip does not exist
     * @throws ForbiddenException        if the requester is not the trip owner
     */
    @Transactional(readOnly = true)
    public Trip loadOwnedTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findWithStopsById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        if (!trip.getUser().getId().equals(requesterId)) {
            log.debug("Trip ownership check failed tripId={} ownerId={} requesterId={}",
                    tripId, trip.getUser().getId(), requesterId);
            throw new ForbiddenException("You do not have access to this trip");
        }
        return trip;
    }

    /**
     * Owner-or-public access: true if the requester owns the trip, or the trip is not
     * PRIVATE. Callers that already hold a {@link Trip} (e.g. loaded via a parent
     * entity) should call this directly rather than going through {@link #findVisible};
     * the 404-vs-leak decision on a negative result is the caller's, since the "not
     * found" message differs per resource (e.g. "Trip not found" vs "Stop not found").
     */
    public boolean isVisible(Trip trip, Long requesterId) {
        boolean isOwner = trip.getUser().getId().equals(requesterId);
        return isOwner || trip.getVisibility() != TripVisibility.PRIVATE;
    }

    /**
     * Loads a trip with stops and verifies owner-or-public visibility.
     *
     * @throws ResourceNotFoundException if the trip does not exist, or is PRIVATE and the
     *                                    requester isn't the owner (existence not disclosed)
     */
    @Transactional(readOnly = true)
    public Trip loadVisibleTrip(Long tripId, Long requesterId) {
        return findVisible(tripRepository.findWithStopsById(tripId), tripId, requesterId);
    }

    /**
     * Loads a trip without stops and verifies owner-or-public visibility — for callers
     * that don't need the stop collection eagerly fetched.
     *
     * @throws ResourceNotFoundException if the trip does not exist, or is PRIVATE and the
     *                                    requester isn't the owner (existence not disclosed)
     */
    @Transactional(readOnly = true)
    public Trip loadVisibleTripLite(Long tripId, Long requesterId) {
        return findVisible(tripRepository.findById(tripId), tripId, requesterId);
    }

    private Trip findVisible(Optional<Trip> found, Long tripId, Long requesterId) {
        Trip trip = found.orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        if (!isVisible(trip, requesterId)) {
            log.debug("Private trip access denied (404, existence not disclosed) tripId={} requesterId={}",
                    tripId, requesterId);
            throw new ResourceNotFoundException("Trip not found: " + tripId);
        }
        return trip;
    }
}
