package com.tripflow.backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripSummaryResponse;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /**
     * Single-trip read with stops and places fetched in one query.
     * Use for all read paths that map to TripResponse.
     *
     * <p>Explicit {@code JOIN FETCH} rather than {@code @EntityGraph} (SCRUM-226): with
     * {@code spring.jpa.open-in-view=false}, callers that read the returned {@code Trip}
     * after this method's transaction has already committed (e.g. {@code RouteOptimizationService},
     * {@code AiItineraryService} — see SCRUM-210) need {@code stops.place} to be genuinely
     * initialized, not just entity-graph-hinted; {@code DISTINCT} avoids duplicate {@code Trip}
     * rows from the {@code stops} join.
     */
    @Query("""
            SELECT DISTINCT t FROM Trip t
            LEFT JOIN FETCH t.stops s
            LEFT JOIN FETCH s.place
            WHERE t.id = :id
            """)
    Optional<Trip> findWithStopsById(@Param("id") Long id);

    /**
     * Card-projection list read for GET /api/trips. Deliberately NOT a fetch-joined
     * entity query — pairing Pageable with a collection fetch join (e.g. {@code stops})
     * makes Hibernate paginate in memory (HHH90003004), which defeats pagination entirely.
     * This is a flat, single-row-per-trip projection so paging happens in SQL.
     */
    @Query("""
            SELECT new com.tripflow.backend.dto.TripSummaryResponse(
                t.id, t.title, t.visibility, t.status, t.createdAt, t.updatedAt,
                (SELECT COUNT(s) FROM Stop s WHERE s.trip = t), null)
            FROM Trip t
            WHERE t.user.id = :userId
            """)
    Page<TripSummaryResponse> findSummariesByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Card-projection list read for GET /api/discovery/trips (SCRUM-160). Same flat
     * projection / no-fetch-join shape as {@link #findSummariesByUserId} and for the
     * same reason: a collection fetch join paired with Pageable forces Hibernate to
     * paginate in memory (HHH90003004).
     */
    @Query("""
            SELECT new com.tripflow.backend.dto.TripSummaryResponse(
                t.id, t.title, t.visibility, t.status, t.createdAt, t.updatedAt,
                (SELECT COUNT(s) FROM Stop s WHERE s.trip = t), null)
            FROM Trip t
            WHERE t.visibility = :visibility
            """)
    Page<TripSummaryResponse> findSummariesByVisibility(
            @Param("visibility") TripVisibility visibility, Pageable pageable);

    /**
     * Atomic counter bump (SCRUM-161) — {@code SET like_count = like_count + 1} at the
     * database, not a Java read-modify-write, so concurrent likes never lose an update.
     * Only call after {@link com.tripflow.backend.repository.TripLikeRepository#insertIfAbsent}
     * returns 1 (i.e. a like row was actually inserted).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Trip t SET t.likeCount = t.likeCount + 1 WHERE t.id = :tripId")
    int incrementLikeCount(@Param("tripId") Long tripId);

    /**
     * Atomic counter bump (SCRUM-161), symmetric with {@link #incrementLikeCount}.
     * {@code GREATEST(..., 0)} is defensive only — decrements are gated on
     * {@code TripLikeRepository.deleteByUserIdAndTripId} actually removing a row, so
     * like_count should never go negative in practice.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE trips SET like_count = GREATEST(like_count - 1, 0) WHERE id = :tripId",
            nativeQuery = true)
    int decrementLikeCount(@Param("tripId") Long tripId);
}
