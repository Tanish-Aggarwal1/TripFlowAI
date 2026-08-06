-- SCRUM-161: trip_likes join table (PK (user_id, trip_id) prevents double-like) plus a
-- denormalized like_count on trips so the discovery feed can sort/display like counts
-- without a COUNT(*) subquery per row. like_count is maintained via atomic
-- UPDATE ... SET like_count = like_count + 1 / - 1 alongside each insert/delete of this
-- table, inside the same transaction — never a Java read-modify-write.

CREATE TABLE trip_likes (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id    BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, trip_id)
);

CREATE INDEX idx_trip_likes_trip_id ON trip_likes(trip_id);

ALTER TABLE trips
    ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;
