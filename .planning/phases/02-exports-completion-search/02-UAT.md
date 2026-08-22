---
status: testing
phase: 02-exports-completion-search
source: [02-VERIFICATION.md]
started: 2026-08-21T22:15:00Z
updated: 2026-08-22T02:35:00Z
---

## Current Test

number: 2
name: Visually confirm the Mapbox map snapshot in an exported PDF
expected: |
  A rendered, non-garbled map image with the correct route/pins.
awaiting: user response

## Tests

### 1. Push branch and let CI run the Testcontainers `*IT` suite
expected: Push branch `docs/SCRUM-478-phase-2-planning-docs` (16 commits ahead of origin, unpushed) and let CI's `mvn -B verify -Pci` run the Testcontainers `*IT` suite: `TripExportControllerIT` (PDF 200/404), `TripRepositoryIT` (visited-count correlated subquery + `TripSummaryResponse` 8-component D-08 tripwire), `TripSearchRepositoryIT` (16 `searchOwnedTrips_*` methods — exactly-once matching, filter intersection, ordering stability, owner scoping), `TripControllerIT` (paged envelope + malformed-status 400). All `*IT` methods should pass against real Postgres, matching what the unit-level mocks and code inspection already indicate. Why human: no Docker daemon on this machine (`docker info` reaches the client but the server pipe `dockerDesktopLinuxEngine` is not running) — matches CLAUDE.md's documented team-wide constraint. `*IT` files compile clean under `-Pci` (confirmed) but have never actually executed against Postgres for this phase's code, and the branch has not been pushed, so CI has not run on it either.
result: PASS — first CI run (commit `1460e35`) caught a real pre-existing bug: `TripSearchRepositoryIT` failed 8/20 tests with "could not determine data type of parameter $2" on every blank-search `searchOwnedTrips` call. Root cause: `TEXT_MATCH_SQL`'s `pattern IS NULL` check was the one filter in the query left uncast (every other filter already used `CAST(? AS text/date/int) IS NULL`), so Postgres couldn't infer a type when the bound value was null. Fixed with `CAST(:pattern AS text) IS NULL` (commit `0a6c335`), no behavior change for a real search term. Re-run: `backend`, `frontend`, `CodeQL`, both `Analyze` jobs, and `check-title` all pass on PR #278 as of commit `0a6c335`.

### 2. Visually confirm the Mapbox map snapshot in an exported PDF
expected: Once `MAPBOX_TOKEN` is provisioned (Render + local `.env`), export the PDF of a trip that has been route-optimized and open it to confirm the embedded map snapshot is legible and matches the app's route line visually — a rendered, non-garbled map image with the correct route/pins. Why human: cannot provision a real Mapbox token or render/inspect a PDF image in this session; explicitly called out as outstanding in `02-02-SUMMARY.md`.
result: [pending]

## Summary

total: 2
passed: 1
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
