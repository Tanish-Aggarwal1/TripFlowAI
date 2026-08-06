package com.tripflow.backend.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite PK (user_id, trip_id) for {@link TripLike} — prevents double-liking (SCRUM-161). */
@Embeddable
public class TripLikeId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "trip_id")
    private Long tripId;

    protected TripLikeId() {
    }

    public TripLikeId(Long userId, Long tripId) {
        this.userId = userId;
        this.tripId = tripId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTripId() {
        return tripId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TripLikeId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(tripId, that.tripId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tripId);
    }
}
