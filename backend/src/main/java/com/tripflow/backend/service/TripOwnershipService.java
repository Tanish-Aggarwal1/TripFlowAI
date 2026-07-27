package com.tripflow.backend.service;

import org.springframework.stereotype.Component;

import com.tripflow.backend.domain.Trip;
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
}
