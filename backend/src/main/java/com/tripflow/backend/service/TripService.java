package com.tripflow.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.FeedTripResponse;
import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.dto.TripSearchFilters;
import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.exception.InvalidRequestException;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.TripSearchRepositoryImpl;
import com.tripflow.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Trip CRUD only (SCRUM-215) — stop CRUD lives in {@link StopService}, place
 * resolution in {@link PlaceResolutionService}.
 * {@code createTrip}/{@code updateTrip} still build each trip's
 * initial/replacement stop list, but delegate the actual stop-building (place
 * lookup + ordering) to {@link StopService#buildStops} so that logic exists in
 * exactly one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TripMapper tripMapper;
    private final TripOwnershipService tripOwnershipService;
    private final StopService stopService;

    @Transactional(readOnly = true)
    public Page<TripOwnerSummaryResponse> listTrips(Long ownerId, Pageable pageable) {
        return tripRepository.findSummariesByUserId(ownerId, pageable);
    }

    /**
     * SEARCH-01/D-09: search + filter over the owner's own trips only. Unlike
     * {@link #searchPublicTrips}, a blank {@code search} is not an error — it means
     * "show me everything", so it returns the owner's full list rather than a 400.
     */
    @Transactional(readOnly = true)
    public Page<TripOwnerSummaryResponse> searchOwnedTrips(
            Long ownerId, String search, TripSearchFilters filters, Pageable pageable) {
        String trimmed = search == null ? "" : search.trim();
        String pattern = trimmed.isEmpty() ? null : TripSearchRepositoryImpl.likePattern(trimmed);
        return tripRepository.searchOwnedTrips(ownerId, pattern, filters, pageable);
    }

    /**
     * SCRUM-163: case-insensitive substring search over PUBLIC trip titles/tags. MVP
     * only — see {@link com.tripflow.backend.repository.TripSearchRepository} javadoc
     * for the explicit full-text-search non-goal.
     */
    private static final Sort SEARCH_PUBLIC_TRIPS_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Transactional(readOnly = true)
    public Page<TripSummaryResponse> searchPublicTrips(String q, Pageable pageable) {
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidRequestException("Query parameter 'q' must not be blank");
        }
        // SCRUM-415: TripSearchRepositoryImpl hardcodes ORDER BY created_at DESC, id DESC and
        // never reads pageable.getSort() — silently honoring a non-default `sort` here would be
        // the exact "well-formed but wrong" bug the finding describes, so reject it instead.
        if (!pageable.getSort().isEmpty() && !pageable.getSort().equals(SEARCH_PUBLIC_TRIPS_SORT)) {
            throw new InvalidRequestException(
                    "Custom 'sort' is not supported on this endpoint; results are always ordered by createdAt desc");
        }
        return tripRepository.searchPublicTrips(trimmed, pageable);
    }
        
    @Transactional(readOnly = true)
    public Page<TripSummaryResponse> listPublicTrips(Pageable pageable) {
        return tripRepository.findSummariesByVisibility(TripVisibility.PUBLIC, pageable);
    }

    /**
     * SOCIAL-01: authenticated, full-card feed of PUBLIC trips. {@code viewerId} is accepted
     * now even though this task does not branch on it — 06-06 (interest-based ranking) needs
     * it and adding the parameter later would ripple through the controller and both test
     * classes. Ordered by recency only; ranking is explicitly out of scope here (06-06 owns it).
     *
     * <p>Photo lookup is a no-op empty list in this task — Task 3 wires the batched
     * {@code StopPhotoRepository.findByStopIdInOrderByCreatedAtAsc} fetch.
     */
    @Transactional(readOnly = true)
    public Page<FeedTripResponse> listFeed(Long viewerId, Pageable pageable) {
        Page<Trip> page = tripRepository.findByVisibility(TripVisibility.PUBLIC, pageable);
        log.debug("Feed page loaded viewerId={} trips={}", viewerId, page.getNumberOfElements());
        return page.map(this::toFeedResponse);
    }

    private FeedTripResponse toFeedResponse(Trip trip) {
        List<FeedTripResponse.FeedStop> stops = trip.getStops().stream()
                .map(this::toFeedStop)
                .toList();
        return new FeedTripResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getDescription(),
                trip.getTags(),
                trip.getUser().getUsername(),
                trip.getLikeCount(),
                trip.getCreatedAt(),
                stops
        );
    }

    private FeedTripResponse.FeedStop toFeedStop(Stop stop) {
        return new FeedTripResponse.FeedStop(
                stop.getId(),
                stop.getPlace().getName(),
                stop.getPlace().getAddress(),
                stop.getStopOrder(),
                stop.getNotes(),
                List.of()
        );
    }

    @Transactional
    public TripResponse createTrip(Long ownerId, CreateTripRequest request) {
        // ownerId always comes from an authenticated JWT principal tied to a real user row,
        // so a lazy reference (no owner SELECT) is safe here — unlike loadOwnedTrip, which
        // reads a caller-supplied id that may not exist.
        User owner = userRepository.getReferenceById(ownerId);

        Trip trip = tripMapper.toEntity(request, owner);
        trip.getStops().addAll(stopService.buildStops(request.stops(), trip));

        Trip saved = tripRepository.save(trip);
        log.info("Trip created id={} ownerId={} stops={}", saved.getId(), ownerId, saved.getStops().size());

        return tripMapper.toResponse(saved);
    }

	// SCRUM-71a: a non-owner hitting a PRIVATE trip gets ResourceNotFoundException (404),
	// not ForbiddenException (403) — a 403 would confirm the trip id exists, leaking its
	// existence to someone who isn't allowed to know that. 404 is indistinguishable from
	// "no trip with this id." (SCRUM-419: owner-or-public check centralised in
	// TripOwnershipService.loadVisibleTrip.)
	@Transactional(readOnly = true)
	public TripResponse getTrip(Long tripId, Long requesterId) {
		Trip trip = tripOwnershipService.loadVisibleTrip(tripId, requesterId);
		return tripMapper.toResponse(trip);
	}

	// SCRUM-71a: PATCH /api/trips/{id}/visibility. Owner-only (via
	// TripOwnershipService,
	// same 404/403 semantics as updateTrip/deleteTrip) — flips PRIVATE <-> PUBLIC
	// rather
	// than accepting an explicit target value, since that's the only operation the
	// endpoint exposes and it keeps the request bodyless.
	@Transactional
	public TripResponse toggleVisibility(Long tripId, Long requesterId) {
		Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);

		TripVisibility next = trip.getVisibility() == TripVisibility.PRIVATE ? TripVisibility.PUBLIC
				: TripVisibility.PRIVATE;
		trip.setVisibility(next);

		Trip saved = tripRepository.save(trip);
		log.info("Trip visibility toggled id={} ownerId={} visibility={}", saved.getId(), requesterId, next);
		return tripMapper.toResponse(saved);
	}

	@Transactional
	public TripResponse updateTrip(Long tripId, Long requesterId, UpdateTripRequest request) {
		Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);

		trip.setTitle(request.title());
		trip.setDescription(request.description());
		trip.setTags(request.tags() != null ? new ArrayList<>(request.tags()) : new ArrayList<>());
		trip.setVisibility(request.visibility());
		// Absent startDate means "leave unchanged", not "clear". A record can't tell an
		// omitted JSON field from an explicit null, and the 5-arg convenience constructor
		// passes null, so treating null as a clear silently wiped the date on every update
		// that didn't restate it. The trade-off is that startDate can't be cleared through
		// this endpoint; nothing exposes that today.
		if (request.startDate() != null) {
			trip.setStartDate(request.startDate());
		}
		// status is server-owned lifecycle state — intentionally not touched here

		// Merge by stop id rather than replace: surviving stops keep their photos and their
		// status/dayNumber/plannedTime/stopType. See StopService#mergeStops.
		stopService.mergeStops(request.stops(), trip);

		Trip saved = tripRepository.save(trip);
		log.info("Trip updated id={} ownerId={} stops={}", saved.getId(), requesterId, saved.getStops().size());
		return tripMapper.toResponse(saved);
	}

	@Transactional
	public void deleteTrip(Long tripId, Long requesterId) {
		Trip trip = tripOwnershipService.loadOwnedTrip(tripId, requesterId);
		tripRepository.delete(trip); // cascade + FK ON DELETE CASCADE remove stops; Places survive
		log.info("Trip deleted id={} ownerId={}", tripId, requesterId);
	}
}
