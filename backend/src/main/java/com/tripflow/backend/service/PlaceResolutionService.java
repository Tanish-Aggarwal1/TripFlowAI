package com.tripflow.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds or creates the {@link Place} a stop points to, deduplicating by external place id
 * (when present) or by name+coordinates. Extracted out of {@link TripService} (SCRUM-215)
 * so place resolution exists in exactly one class, shared by both {@link TripService} (via
 * {@link StopService#buildStops}) and {@link StopService} directly.
 *
 * <p>Coordinates are rounded to {@value #COORDINATE_SCALE} decimal places (~1m of precision
 * at the equator) before every lookup and before persisting a new {@link Place} (SCRUM-216):
 * comparing raw {@code Double}s for dedup means two requests for the same physical place
 * (e.g. {@code 45.0} vs {@code 45.000000001}, a routine float round-trip through a client)
 * silently fail to match and the table quietly accumulates near-duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceResolutionService {

    /** Decimal places coordinates are rounded to before comparison/persistence. */
    static final int COORDINATE_SCALE = 5;
    private static final double COORDINATE_SCALE_FACTOR = 100_000.0; // 10^COORDINATE_SCALE

    private final PlaceRepository placeRepository;

    public Place resolvePlace(CreateStopRequest request) {
        return resolvePlace(request.name(), request.latitude(), request.longitude(),
                request.address(), request.externalPlaceId());
    }

    public Place resolvePlace(String name, Double latitude, Double longitude, String address, String externalPlaceId) {
        double roundedLatitude = round(latitude);
        double roundedLongitude = round(longitude);

        return findExistingPlace(name, roundedLatitude, roundedLongitude, externalPlaceId)
                .orElseGet(() -> createPlace(name, roundedLatitude, roundedLongitude, address, externalPlaceId));
    }

    /**
     * Batched form of {@link #resolvePlace(CreateStopRequest)} for a whole trip's worth of
     * stops at once (SCRUM-267) — {@link StopService#buildStops} was issuing 1-2 SELECTs
     * per stop in a loop. Runs at most 2 SELECTs total (one by external id, one by name)
     * instead of one round trip per request, then matches/creates in memory. Order of the
     * returned list matches {@code requests}.
     */
    public List<Place> resolvePlaces(List<CreateStopRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        Set<String> externalIds = requests.stream()
                .map(CreateStopRequest::externalPlaceId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, Place> byExternalId = externalIds.isEmpty() ? new HashMap<>()
                : placeRepository.findByExternalPlaceIdIn(externalIds).stream()
                        .collect(Collectors.toMap(Place::getExternalPlaceId, p -> p, (a, b) -> a));

        Set<String> names = requests.stream().map(CreateStopRequest::name).collect(Collectors.toSet());
        Map<String, List<Place>> byName = placeRepository.findByNameIn(names).stream()
                .collect(Collectors.groupingBy(Place::getName));

        List<Place> results = new ArrayList<>(requests.size());
        for (CreateStopRequest request : requests) {
            double roundedLatitude = round(request.latitude());
            double roundedLongitude = round(request.longitude());
            String externalPlaceId = request.externalPlaceId();

            Place match = null;
            if (externalPlaceId != null && !externalPlaceId.isBlank()) {
                match = byExternalId.get(externalPlaceId);
            }
            if (match == null) {
                match = byName.getOrDefault(request.name(), List.of()).stream()
                        .filter(p -> p.getLatitude().equals(roundedLatitude) && p.getLongitude().equals(roundedLongitude))
                        .findFirst()
                        .orElse(null);
            }
            if (match == null) {
                match = createPlace(request.name(), roundedLatitude, roundedLongitude,
                        request.address(), externalPlaceId);
                // Cache it so a later duplicate request in this same batch (e.g. two AI-generated
                // stops pointing at the same place) reuses it instead of racing its own insert.
                if (externalPlaceId != null && !externalPlaceId.isBlank()) {
                    byExternalId.put(externalPlaceId, match);
                }
                byName.computeIfAbsent(request.name(), k -> new ArrayList<>()).add(match);
            }
            results.add(match);
        }
        return results;
    }

    private Place createPlace(String name, double roundedLatitude, double roundedLongitude,
            String address, String externalPlaceId) {
        Place place = new Place();
        place.setName(name);
        place.setLatitude(roundedLatitude);
        place.setLongitude(roundedLongitude);
        place.setAddress(address);
        place.setExternalPlaceId(externalPlaceId);
        try {
            return placeRepository.save(place);
        } catch (DataIntegrityViolationException ex) {
            // Another request won the race against the partial unique index on
            // external_place_id (V3__create_places.sql) between our lookup and this save.
            // Re-read and reuse its row instead of surfacing a 500 to the client.
            log.debug("Place save raced on externalPlaceId={}, re-reading existing row", externalPlaceId);
            return findExistingPlace(name, roundedLatitude, roundedLongitude, externalPlaceId)
                    .orElseThrow(() -> ex);
        }
    }

    private Optional<Place> findExistingPlace(String name, Double latitude, Double longitude, String externalPlaceId) {
        if (externalPlaceId != null && !externalPlaceId.isBlank()) {
            Optional<Place> byExternalId = placeRepository.findByExternalPlaceId(externalPlaceId);
            if (byExternalId.isPresent()) {
                return byExternalId;
            }
        }
        // Non-unique by design (legitimate distinct places can share a name and
        // near-coordinates) — findFirst...OrderById returns one deterministic row instead
        // of throwing when more than one matches.
        return placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById(name, latitude, longitude);
    }

    private static double round(double value) {
        return Math.round(value * COORDINATE_SCALE_FACTOR) / COORDINATE_SCALE_FACTOR;
    }
}
