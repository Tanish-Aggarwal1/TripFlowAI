-- SOCIAL-07: trip_ratings join table. Unlike trip_likes/trip_saves (a toggle, insert-if-
-- absent), a rating is a value a user can change, so the composite PK on (user_id, trip_id)
-- backs an upsert (INSERT ... ON CONFLICT DO UPDATE) rather than do-nothing. The CHECK
-- constraint is defense in depth behind RateTripRequest's Bean Validation bounds (project
-- convention per V10__add_enum_check_constraints.sql) — no denormalized average column on
-- trips; the aggregate is computed on read in TripRatingRepository.

CREATE TABLE trip_ratings (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id    BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, trip_id)
);

CREATE INDEX idx_trip_ratings_trip_id ON trip_ratings(trip_id);
