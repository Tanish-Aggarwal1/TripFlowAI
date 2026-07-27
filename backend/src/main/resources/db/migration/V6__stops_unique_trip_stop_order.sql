-- SCRUM-218 / AUDIT-09: stop_order is the core domain invariant of a multi-stop trip and
-- currently has no database backstop — three separate app-code paths maintain it
-- (TripService/StopService.buildStops, StopService.renumber, RouteOptimizationService.
-- reorderStops) and any bug in any of them silently produces duplicate or gapped values,
-- with nothing failing and nothing logging.

-- Repair step first: if any environment already holds duplicate (trip_id, stop_order)
-- pairs, renumber every trip's stops sequentially (0..N-1), preserving relative order
-- (by existing stop_order, then id as a tiebreaker), so the constraint below applies
-- cleanly regardless of pre-existing data. A no-op on a database with no duplicates.
WITH renumbered AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY trip_id ORDER BY stop_order, id) - 1 AS new_order
    FROM stops
)
UPDATE stops s
SET stop_order = renumbered.new_order
FROM renumbered
WHERE s.id = renumbered.id
  AND s.stop_order <> renumbered.new_order;

-- DEFERRABLE INITIALLY DEFERRED: all three renumbering paths above reassign indices
-- within a single transaction and can transiently collide (e.g. shifting 2->1 while a
-- stop still holds 1) — checking at COMMIT instead of after each individual UPDATE lets
-- correct renumbering complete without spurious violations, while a genuine duplicate
-- (e.g. two concurrent addStop calls computing the same next order) still fails loudly
-- at commit instead of corrupting the data silently.
ALTER TABLE stops
    ADD CONSTRAINT uq_stops_trip_id_stop_order UNIQUE (trip_id, stop_order)
    DEFERRABLE INITIALLY DEFERRED;
