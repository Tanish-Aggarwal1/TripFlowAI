package com.tripflow.backend.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A single user's save/bookmark of a single trip (SOCIAL-04). Existence of a row is the
 * source of truth; unlike {@link TripLike}, no count is denormalized onto {@code trips} —
 * nothing in D-04/SOCIAL-04 displays a save count.
 *
 * <p>Mapping-only entity: {@link com.tripflow.backend.repository.SavedTripRepository} never
 * calls {@code save()} or reads a {@code SavedTrip} back for the toggle operations — both
 * saveTrip/unsaveTrip are hand-written queries (a native {@code INSERT ... ON CONFLICT} and
 * a JPQL bulk {@code DELETE}) chosen so saving/unsaving is a single atomic statement rather
 * than a read-modify-write. This class exists so {@code ddl-auto=validate} has a mapping for
 * {@code trip_saves} and so the repository can extend {@code JpaRepository}; the constructor
 * and {@code @MapsId} associations below are otherwise unreachable code for the toggle path
 * (the list-query projection reads through {@code trip}/{@code user}, not this constructor). */
@Entity
@Table(name = "trip_saves")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SavedTrip {

    @EmbeddedId
    private SavedTripId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tripId")
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public SavedTrip(User user, Trip trip) {
        this.id = new SavedTripId(user.getId(), trip.getId());
        this.user = user;
        this.trip = trip;
    }
}
