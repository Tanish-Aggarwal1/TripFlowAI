package com.tripflow.backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
}
