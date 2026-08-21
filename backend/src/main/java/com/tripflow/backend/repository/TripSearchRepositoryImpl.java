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
import jakarta.persistence.TypedQuery;

/**
 * Backs {@link TripSearchRepository#searchPublicTrips}. The {@code ILIKE} + {@code unnest}
 * over {@code trips.tags} is Postgres-specific and {@code Trip.tags} is a plain array
 * column (not a JPA {@code @ElementCollection}), so JPQL has no collection-membership
 * syntax available for it here — this has to be a native query.
 *
 * <p>Rather than hand-map native result rows into {@link TripSummaryResponse}, this
 * resolves matching trip ids with one native query (page-limited, Postgres-only part),
 * then re-fetches them through the exact same flat JPQL projection every other trip-list
 * endpoint uses ({@code TripRepository#findSummariesByUserId}/{@code findSummariesByVisibility}) —
 * the row shape stays defined in exactly one place.
 */
public class TripSearchRepositoryImpl implements TripSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

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
                  AND (t.title ILIKE :pattern
                       OR EXISTS (SELECT 1 FROM unnest(t.tags) tag WHERE tag ILIKE :pattern))
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
                  AND (t.title ILIKE :pattern
                       OR EXISTS (SELECT 1 FROM unnest(t.tags) tag WHERE tag ILIKE :pattern))
                """)
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
     * request here, not a required parameter. Task 1 tracer scope: title matching only;
     * tags, place-names and {@code filters} land in task 2.
     */
    @Override
    public Page<TripOwnerSummaryResponse> searchOwnedTrips(
            Long userId, String query, TripSearchFilters filters, Pageable pageable) {
        List<Long> ids = matchingOwnedIds(userId, query, pageable);
        long total = countOwnedMatches(userId, query);

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
    private List<Long> matchingOwnedIds(Long userId, String pattern, Pageable pageable) {
        return entityManager.createNativeQuery("""
                SELECT t.id FROM trips t
                WHERE t.user_id = :userId
                  AND (:pattern IS NULL OR t.title ILIKE :pattern)
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("userId", userId)
                .setParameter("pattern", pattern)
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();
    }

    private long countOwnedMatches(Long userId, String pattern) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM trips t
                WHERE t.user_id = :userId
                  AND (:pattern IS NULL OR t.title ILIKE :pattern)
                """)
                .setParameter("userId", userId)
                .setParameter("pattern", pattern)
                .getSingleResult();
        return count.longValue();
    }
}
