-- One-time cleanup of orphaned places (SCRUM-216 / AUDIT-07): rows with no referencing
-- stop, accumulated because no code path ever deletes a Place directly and V4 wires no
-- ON DELETE cleanup action. Going forward, OrphanPlaceCleanupJob deletes the same
-- condition on a daily schedule, so this migration only needs to catch up pre-existing
-- data — no schema change, V3/V4 untouched.
DELETE FROM places
WHERE id NOT IN (SELECT DISTINCT place_id FROM stops);
