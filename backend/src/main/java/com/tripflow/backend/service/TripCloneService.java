package com.tripflow.backend.service;

import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deep-copies a trip into the requester's account (SCRUM-162): a fresh, PRIVATE
 * {@link Trip} owned by the requester with cloned {@link Stop}s in the same order.
 * {@link com.tripflow.backend.domain.Place} rows are shared, never duplicated — stops
 * are cloned by pointing at the same Place, per the shared-Place design. Photos and
 * likes are deliberately not carried over: the clone starts with zero of both (no
 * StopPhoto rows are copied, and a new Trip naturally starts with like_count = 0).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripCloneService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripMapper tripMapper;

    @Transactional
    public TripResponse cloneTrip(Long tripId, Long requesterId) {
        Trip original = loadVisibleTrip(tripId, requesterId);
        User requester = userRepository.getReferenceById(requesterId);

        Trip clone = new Trip();
        clone.setUser(requester);
        clone.setTitle("Copy of " + original.getTitle());
        clone.setDescription(original.getDescription());
        clone.setTags(new ArrayList<>(original.getTags()));
        clone.setVisibility(TripVisibility.PRIVATE);
        clone.setStatus(TripStatus.DRAFT);
        clone.setStartDate(original.getStartDate());
        // routeGeometry, like_count NOT copied: geometry is tied to a specific
        // optimization run against the original trip and a fresh clone hasn't been
        // optimized; likeCount and stop_photos always start empty for a clone.

        for (Stop originalStop : original.getStops()) {
            Stop cloneStop = new Stop();
            cloneStop.setTrip(clone);
            cloneStop.setPlace(originalStop.getPlace()); // shared Place, not duplicated
            cloneStop.setStopOrder(originalStop.getStopOrder());
            cloneStop.setStatus(originalStop.getStatus());
            cloneStop.setNotes(originalStop.getNotes());
            cloneStop.setDayNumber(originalStop.getDayNumber());
            cloneStop.setPlannedTime(originalStop.getPlannedTime());
            cloneStop.setStopType(originalStop.getStopType());
            clone.getStops().add(cloneStop);
        }

        Trip saved = tripRepository.save(clone);
        log.info("Trip cloned sourceId={} newId={} ownerId={} stops={}",
                original.getId(), saved.getId(), requesterId, saved.getStops().size());
        return tripMapper.toResponse(saved);
    }

    /** Owner or public-trip access; PRIVATE + non-owner is reported as not-found (no existence leak). */
    private Trip loadVisibleTrip(Long tripId, Long requesterId) {
        Trip trip = tripRepository.findWithStopsById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        boolean isOwner = trip.getUser().getId().equals(requesterId);
        if (trip.getVisibility() == TripVisibility.PRIVATE && !isOwner) {
            log.debug("Private trip clone denied tripId={} requesterId={}", tripId, requesterId);
            throw new ResourceNotFoundException("Trip not found: " + tripId);
        }
        return trip;
    }
}
