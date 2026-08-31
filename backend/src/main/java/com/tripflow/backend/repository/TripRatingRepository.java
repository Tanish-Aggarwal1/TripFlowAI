package com.tripflow.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripflow.backend.domain.TripRating;
import com.tripflow.backend.domain.TripRatingId;

public interface TripRatingRepository extends JpaRepository<TripRating, TripRatingId> {

    /**
     * Upsert a rating: {@code ON CONFLICT (user_id, trip_id) DO UPDATE} means re-rating the
     * same trip replaces the existing row's value rather than being ignored (unlike
     * {@code TripLikeRepository}/{@code SavedTripRepository}'s {@code DO NOTHING} toggle) or
     * accumulating a second row that would skew the average (SOCIAL-07).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO trip_ratings (user_id, trip_id, rating, created_at, updated_at)
            VALUES (:userId, :tripId, :rating, NOW(), NOW())
            ON CONFLICT (user_id, trip_id) DO UPDATE SET rating = excluded.rating, updated_at = NOW()
            """, nativeQuery = true)
    void upsertRating(@Param("userId") Long userId, @Param("tripId") Long tripId, @Param("rating") int rating);

    /**
     * Average and count of every rating on a trip. Spring Data returns an array-typed method
     * as a one-row wrapper, so this comes back as a single-element {@code Object[]} whose
     * only element is the actual {@code [Double average, Long count]} row — callers must
     * unwrap once (see {@code TripRatingService.getSummary}). {@code AVG} over zero rows
     * returns SQL NULL, not zero — {@code TripRatingService} maps that NULL through to
     * {@code TripRatingSummaryResponse.averageRating} honestly rather than as a misleading
     * 0.0. Plain {@code Object[]} rather than a constructor-expression DTO: a two-column
     * aggregate with no other consumer doesn't earn a dedicated projection class.
     */
    @Query("SELECT AVG(r.rating), COUNT(r) FROM TripRating r WHERE r.id.tripId = :tripId")
    Object[] findAverageAndCountByTripId(@Param("tripId") Long tripId);

    /** The caller's own stored rating for a trip, or empty if they haven't rated it. */
    @Query("SELECT r.rating FROM TripRating r WHERE r.id.userId = :userId AND r.id.tripId = :tripId")
    Optional<Integer> findRatingByUserIdAndTripId(@Param("userId") Long userId, @Param("tripId") Long tripId);
}
