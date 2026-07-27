package com.tripflow.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.backend.domain.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
	Optional<Place> findByExternalPlaceId(String externalPlaceId);

	/**
	 * Dedup fallback lookup by name+coordinates. {@code idx_places_name_lat_lng}
	 * (V3__create_places.sql) is deliberately non-unique — two places can legitimately
	 * share a name and near-coordinates — so this must never assume at most one row
	 * matches. {@code findFirst...OrderById} picks a single, deterministic result
	 * (the oldest matching row) instead of throwing IncorrectResultSizeDataAccessException
	 * when more than one row matches (SCRUM-216).
	 */
	Optional<Place> findFirstByNameAndLatitudeAndLongitudeOrderById(String name, Double latitude, Double longitude);
}
