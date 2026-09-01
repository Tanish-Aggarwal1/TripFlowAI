# Deferred Items — Phase 06 (Community & Social)

Items discovered during plan execution that are out of scope for the discovering
plan (pre-existing, unrelated to the files that plan touched). Logged per the
executor's scope-boundary rule rather than fixed inline.

## From plan 06-04 (trip ratings)

**`npm run test:ci` fails its global function-coverage floor (90%), unrelated to 06-04's changes.**

- Measured after 06-04's three commits: statements 94.2%, branches 87.34%,
  **functions 89.22%** (floor 90%), lines 95.56%. All 455 individual specs pass —
  the failure is `karma-coverage`'s `check.global.functions` gate in
  `frontend/karma.conf.js`, not a test failure.
- Root cause is pre-existing and outside 06-04's file set:
  - `frontend/src/app/app.routes.ts` — 0/18 functions covered (route guard
    factory functions never exercised by a route-config test). Last touched by
    06-05 (`d7c2225`), not 06-04.
  - `frontend/src/app/core/services/stop-photo.service.ts` — 14/20 (70%). Last
    touched by SCRUM-164, well before this phase.
  - `frontend/src/app/pages/trips/dashboard/dashboard.page.ts` — 29/33 (87.87%).
  - `frontend/src/app/pages/trips/trip-edit/trip-edit.page.ts` — 16/19 (84.21%).
  - `frontend/src/app/pages/trips/trip-view/trip-view.page.ts` — 38/42 (90.47%).
  - `frontend/src/testing/a11y.ts` — 6/8 (75%).
  - `frontend/src/app/core/services/trip.service.ts` has 6 pre-existing
    uncovered `catchError` closures (createTrip, updateTrip, deleteTrip,
    optimizeTrip, exportIcs, exportPdf — none touched by 06-04); 06-04's own
    additions to this file (`rateTrip`, `getTripRating`) are 100% covered by
    their own new tests.
  - `frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.ts`
    is 19/19 (100%) after 06-04's coverage test for the rating-summary-fetch
    error path — 06-04 leaves this file fully covered, not partially.
- The floor was set 2026-07-28 (SCRUM-247/206/214) at a measured 93.95%
  functions baseline; current shortfall predates 06-04 and most likely
  accumulated across 06-02/06-05's route and page work.
- **Recommended fix (not 06-04's job):** add route-guard invocation coverage
  for `app.routes.ts`, and close the remaining gaps in `stop-photo.service.ts`
  and the three page components above. File as a follow-up ticket in the next
  planning session (SCRUM-XXX, or resolve during phase 06's `/gsd-ship` gate).
