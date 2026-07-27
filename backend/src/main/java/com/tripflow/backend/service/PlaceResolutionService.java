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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceResolutionService {

    private final PlaceRepository placeRepository;

    public Place resolvePlace(CreateStopRequest request) {
        return resolvePlace(request.name(), request.latitude(), request.longitude(),
                request.address(), request.externalPlaceId());
    }

    public Place resolvePlace(String name, Double latitude, Double longitude, String address, String externalPlaceId) {
        Optional<Place> existing = findExistingPlace(name, latitude, longitude, externalPlaceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Place place = new Place();
        place.setName(name);
        place.setLatitude(latitude);
        place.setLongitude(longitude);
        place.setAddress(address);
        place.setExternalPlaceId(externalPlaceId);
        try {
            return placeRepository.save(place);
        } catch (DataIntegrityViolationException ex) {
            // Another request won the race against the partial unique index on
            // external_place_id (V3__create_places.sql) between our lookup and this save.
            // Re-read and reuse its row instead of surfacing a 500 to the client.
            log.debug("Place save raced on externalPlaceId={}, re-reading existing row", externalPlaceId);
            return findExistingPlace(name, latitude, longitude, externalPlaceId)
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
        return placeRepository.findByNameAndLatitudeAndLongitude(name, latitude, longitude);
    }
}
