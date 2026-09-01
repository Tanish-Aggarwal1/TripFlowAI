package com.tripflow.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.dto.TripRatingSummaryResponse;
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

    /**
     * Average, count and the caller's own rating for a trip. Deliberately not folded into
     * {@code FeedTripResponse} — that would mean either a second aggregate per trip on
     * every feed page, or a denormalized average column to keep in sync. The action rail
     * fetches this per card as it becomes active instead (RESEARCH.md Pitfall 2).
     *
     * <p>Calls {@code loadVisibleTripLite} so the read path honours the same 404-not-403
     * existence-hiding rule as {@link #rateTrip} — the summary endpoint must not become an
     * oracle that discloses a PRIVATE trip's existence the write path refuses to.
     */
    @Transactional(readOnly = true)
    public TripRatingSummaryResponse getSummary(Long tripId, Long requesterId) {
        tripOwnershipService.loadVisibleTripLite(tripId, requesterId);

        // The repository's Object[] return type is itself a single-element wrapper around
        // the query's one aggregate row (also an Object[]) — Spring Data treats an array
        // return type as a collection projection, not the row tuple directly.
        Object[] row = (Object[]) tripRatingRepository.findAverageAndCountByTripId(tripId)[0];
        Double averageRating = (Double) row[0];
        long ratingCount = (Long) row[1];
        Integer myRating = tripRatingRepository.findRatingByUserIdAndTripId(requesterId, tripId).orElse(null);

        return new TripRatingSummaryResponse(averageRating, ratingCount, myRating);
    }
}
