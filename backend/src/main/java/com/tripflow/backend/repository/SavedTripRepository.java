package com.tripflow.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.SavedTrip;
import com.tripflow.backend.domain.SavedTripId;
import com.tripflow.backend.dto.TripOwnerSummaryResponse;

public interface SavedTripRepository extends JpaRepository<SavedTrip, SavedTripId> {

    /**
     * Idempotent, concurrency-safe save: {@code ON CONFLICT DO NOTHING} on the
     * {@code (user_id, trip_id)} PK means two concurrent saves from the same user race at
     * the database, not in Java — no need for a separate existsById check first (SOCIAL-04).
     * Returns 1 if a row was actually inserted, 0 if the trip was already saved.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO trip_saves (user_id, trip_id, created_at)
            VALUES (:userId, :tripId, NOW())
            ON CONFLICT (user_id, trip_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("tripId") Long tripId);

    /**
     * Idempotent unsave. Returns 1 if a row was actually deleted, 0 if the trip wasn't saved.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SavedTrip st WHERE st.id.userId = :userId AND st.id.tripId = :tripId")
    int deleteByUserIdAndTripId(@Param("userId") Long userId, @Param("tripId") Long tripId);

    /**
     * Owner-scoped paged list read for {@code GET /api/trips/saved} (SOCIAL-04). Same flat
     * projection / no-fetch-join shape as {@code TripRepository.findSummariesByUserId} and
     * for the same reason: a collection fetch join paired with Pageable forces Hibernate to
     * paginate in memory (HHH90003004). {@code userId} always comes from the authenticated
     * principal — never a request-supplied parameter — so one caller's saves can never leak
     * into another caller's list.
     */
    @Query("""
            SELECT new com.tripflow.backend.dto.TripOwnerSummaryResponse(
                t.id, t.title, t.visibility, t.status, t.createdAt, t.updatedAt,
                (SELECT COUNT(s) FROM Stop s WHERE s.trip = t), null,
                (SELECT COUNT(s) FROM Stop s WHERE s.trip = t AND s.status = com.tripflow.backend.domain.enums.StopStatus.VISITED))
            FROM SavedTrip st
            JOIN st.trip t
            WHERE st.id.userId = :userId
            """)
    Page<TripOwnerSummaryResponse> findSavedTripsByUserId(@Param("userId") Long userId, Pageable pageable);
}
