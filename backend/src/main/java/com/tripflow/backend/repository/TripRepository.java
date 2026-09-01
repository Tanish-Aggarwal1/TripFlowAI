package com.tripflow.backend.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripSummaryResponse;

public interface TripRepository extends JpaRepository<Trip, Long>, TripSearchRepository {

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
     *
     * <p>Owner-only: targets {@link TripOwnerSummaryResponse}, which carries a
     * {@code visitedStopCount} that {@link TripSummaryResponse} does not (D-08). The
     * public-feed queries below stay on {@code TripSummaryResponse} — this is the fork
     * that keeps a user's completion progress out of the discovery feed.
     */
    @Query("""
            SELECT new com.tripflow.backend.dto.TripOwnerSummaryResponse(
                t.id, t.title, t.visibility, t.status, t.createdAt, t.updatedAt,
                (SELECT COUNT(s) FROM Stop s WHERE s.trip = t), null,
                (SELECT COUNT(s) FROM Stop s WHERE s.trip = t AND s.status = com.tripflow.backend.domain.enums.StopStatus.VISITED))
            FROM Trip t
            WHERE t.user.id = :userId
            """)
    Page<TripOwnerSummaryResponse> findSummariesByUserId(@Param("userId") Long userId, Pageable pageable);

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
     * Full-entity page read backing {@code GET /api/discovery/feed} (SOCIAL-01). Unlike
     * {@link #findSummariesByVisibility}, this returns the actual {@link Trip} entities
     * (with stops, per {@code Trip.stops}' {@code @OrderBy("stopOrder ASC")}) so
     * {@code TripService.listFeed} can build the full-card {@code FeedTripResponse} shape —
     * owner username, tags, and per-stop text/photos — that the summary projection doesn't
     * carry. No interest-overlap {@code ORDER BY} here; plan 06-06 owns ranking.
     */
    Page<Trip> findByVisibility(TripVisibility visibility, Pageable pageable);

    /**
     * Ranked feed query (SOCIAL-06, D-05/D-06) — PUBLIC trips whose {@code tags} overlap the
     * viewer's stored {@code User.interests} come first, {@code created_at} descending second.
     * {@code TripService.listFeed} only calls this when {@code interests} is non-empty; an empty
     * {@code IN (...)} list is a SQL syntax error, not a query that matches nothing, so the empty
     * case branches to {@link #findByVisibility} instead.
     *
     * <p>Deliberate deviation from 06-RESEARCH.md Pattern 4's Postgres array-overlap ({@code &&})
     * sketch: {@code &&} requires binding a {@code text[]} parameter, which in practice means
     * assembling a Postgres array literal string out of free-text, user-supplied interest values —
     * a value containing a comma, brace or double quote silently corrupts that literal. This
     * {@code unnest} + {@code IN} form instead binds an ordinary collection parameter that Spring
     * Data expands into regular bound placeholders, so no literal is ever constructed and no
     * escaping is required. The semantics are identical: both answer "do these two sets share at
     * least one element."
     *
     * <p>The {@code created_at DESC} tiebreaker is not cosmetic — without a total ordering,
     * Postgres may return rows in a different order at offset 20 than at offset 0, which is
     * exactly how a paginated feed starts duplicating and skipping trips across pages.
     */
    @Query(value = """
            SELECT t.* FROM trips t
            WHERE t.visibility = 'PUBLIC'
            ORDER BY
                EXISTS (SELECT 1 FROM unnest(t.tags) AS tag WHERE tag IN (:interests)) DESC,
                t.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM trips t
            WHERE t.visibility = 'PUBLIC'
            """,
            nativeQuery = true)
    Page<Trip> findPublicRankedByInterests(@Param("interests") Collection<String> interests, Pageable pageable);

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
