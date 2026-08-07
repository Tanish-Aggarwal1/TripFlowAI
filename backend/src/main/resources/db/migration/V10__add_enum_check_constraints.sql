-- DB-1: back the four @Enumerated(EnumType.STRING) columns with DB-level CHECK
-- constraints so an invalid value can never be written to these columns, even by a
-- future raw-SQL migration, manual data fix, or bulk-update bug. Value sets match the
-- Java enums exactly (Trip.java:54-60, Stop.java:44-46,59-61) as of this migration;
-- ddl-auto=validate does not check constraints, so this carries no boot-time risk.

ALTER TABLE trips
    ADD CONSTRAINT chk_trips_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'));

ALTER TABLE trips
    ADD CONSTRAINT chk_trips_status CHECK (status IN ('DRAFT', 'PLANNED', 'ACTIVE', 'COMPLETED'));

ALTER TABLE stops
    ADD CONSTRAINT chk_stops_status CHECK (status IN ('PLANNED', 'VISITED', 'SKIPPED'));

ALTER TABLE stops
    ADD CONSTRAINT chk_stops_stop_type CHECK (stop_type IN ('SIGHTSEEING', 'MEAL', 'LODGING', 'OTHER'));
