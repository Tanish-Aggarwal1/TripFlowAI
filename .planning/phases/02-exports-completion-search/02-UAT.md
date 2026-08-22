---
status: diagnosed
phase: 02-exports-completion-search
source: [02-VERIFICATION.md]
started: 2026-08-21T22:15:00Z
updated: 2026-08-22T03:10:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Push branch and let CI run the Testcontainers `*IT` suite
expected: Push branch `docs/SCRUM-478-phase-2-planning-docs` (16 commits ahead of origin, unpushed) and let CI's `mvn -B verify -Pci` run the Testcontainers `*IT` suite: `TripExportControllerIT` (PDF 200/404), `TripRepositoryIT` (visited-count correlated subquery + `TripSummaryResponse` 8-component D-08 tripwire), `TripSearchRepositoryIT` (16 `searchOwnedTrips_*` methods — exactly-once matching, filter intersection, ordering stability, owner scoping), `TripControllerIT` (paged envelope + malformed-status 400). All `*IT` methods should pass against real Postgres, matching what the unit-level mocks and code inspection already indicate. Why human: no Docker daemon on this machine (`docker info` reaches the client but the server pipe `dockerDesktopLinuxEngine` is not running) — matches CLAUDE.md's documented team-wide constraint. `*IT` files compile clean under `-Pci` (confirmed) but have never actually executed against Postgres for this phase's code, and the branch has not been pushed, so CI has not run on it either.
result: PASS — first CI run (commit `1460e35`) caught a real pre-existing bug: `TripSearchRepositoryIT` failed 8/20 tests with "could not determine data type of parameter $2" on every blank-search `searchOwnedTrips` call. Root cause: `TEXT_MATCH_SQL`'s `pattern IS NULL` check was the one filter in the query left uncast (every other filter already used `CAST(? AS text/date/int) IS NULL`), so Postgres couldn't infer a type when the bound value was null. Fixed with `CAST(:pattern AS text) IS NULL` (commit `0a6c335`), no behavior change for a real search term. Re-run: `backend`, `frontend`, `CodeQL`, both `Analyze` jobs, and `check-title` all pass on PR #278 as of commit `0a6c335`.

### 2. Visually confirm the Mapbox map snapshot in an exported PDF
expected: Once `MAPBOX_TOKEN` is provisioned (Render + local `.env`), export the PDF of a trip that has been route-optimized and open it to confirm the embedded map snapshot is legible and matches the app's route line visually — a rendered, non-garbled map image with the correct route/pins. Why human: cannot provision a real Mapbox token or render/inspect a PDF image in this session; explicitly called out as outstanding in `02-02-SUMMARY.md`.
result: issue (fix applied, awaiting re-verification against a live optimized-route export)
reported: "it passes when i export a pdf with a unoptimized route but after i optimize a route it does not show the map"
severity: major

## Summary

total: 2
passed: 1
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-02-2
  truth: "A rendered, non-garbled map image with the correct route/pins."
  status: fix_applied
  reason: "User reported: it passes when i export a pdf with a unoptimized route but after i optimize a route it does not show the map"
  severity: major
  test: 2
  root_cause: "Mapbox's `auto` position/zoom (used on every request) computes its bounding box from the overlay's `features`. Trip.routeGeometry is stored as a bare GeoJSON Geometry (no Feature wrapper, deliberately per D-04), so geojsonOverlay sent Mapbox a payload with no `features` key — Mapbox rejected it with a 422 'Invalid GeoJSON'. Confirmed via the user's production log line and Mapbox's own docs (one documented 422 case is exactly 'Auto extent cannot be determined when GeoJSON has no features'; their own bare-Geometry example pairs it only with an explicit center/zoom, never `auto`). Marker overlays also use `auto` but succeed because Mapbox computes marker auto-extent through a different, non-GeoJSON code path."
  artifacts:
    - path: "backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClient.java"
      issue: "geojsonOverlay encoded the bare Geometry directly instead of wrapping it in a Feature"
  missing:
    - "Wrap routeGeometryJson in {\"type\":\"Feature\",\"properties\":{},\"geometry\":<geometry>} before encoding"
  debug_session: ".planning/debug/mapbox-snapshot-missing-on-optimized-route.md"
  fix_commit: "(next commit on docs/SCRUM-478-phase-2-planning-docs)"
  fix_verified_live: false
