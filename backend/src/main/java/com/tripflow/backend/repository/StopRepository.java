package com.tripflow.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.Stop;

public interface StopRepository extends JpaRepository<Stop, Long> {

    /**
     * Loads a Stop with its parent Trip and the Trip's owning User fetched in the same
     * query, avoiding the 2-query lazy-load chain (Stop -> Trip -> User) that
     * StopPhotoService's ownership checks would otherwise pay on every call.
     */
    @Query("""
            SELECT s FROM Stop s
            JOIN FETCH s.trip t
            JOIN FETCH t.user
            WHERE s.id = :id
            """)
    Optional<Stop> findWithTripAndOwnerById(@Param("id") Long id);
}