package com.tripflow.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripSearchFilters;
import com.tripflow.backend.dto.TripSummaryResponse;

/**
 * SCRUM-163: case-insensitive substring search over PUBLIC trip titles and tags
 * (Postgres {@code ILIKE}). Deliberately MVP-only — plain substring matching, no
 * ranking, no stemming. A full-text upgrade (tsvector column + GIN index, ts_rank
 * ordering) is explicitly out of scope for this ticket and is the natural next step
 * if search relevance/performance becomes a problem at scale.
 */
public interface TripSearchRepository {
    Page<TripSummaryResponse> searchPublicTrips(String query, Pageable pageable);

    /**
     * SEARCH-01/D-09: owner-only sibling of {@link #searchPublicTrips} — scoped to
     * {@code userId} rather than {@code visibility='PUBLIC'}, and additionally matches
     * stop/place names (D-10) plus the {@link TripSearchFilters} dimensions (D-12).
     * Unlike {@code searchPublicTrips}, {@code query} arrives here already normalized
     * into its final ILIKE pattern (e.g. {@code "%paris%"}) or {@code null} — meaning
     * "no text predicate" (the owner's whole list, filters still apply) — by the
     * service layer, since a blank search is a valid request here rather than a 400.
     */
    Page<TripOwnerSummaryResponse> searchOwnedTrips(
            Long userId, String query, TripSearchFilters filters, Pageable pageable);
}
