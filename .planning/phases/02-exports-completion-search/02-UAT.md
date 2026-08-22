---
status: complete
phase: 02-exports-completion-search
source: [02-VERIFICATION.md]
started: 2026-08-21T22:15:00Z
updated: 2026-08-22T04:15:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Push branch and let CI run the Testcontainers `*IT` suite
expected: Push branch `docs/SCRUM-478-phase-2-planning-docs` (16 commits ahead of origin, unpushed) and let CI's `mvn -B verify -Pci` run the Testcontainers `*IT` suite: `TripExportControllerIT` (PDF 200/404), `TripRepositoryIT` (visited-count correlated subquery + `TripSummaryResponse` 8-component D-08 tripwire), `TripSearchRepositoryIT` (16 `searchOwnedTrips_*` methods — exactly-once matching, filter intersection, ordering stability, owner scoping), `TripControllerIT` (paged envelope + malformed-status 400). All `*IT` methods should pass against real Postgres, matching what the unit-level mocks and code inspection already indicate. Why human: no Docker daemon on this machine (`docker info` reaches the client but the server pipe `dockerDesktopLinuxEngine` is not running) — matches CLAUDE.md's documented team-wide constraint. `*IT` files compile clean under `-Pci` (confirmed) but have never actually executed against Postgres for this phase's code, and the branch has not been pushed, so CI has not run on it either.
result: PASS — first CI run (commit `1460e35`) caught a real pre-existing bug: `TripSearchRepositoryIT` failed 8/20 tests with "could not determine data type of parameter $2" on every blank-search `searchOwnedTrips` call. Root cause: `TEXT_MATCH_SQL`'s `pattern IS NULL` check was the one filter in the query left uncast (every other filter already used `CAST(? AS text/date/int) IS NULL`), so Postgres couldn't infer a type when the bound value was null. Fixed with `CAST(:pattern AS text) IS NULL` (commit `0a6c335`), no behavior change for a real search term. Re-run: `backend`, `frontend`, `CodeQL`, both `Analyze` jobs, and `check-title` all pass on PR #278 as of commit `0a6c335`.

### 2. Visually confirm the Mapbox map snapshot in an exported PDF
expected: Once `MAPBOX_TOKEN` is provisioned (Render + local `.env`), export the PDF of a trip that has been route-optimized and open it to confirm the embedded map snapshot is legible and matches the app's route line visually — a rendered, non-garbled map image with the correct route/pins. Why human: cannot provision a real Mapbox token or render/inspect a PDF image in this session; explicitly called out as outstanding in `02-02-SUMMARY.md`.
result: pass — confirmed live by user after 3 rounds of fixes: #279 (wrap bare Geometry in a
  Feature so Mapbox's `auto` extent can read it), #280 (fix URLEncoder's space-to-'+' corruption
  against real, non-compact stored geometry — the actual root cause of the persisting 422), #281
  (combine the route line with stop markers in one overlay per follow-up feedback, D-04 revised).
  User confirmed: "passed".
reported: "it passes when i export a pdf with a unoptimized route but after i optimize a route it does not show the map"
severity: major

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-02-2
  truth: "A rendered, non-garbled map image with the correct route/pins."
  status: resolved
  reason: "User reported: it passes when i export a pdf with a unoptimized route but after i optimize a route it does not show the map"
  severity: major
  test: 2
  root_cause: "Two compounding bugs, found in sequence against real production data. (1) Mapbox's `auto` position/zoom (used on every request) computes its bounding box from the overlay's `features`; Trip.routeGeometry is a bare GeoJSON Geometry (no Feature wrapper, deliberately per original D-04), so Mapbox rejected it with a 422 'Invalid GeoJSON' — fixed in #279 by wrapping it in a Feature. (2) That alone didn't fix it: the actual stored routeGeometry is NOT compact JSON (Jackson's default writer inserts a space after every ':' and ','), and URLEncoder.encode is application/x-www-form-urlencoded, which turns a space into a literal '+' rather than '%20' — Mapbox percent-decodes but never form-decodes, so the stray '+' corrupted the JSON syntax, still producing the same 422. Fixed in #280 by re-encoding '+' to '%20'. A third, unrelated follow-up (#281) then combined the route line with stop markers per live user feedback that route-only looked wrong (D-04 revised)."
  artifacts:
    - path: "backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClient.java"
      issue: "geojsonOverlay: bare Geometry not wrapped in a Feature, and URLEncoder's space-to-'+' output was never corrected to '%20'"
  missing: []
  debug_session: ".planning/debug/mapbox-snapshot-missing-on-optimized-route.md"
  fix_commit: "#279, #280, #281 (all merged to main)"
  fix_verified_live: true
