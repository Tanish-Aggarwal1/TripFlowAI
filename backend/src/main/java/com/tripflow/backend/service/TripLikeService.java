package com.tripflow.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.exception.ResourceNotFoundException;
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

    @Transactional
    public void likeTrip(Long tripId, Long requesterId) {
        loadVisibleTrip(tripId, requesterId);

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
        loadVisibleTrip(tripId, requesterId);

        int deleted = tripLikeRepository.deleteByUserIdAndTripId(requesterId, tripId);
        if (deleted > 0) {
            tripRepository.decrementLikeCount(tripId);
            log.info("Trip unliked tripId={} userId={}", tripId, requesterId);
        } else {
            log.debug("Trip was not liked tripId={} userId={}", tripId, requesterId);
        }
    }

    /** Owner or public-trip access; PRIVATE + non-owner is reported as not-found. */
    private Trip loadVisibleTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        boolean isOwner = trip.getUser().getId().equals(requesterId);
        if (trip.getVisibility() == TripVisibility.PRIVATE && !isOwner) {
            log.debug("Private trip like/unlike denied tripId={} requesterId={}", tripId, requesterId);
            throw new ResourceNotFoundException("Trip not found: " + tripId);
        }
        return trip;
    }
}
