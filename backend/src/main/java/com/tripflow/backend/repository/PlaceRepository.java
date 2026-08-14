package com.tripflow.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.tripflow.backend.domain.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
	Optional<Place> findByExternalPlaceId(String externalPlaceId);

	/** Batched form of {@link #findByExternalPlaceId}, used to resolve a whole trip's stops in one query. */
	List<Place> findByExternalPlaceIdIn(Collection<String> externalPlaceIds);

	/** Batched pre-filter for {@link #findFirstByNameAndLatitudeAndLongitudeOrderById} — coordinate matching still happens in-memory since it's not a simple IN. */
	List<Place> findByNameIn(Collection<String> names);

	/**
	 * Dedup fallback lookup by name+coordinates. {@code idx_places_name_lat_lng}
	 * (V3__create_places.sql) is deliberately non-unique — two places can legitimately
	 * share a name and near-coordinates — so this must never assume at most one row
	 * matches. {@code findFirst...OrderById} picks a single, deterministic result
	 * (the oldest matching row) instead of throwing IncorrectResultSizeDataAccessException
	 * when more than one row matches (SCRUM-216).
	 */
	Optional<Place> findFirstByNameAndLatitudeAndLongitudeOrderById(String name, Double latitude, Double longitude);

	/**
	 * Deletes every Place with no referencing stop. Backs {@code OrphanPlaceCleanupJob}
	 * (SCRUM-216) — no code path ever deleted a Place on its own, so rows accumulate
	 * unboundedly once their last referencing stop/trip is removed.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM Place p WHERE NOT EXISTS (SELECT 1 FROM Stop s WHERE s.place = p)")
	int deleteOrphans();

	/**
	 * Atomic, concurrency-safe insert: {@code ON CONFLICT DO NOTHING} on
	 * {@code idx_places_external_id} (V3__create_places.sql, unique only when
	 * {@code external_place_id IS NOT NULL}) means two concurrent inserts for the same
	 * external place race at the database, not in Java — same technique as
	 * {@code TripLikeRepository.insertIfAbsent} (SCRUM-306). Unlike a plain {@code save()}
	 * wrapped in a try/catch, Postgres never raises an error for {@code DO NOTHING}, so the
	 * caller's transaction is never poisoned and this participates normally in it — no
	 * separate transaction boundary needed, and no risk of committing a Place independently
	 * of whatever trip/stop creation the caller ultimately does (or rolls back).
	 *
	 * <p>When {@code externalPlaceId} is null, the partial index never applies, so this
	 * always inserts unconditionally, same as a plain save.
	 *
	 * <p>Returns the new row's id, or empty if the insert lost the race. Callers must
	 * re-read the existing row by {@code externalPlaceId} in that case.
	 */
	@Query(value = """
			INSERT INTO places (name, latitude, longitude, address, external_place_id)
			VALUES (:name, :latitude, :longitude, :address, :externalPlaceId)
			ON CONFLICT (external_place_id) WHERE external_place_id IS NOT NULL DO NOTHING
			RETURNING id
			""", nativeQuery = true)
	Optional<Long> insertIgnoringConflict(String name, double latitude, double longitude, String address,
			String externalPlaceId);
}
