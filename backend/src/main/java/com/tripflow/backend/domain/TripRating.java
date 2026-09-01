package com.tripflow.backend.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
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

/** A single user's 1-5 star rating of a single trip (SOCIAL-07). Unlike {@link TripLike} and
 * {@link SavedTrip}, a rating is a value the user can change, not a toggle — re-rating
 * replaces the stored value rather than being ignored, so this entity carries both
 * {@code createdAt} and {@code updatedAt} rather than {@code createdAt} alone.
 *
 * <p>Mapping-only entity: {@link com.tripflow.backend.repository.TripRatingRepository} never
 * calls {@code save()} or reads a {@code TripRating} back — {@code rateTrip} is a
 * hand-written native {@code INSERT ... ON CONFLICT ... DO UPDATE}, chosen so re-rating is a
 * single atomic statement rather than a read-modify-write. This class exists so
 * {@code ddl-auto=validate} has a mapping for {@code trip_ratings} and so the repository can
 * extend {@code JpaRepository}; the constructor and {@code @MapsId} associations below are
 * otherwise unreachable code for the upsert path. */
@Entity
@Table(name = "trip_ratings")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TripRating {

    @EmbeddedId
    private TripRatingId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tripId")
    @JoinColumn(name = "trip_id")
    private Trip trip;

    // @JdbcTypeCode(SMALLINT) pins the JDBC type Hibernate validates against: V16
    // declares `rating SMALLINT`, and a plain Integer field otherwise maps to `integer`
    // under ddl-auto=validate (a columnDefinition string alone doesn't change the type
    // code the schema validator compares).
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer rating;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public TripRating(User user, Trip trip, Integer rating) {
        this.id = new TripRatingId(user.getId(), trip.getId());
        this.user = user;
        this.trip = trip;
        this.rating = rating;
    }
}
