-- SOCIAL-05 / D-07: stored profile interests, free-text TEXT[] mirroring Trip.tags's
-- shape exactly (no fixed taxonomy — see 06-05-PLAN.md Task 1 decision). NOT NULL with an
-- empty-array default backfills existing rows in the same statement and means User never
-- has to defend against a null interests column.

ALTER TABLE users ADD COLUMN interests TEXT[] NOT NULL DEFAULT '{}';
