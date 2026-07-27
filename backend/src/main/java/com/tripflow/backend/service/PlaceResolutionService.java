package com.tripflow.backend.service;

import java.util.Optional;

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

        Optional<Place> existing = findExistingPlace(name, roundedLatitude, roundedLongitude, externalPlaceId);
        if (existing.isPresent()) {
            return existing.get();
        }
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
