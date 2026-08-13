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

/** A single user's like on a single trip (SCRUM-161). Existence of a row is the source of
 * truth; {@code Trip.likeCount} is a denormalized count kept in sync alongside it.
 *
 * <p>Mapping-only entity: {@link com.tripflow.backend.repository.TripLikeRepository} never
 * calls {@code save()} or reads a {@code TripLike} back — both operations it exposes are
 * hand-written queries (a native {@code INSERT ... ON CONFLICT} and a JPQL bulk
 * {@code DELETE}) chosen so liking/unliking is a single atomic statement rather than a
 * read-modify-write. This class exists so {@code ddl-auto=validate} has a mapping for
 * {@code trip_likes} and so the repository can extend {@code JpaRepository}; the
 * constructor and {@code @MapsId} associations below are otherwise unreachable code. */
@Entity
@Table(name = "trip_likes")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TripLike {

    @EmbeddedId
    private TripLikeId id;

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

    public TripLike(User user, Trip trip) {
        this.id = new TripLikeId(user.getId(), trip.getId());
        this.user = user;
        this.trip = trip;
    }
}
