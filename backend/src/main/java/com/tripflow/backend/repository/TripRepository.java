package com.tripflow.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.backend.domain.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByUserId(Long userId);
    List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Single-trip read with stops and places fetched in one query.
     * Use for all read paths that map to TripResponse.
     */
    @EntityGraph(attributePaths = {"stops", "stops.place"})
    Optional<Trip> findWithStopsById(Long id);
}