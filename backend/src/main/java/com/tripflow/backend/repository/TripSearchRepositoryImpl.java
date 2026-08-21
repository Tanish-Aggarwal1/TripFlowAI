package com.tripflow.backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripSearchFilters;
import com.tripflow.backend.dto.TripSummaryResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

/**
 * Backs {@link TripSearchRepository#searchPublicTrips} and {@link TripSearchRepository#searchOwnedTrips}.
 * The {@code ILIKE} + {@code unnest} over {@code trips.tags}, and the {@code stops}/{@code places}
 * join for place-name matching (D-10), are Postgres-specific — {@code Trip.tags} is a plain array
 * column (not a JPA {@code @ElementCollection}), so JPQL has no collection-membership syntax
 * available for it here — this has to be a native query.
 *
 * <p>Rather than hand-map native result rows into a summary DTO, both methods resolve matching
 * trip ids with one native query (page-limited, Postgres-only part), then re-fetch them through
 * the exact same flat JPQL projection every other trip-list endpoint uses — the row shape stays
 * defined in exactly one place.
 */
public class TripSearchRepositoryImpl implements TripSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * D-11: shared text-match fragment used by BOTH {@link #searchPublicTrips} and
     * {@link #searchOwnedTrips} — the two queries differ only in scope (visibility='PUBLIC'
     * vs user_id=:userId) plus the owner query's filter chain. Matches title, tags, and
     * stop/place names (D-10). The place-name arm is an EXISTS subquery, not a top-level
     * join: a top-level join would emit one row per matching stop, so a trip with three
     * matching stops would appear three times and inflate the count — the same reason the
     * tags arm already uses EXISTS over unnest.
     */
    private static final String TEXT_MATCH_SQL = """
            (:pattern IS NULL OR t.title ILIKE :pattern
             OR EXISTS (SELECT 1 FROM unnest(t.tags) tag WHERE tag ILIKE :pattern)
             OR EXISTS (
                 SELECT 1 FROM stops s JOIN places p ON p.id = s.place_id
                 WHERE s.trip_id = t.id AND p.name ILIKE :pattern))""";

    @Override
    public Page<TripSummaryResponse> searchPublicTrips(String query, Pageable pageable) {
        String pattern = "%" + query + "%";

        List<Long> ids = matchingIds(pattern, pageable);
        long total = countMatches(pattern);

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        TypedQuery<TripSummaryResponse> summaryQuery = entityManager.createQuery("""
                SELECT new com.tripflow.backend.dto.TripSummaryResponse(
                    t.id, t.title, t.visibility, t.status, t.createdAt, t.updatedAt,
                    (SELECT COUNT(s) FROM Stop s WHERE s.trip = t), null)
                FROM Trip t
                WHERE t.id IN :ids
                ORDER BY t.createdAt DESC, t.id DESC
                """, TripSummaryResponse.class);
        summaryQuery.setParameter("ids", ids);

        return new PageImpl<>(summaryQuery.getResultList(), pageable, total);
    }

    @SuppressWarnings("unchecked")
    private List<Long> matchingIds(String pattern, Pageable pageable) {
        return entityManager.createNativeQuery("""
                SELECT t.id FROM trips t
                WHERE t.visibility = 'PUBLIC'
                  AND """ + TEXT_MATCH_SQL + """

                ORDER BY t.created_at DESC, t.id DESC
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("pattern", pattern)
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();
    }

    private long countMatches(String pattern) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM trips t
                WHERE t.visibility = 'PUBLIC'
                  AND """ + TEXT_MATCH_SQL)
                .setParameter("pattern", pattern)
                .getSingleResult();
        return count.longValue();
    }

    /**
     * SEARCH-01/D-09: owner-scoped sibling of {@link #searchPublicTrips}. {@code userId}
     * sits in the WHERE clause of both the id query and the count query, sourced from
     * {@code principal.userId()} by the caller — never from request input — so another
     * user's trip can neither be returned nor counted (T-02-10). Unlike
     * {@link #searchPublicTrips}, {@code query} arrives here already normalized into its
     * final ILIKE pattern (or {@code null} for "no text predicate") by
     * {@code TripService#searchOwnedTrips} — a blank search is a valid "list everything"
     * request here, not a required parameter. {@code filters} (D-12) all AND together and
     * are each null-tolerant, so an unset filter is a no-op.
     */
    @Override
    public Page<TripOwnerSummaryResponse> searchOwnedTrips(
            Long userId, String query, TripSearchFilters filters, Pageable pageable) {
        List<Long> ids = matchingOwnedIds(userId, query, filters, pageable);
        long total = countOwnedMatches(userId, query, filters);

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        TypedQuery<TripOwnerSummaryResponse> summaryQuery = entityManager.createQuery("""
                SELECT new com.tripflow.backend.dto.TripOwnerSummaryResponse(
                    t.id, t.title, t.visibility, t.status, t.createdAt, t.updatedAt,
                    (SELECT COUNT(s) FROM Stop s WHERE s.trip = t), null,
                    (SELECT COUNT(s) FROM Stop s WHERE s.trip = t AND s.status = com.tripflow.backend.domain.enums.StopStatus.VISITED))
                FROM Trip t
                WHERE t.id IN :ids
                ORDER BY t.createdAt DESC, t.id DESC
                """, TripOwnerSummaryResponse.class);
        summaryQuery.setParameter("ids", ids);

        return new PageImpl<>(summaryQuery.getResultList(), pageable, total);
    }

    @SuppressWarnings("unchecked")
    private List<Long> matchingOwnedIds(
            Long userId, String pattern, TripSearchFilters filters, Pageable pageable) {
        Query nativeQuery = entityManager.createNativeQuery("""
                SELECT t.id FROM trips t
                WHERE t.user_id = :userId
                  AND """ + TEXT_MATCH_SQL + """

                  AND (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text))
                  AND (CAST(:visibility AS text) IS NULL OR t.visibility = CAST(:visibility AS text))
                  AND (CAST(:startDateFrom AS date) IS NULL OR t.start_date >= CAST(:startDateFrom AS date))
                  AND (CAST(:startDateTo AS date) IS NULL OR t.start_date <= CAST(:startDateTo AS date))
                  AND (CAST(:durationDays AS int) IS NULL OR (
                      SELECT COALESCE(MAX(s.day_number), 0) FROM stops s WHERE s.trip_id = t.id
                  ) = CAST(:durationDays AS int))
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT :limit OFFSET :offset
                """);
        bindOwnedParameters(nativeQuery, userId, pattern, filters);
        return nativeQuery
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();
    }

    private long countOwnedMatches(Long userId, String pattern, TripSearchFilters filters) {
        Query nativeQuery = entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM trips t
                WHERE t.user_id = :userId
                  AND """ + TEXT_MATCH_SQL + """

                  AND (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text))
                  AND (CAST(:visibility AS text) IS NULL OR t.visibility = CAST(:visibility AS text))
                  AND (CAST(:startDateFrom AS date) IS NULL OR t.start_date >= CAST(:startDateFrom AS date))
                  AND (CAST(:startDateTo AS date) IS NULL OR t.start_date <= CAST(:startDateTo AS date))
                  AND (CAST(:durationDays AS int) IS NULL OR (
                      SELECT COALESCE(MAX(s.day_number), 0) FROM stops s WHERE s.trip_id = t.id
                  ) = CAST(:durationDays AS int))
                """);
        bindOwnedParameters(nativeQuery, userId, pattern, filters);
        Number count = (Number) nativeQuery.getSingleResult();
        return count.longValue();
    }

    /**
     * D-13/D-14: date range filters bind on {@code start_date} (when the trip happens),
     * not {@code created_at}. Duration binds on {@code MAX(day_number)} across the trip's
     * stops rather than a stored column, {@code COALESCE}'d to 0 (D-07 convention) so a
     * zero-stop/never-optimized trip reports duration 0 instead of silently vanishing from
     * a duration-filtered list. Enums bind as their {@code name()} strings so the native
     * comparison matches the {@code varchar} columns; every filter is explicitly
     * {@code CAST} so an untyped null bind never fails Postgres type inference — one
     * static query text, never branched on which filters are present, is what keeps this
     * injection-proof.
     */
    private void bindOwnedParameters(Query nativeQuery, Long userId, String pattern, TripSearchFilters filters) {
        nativeQuery
                .setParameter("userId", userId)
                .setParameter("pattern", pattern)
                .setParameter("status", filters.status() == null ? null : filters.status().name())
                .setParameter("visibility", filters.visibility() == null ? null : filters.visibility().name())
                .setParameter("startDateFrom", filters.startDateFrom())
                .setParameter("startDateTo", filters.startDateTo())
                .setParameter("durationDays", filters.durationDays());
    }
}
