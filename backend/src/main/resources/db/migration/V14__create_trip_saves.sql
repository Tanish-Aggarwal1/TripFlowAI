-- SOCIAL-04: trip_saves join table (PK (user_id, trip_id) prevents double-saving),
-- mirroring V9__create_trip_likes.sql's shape. Unlike trip_likes, saves do NOT
-- denormalize a count column onto trips -- nothing in D-04/SOCIAL-04 displays a
-- save count, so there is no counter to keep in sync.

CREATE TABLE trip_saves (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id    BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, trip_id)
);

CREATE INDEX idx_trip_saves_trip_id ON trip_saves(trip_id);
