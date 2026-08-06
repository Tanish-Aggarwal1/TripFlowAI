package com.tripflow.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}
