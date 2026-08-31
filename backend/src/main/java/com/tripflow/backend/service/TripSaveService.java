package com.tripflow.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.repository.SavedTripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Save/unsave a trip to a user's private saved-trips list (SOCIAL-04). A requester may
 * save any PUBLIC trip, or their own PRIVATE trips; a PRIVATE trip belonging to someone
 * else is reported as 404 (not 403) to avoid leaking its existence, matching the
 * discovery-feed/like/clone access convention (SCRUM-274).
 *
 * <p>Both operations are idempotent and concurrency-safe: {@link SavedTripRepository}'s
 * {@code INSERT ... ON CONFLICT DO NOTHING} / {@code DELETE} do the existence check and
 * mutation atomically at the database. Unlike {@code TripLikeService}, there is no
 * denormalized count to bump — nothing displays a save count.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripSaveService {

    private final SavedTripRepository savedTripRepository;
    private final TripOwnershipService tripOwnershipService;

    @Transactional
    public void saveTrip(Long tripId, Long requesterId) {
        tripOwnershipService.loadVisibleTripLite(tripId, requesterId);

        int inserted = savedTripRepository.insertIfAbsent(requesterId, tripId);
        if (inserted > 0) {
            log.info("Trip saved tripId={} userId={}", tripId, requesterId);
        } else {
            log.debug("Trip already saved tripId={} userId={}", tripId, requesterId);
        }
    }

    @Transactional
    public void unsaveTrip(Long tripId, Long requesterId) {
        tripOwnershipService.loadVisibleTripLite(tripId, requesterId);

        int deleted = savedTripRepository.deleteByUserIdAndTripId(requesterId, tripId);
        if (deleted > 0) {
            log.info("Trip unsaved tripId={} userId={}", tripId, requesterId);
        } else {
            log.debug("Trip was not saved tripId={} userId={}", tripId, requesterId);
        }
    }

    /**
     * Paged read of the requester's own saved trips. Scoped strictly to
     * {@code requesterId} — never accepts a user id from the request, so one user's saves
     * can never be listed by another (SOCIAL-04).
     */
    @Transactional(readOnly = true)
    public Page<TripOwnerSummaryResponse> listSaved(Long requesterId, Pageable pageable) {
        return savedTripRepository.findSavedTripsByUserId(requesterId, pageable);
    }
}
