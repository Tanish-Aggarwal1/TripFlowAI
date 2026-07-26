package com.tripflow.backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.TripSummaryResponse;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /**
     * Single-trip read with stops and places fetched in one query.
     * Use for all read paths that map to TripResponse.
     */
    @EntityGraph(attributePaths = {"stops", "stops.place"})
    Optional<Trip> findWithStopsById(Long id);

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
}