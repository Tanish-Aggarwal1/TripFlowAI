package com.tripflow.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.repository.TripLikeRepository;
import com.tripflow.backend.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Like/unlike a trip (SCRUM-161). A requester may like any PUBLIC trip, or their own
 * PRIVATE trips; a PRIVATE trip belonging to someone else is reported as 404 (not 403)
 * to avoid leaking its existence, matching the discovery-feed access convention.
 *
 * <p>Both operations are idempotent and concurrency-safe: {@link TripLikeRepository}'s
 * {@code INSERT ... ON CONFLICT DO NOTHING} / {@code DELETE} do the existence check and
 * mutation atomically at the database, and {@code Trip.likeCount} is only bumped when
 * that call actually changed a row — never a Java read-modify-write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripLikeService {

    private final TripRepository tripRepository;
    private final TripLikeRepository tripLikeRepository;
    private final TripOwnershipService tripOwnershipService;

    @Transactional
    public void likeTrip(Long tripId, Long requesterId) {
        // findWithStopsById isn't needed here (SCRUM-419): loadVisibleTripLite uses the
        // cheaper plain findById since likeTrip never touches the stop collection.
        tripOwnershipService.loadVisibleTripLite(tripId, requesterId);

        int inserted = tripLikeRepository.insertIfAbsent(requesterId, tripId);
        if (inserted > 0) {
            tripRepository.incrementLikeCount(tripId);
            log.info("Trip liked tripId={} userId={}", tripId, requesterId);
        } else {
            log.debug("Trip already liked tripId={} userId={}", tripId, requesterId);
        }
    }

    @Transactional
    public void unlikeTrip(Long tripId, Long requesterId) {
        tripOwnershipService.loadVisibleTripLite(tripId, requesterId);

        int deleted = tripLikeRepository.deleteByUserIdAndTripId(requesterId, tripId);
        if (deleted > 0) {
            tripRepository.decrementLikeCount(tripId);
            log.info("Trip unliked tripId={} userId={}", tripId, requesterId);
        } else {
            log.debug("Trip was not liked tripId={} userId={}", tripId, requesterId);
        }
    }
}
