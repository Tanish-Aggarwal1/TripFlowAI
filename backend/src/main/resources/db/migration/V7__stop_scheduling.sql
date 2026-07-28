-- SCRUM-244a: foundation for per-stop day/time scheduling. All columns are nullable
-- (or defaulted) so existing trips/stops remain valid without any backfill — a trip
-- that hasn't been (re-)optimized since this migration simply has no schedule yet.

ALTER TABLE trips
    ADD COLUMN start_date DATE;

ALTER TABLE stops
    ADD COLUMN day_number INT,
    ADD COLUMN planned_time TIME,
    ADD COLUMN stop_type VARCHAR(20) NOT NULL DEFAULT 'SIGHTSEEING';
