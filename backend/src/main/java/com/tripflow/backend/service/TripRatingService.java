package com.tripflow.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.repository.TripRatingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate a trip 1-5 stars (SOCIAL-07). Unlike {@link TripLikeService}/{@link TripSaveService},
 * a rating is a value the user can change, not a toggle — there is no "unrate" method;
 * removing a rating entirely is out of scope for SOCIAL-07.
 *
 * <p>A requester may rate any PUBLIC trip, or their own PRIVATE trips; a PRIVATE trip
 * belonging to someone else is reported as 404 (not 403) to avoid leaking its existence,
 * matching the discovery-feed/like/save/clone access convention (SCRUM-274).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripRatingService {

    private final TripRatingRepository tripRatingRepository;
    private final TripOwnershipService tripOwnershipService;

    @Transactional
    public void rateTrip(Long tripId, Long requesterId, int rating) {
        tripOwnershipService.loadVisibleTripLite(tripId, requesterId);

        tripRatingRepository.upsertRating(requesterId, tripId, rating);
        log.info("Trip rated tripId={} userId={} rating={}", tripId, requesterId, rating);
    }
}
