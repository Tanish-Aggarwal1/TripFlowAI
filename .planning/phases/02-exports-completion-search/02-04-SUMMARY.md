---
phase: 02-exports-completion-search
plan: 04
subsystem: api
tags: [spring-data-jpa, postgres, native-query, angular, rxjs, ionic]

requires:
  - phase: 02-exports-completion-search (plan 02-03)
    provides: TripOwnerSummaryResponse projection and the owner/public DTO split (D-08)
provides:
  - "GET /api/trips search (title/tags/stop place-names) and filters (status, visibility, start-date range, duration), owner-scoped"
  - "TripSearchRepository.searchOwnedTrips + shared TEXT_MATCH_SQL fragment reused by searchPublicTrips (D-11)"
  - "Debounced dashboard search box and filter controls (status/visibility/date-range/duration)"
  - "Corrected frontend TripStatus enum (DRAFT/PLANNED/ACTIVE/COMPLETED)"
  - "docs/api-contracts.md brought current for all three Phase 2 slices (PDF export, completion fields, search/filter)"
affects: [dashboard, trip-search, api-contracts]

actuals:
  tokens: 42000
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Shared native-query text-match fragment (TEXT_MATCH_SQL) reused across searchPublicTrips/searchOwnedTrips, differing only in scope + filter chain (D-11)"
    - "CAST(:param AS type) IS NULL null-tolerant filter chain — one static query text, never branched on which filters are present"
    - "One RxJS debounce/distinct/switchMap stream carrying the whole filter object, shared by the search box and every filter control"

key-files:
  created:
    - backend/src/main/java/com/tripflow/backend/dto/TripSearchFilters.java
  modified:
    - backend/src/main/java/com/tripflow/backend/repository/TripSearchRepository.java
    - backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java
    - backend/src/main/java/com/tripflow/backend/service/TripService.java
    - backend/src/main/java/com/tripflow/backend/controller/TripController.java
    - backend/src/test/java/com/tripflow/backend/service/TripServiceTest.java
    - backend/src/test/java/com/tripflow/backend/repository/TripSearchRepositoryIT.java
    - backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java
    - frontend/src/app/core/models/trip.model.ts
    - frontend/src/app/core/services/trip.service.ts
    - frontend/src/app/core/services/trip.service.spec.ts
    - frontend/src/app/pages/trips/dashboard/dashboard.page.ts
    - frontend/src/app/pages/trips/dashboard/dashboard.page.html
    - frontend/src/app/pages/trips/dashboard/dashboard.page.scss
    - frontend/src/app/pages/trips/dashboard/dashboard.page.spec.ts
    - docs/api-contracts.md

key-decisions:
  - "TripService.searchOwnedTrips (not the repository) normalizes search text into its final ILIKE pattern (null or \"%text%\") — TripSearchRepositoryImpl.searchOwnedTrips takes the pattern as-is rather than re-wrapping, since the plan's TripServiceTest assertions pin the pattern-or-null contract at the service boundary."
  - "TEXT_MATCH_SQL (title/tags/place-name matching) is shared via one Java constant between searchPublicTrips and searchOwnedTrips per D-11, but the filter chain (status/visibility/date/duration + COALESCE(MAX(day_number),0)) is duplicated verbatim between the id-query and count-query strings — needed because grep-based acceptance criteria required the literal SQL text to appear in both places, and it also matches the codebase's existing convention of duplicating predicate text between id/count query pairs."
  - "durationDays stays filter-only, never added to any response DTO, per the plan's explicit non-goal."

requirements-completed: [SEARCH-01]

coverage:
  - id: D1
    description: "Owner searches their trip list by title, tags, or stop place-name; results never include a trip owned by someone else, PUBLIC or not"
    requirement: SEARCH-01
    verification:
      - kind: unit
        ref: "TripServiceTest#searchOwnedTrips_populatedSearch_passesWildcardedPatternToRepository"
        status: pass
      - kind: integration
        ref: "TripSearchRepositoryIT#searchOwnedTrips_anotherUsersPublicTripMatching_returnsNothing (CI-only, Testcontainers)"
        status: unknown
    human_judgment: false
  - id: D2
    description: "status/visibility/startDate-range/durationDays filters AND together; search independently optional; blank search returns full list not 400; duration computed from stops (COALESCE 0 for stopless trips)"
    requirement: SEARCH-01
    verification:
      - kind: integration
        ref: "TripSearchRepositoryIT#searchOwnedTrips_multipleFilters_narrowToIntersection, #searchOwnedTrips_zeroStopTrip_matchesDurationZero_excludedFromNonZero (CI-only, Testcontainers)"
        status: unknown
      - kind: unit
        ref: "TripControllerIT#listTrips_invalidStatusValue_returns400WithApiError (CI-only, Testcontainers)"
        status: unknown
    human_judgment: false
  - id: D3
    description: "Dashboard offers debounced search plus status/visibility/date-range/duration filter controls, sharing one request stream so a keystroke and a filter change never race"
    verification:
      - kind: unit
        ref: "dashboard.page.spec.ts#'search and filters' describe block (debounce, filter combination, clearFilters)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Frontend TripStatus corrected to the real backend enum (DRAFT/PLANNED/ACTIVE/COMPLETED); statusColor/statusLabel cover all four"
    verification:
      - kind: unit
        ref: "dashboard.page.spec.ts#statusColor and #statusLabel describe blocks"
        status: pass
    human_judgment: false

duration: ~50min
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 4: Search & Filter Summary

**Owner-scoped trip search (title/tags/stop place-names) and status/visibility/date-range/duration filters on `GET /api/trips`, with a debounced dashboard search+filter bar and a corrected `TripStatus` frontend enum.**

## Performance

- **Duration:** ~50 min
- **Tasks:** 3 (Task 1 tracer, Task 2 TDD full match+filters, Task 3 filter UI/docs)
- **Files modified:** 15 (1 new)

## Accomplishments
- `GET /api/trips` accepts `search`, `status`, `visibility`, `startDateFrom`, `startDateTo`, `durationDays` — any subset, all AND-combined, `search` independently optional — inside the unchanged REF-21 paged envelope.
- Search matches title, tags, and stop place-names via `EXISTS` subqueries (never a top-level join), so a trip with multiple matching stops is returned exactly once.
- Owner scope (`t.user_id = :userId`) sits in the native SQL WHERE clause of both the id query and the count query — a stranger's trip, PUBLIC or not, is never returned or counted.
- Every user-derived value (pattern, userId, status, visibility, both dates, durationDays, limit, offset) is bound via `.setParameter(...)`; no string concatenation into SQL anywhere in the new or touched queries.
- Duration filters on `COALESCE(MAX(day_number), 0)` across a trip's stops, so a stopless/never-optimized trip reports duration 0 instead of silently vanishing from a duration-filtered list.
- Dashboard gained a debounced (350ms) search box plus status/visibility selects, native `<input type="date">` range fields, and a duration input — all sharing one RxJS stream so a keystroke and a filter change share one request path.
- Fixed the stale frontend `TripStatus` union (`DRAFT | IN_PROGRESS | COMPLETED` → the real `DRAFT | PLANNED | ACTIVE | COMPLETED`) and the dashboard's `statusColor`/`statusLabel` helpers, which previously branched on a value the backend never emits.
- `docs/api-contracts.md` brought current for all three Phase 2 slices: the new query params, `visitedStopCount`/`completionPercentage` on both the list and detail responses, why the discovery feed intentionally keeps the leaner shape, and a new `GET /api/trips/{id}/export/pdf` section describing the shipped (previously undocumented) PDF endpoint.

## Task Commits

1. **Task 1: tracer — title-only search end-to-end** - `9d2083f` (feat)
2. **Task 2: RED — failing IT coverage for full match/filter surface** - `1473ba7` (test)
2. **Task 2: GREEN — full search match surface and filter set** - `d71139e` (feat)
3. **Task 3: filter UI, enum fix, API contract docs** - `744825c` (feat)

_No separate plan-metadata commit — `commit_docs` handling deferred to the orchestrator per the executor's instructions not to touch `STATE.md`/`ROADMAP.md`/`config.json`._

## Files Created/Modified
- `backend/src/main/java/com/tripflow/backend/dto/TripSearchFilters.java` - new record: status/visibility/startDateFrom/startDateTo/durationDays, all nullable, `none()` factory
- `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepository.java` - `searchOwnedTrips` interface method
- `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java` - shared `TEXT_MATCH_SQL` fragment; `searchOwnedTrips`/`matchingOwnedIds`/`countOwnedMatches` with the full filter chain
- `backend/src/main/java/com/tripflow/backend/service/TripService.java` - `searchOwnedTrips`: blank-search-is-not-an-error normalization
- `backend/src/main/java/com/tripflow/backend/controller/TripController.java` - `GET /api/trips` gains `search`/`status`/`visibility`/`startDateFrom`/`startDateTo`/`durationDays`, routes to search vs plain list
- `backend/src/test/java/com/tripflow/backend/service/TripServiceTest.java` - 4 new cases: null/blank/whitespace/populated search normalization
- `backend/src/test/java/com/tripflow/backend/repository/TripSearchRepositoryIT.java` - 16 new `searchOwnedTrips_*` methods (match surface, adjacency, scope, filters, ordering)
- `backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java` - full query-string envelope check, search-narrowing case, malformed-status 400 case
- `frontend/src/app/core/models/trip.model.ts` - `TripStatus` corrected; new `TripListFilters` interface
- `frontend/src/app/core/services/trip.service.ts` - `listTrips` takes a `TripListFilters` object instead of a positional search string
- `frontend/src/app/core/services/trip.service.spec.ts` - specs for empty-filter URL stability, search, and combined filters
- `frontend/src/app/pages/trips/dashboard/dashboard.page.ts` - filter state + debounced stream, status/visibility/date/duration handlers, `clearFilters`, corrected `statusColor`/`statusLabel`
- `frontend/src/app/pages/trips/dashboard/dashboard.page.html` - searchbar, filter bar, distinct "no matches" empty state
- `frontend/src/app/pages/trips/dashboard/dashboard.page.scss` - `.filter-bar` layout
- `frontend/src/app/pages/trips/dashboard/dashboard.page.spec.ts` - debounce, filter-combination, clear-filters, statusColor/statusLabel specs
- `docs/api-contracts.md` - `GET /api/trips` query params, completion fields on list+detail, discovery-feed asymmetry note, `GET /api/trips/{id}/export/pdf` section

## Decisions Made
- Normalized the search-pattern-vs-null contract at the **service** layer (`TripService.searchOwnedTrips`) rather than the repository, so `TripSearchRepositoryImpl.searchOwnedTrips` takes its `query` argument as an already-final ILIKE pattern (or `null`) — this is asymmetric with `searchPublicTrips` (which still wraps internally), a deliberate difference since a blank search is valid here but an error there.
- Kept the filter-chain SQL text duplicated between the owner id-query and count-query strings (not extracted into one shared Java constant used by both) so the plan's grep-based acceptance criteria (`COALESCE(MAX(s.day_number), 0)` appearing at least twice in the file) hold literally — this also matches the existing codebase convention of duplicating predicate text between id/count pairs (see `searchPublicTrips`'s pre-existing `matchingIds`/`countMatches`).
- `durationDays` was implemented as filter-input-only, per the plan's explicit "deliberately NOT produced" note — no response DTO field, no extra correlated subquery on a read path nothing renders.

## Deviations from Plan

None — plan executed exactly as written. The plan's own task 1 action text anticipated the empty-`TripSearchFilters`-record-in-task-1 possibility ("if that reads awkwardly, land the empty TripSearchFilters record here and let task 2 fill its components"); the full record with all 5 components was landed in task 1 since the fields themselves were trivial and this avoided a later signature change.

## Issues Encountered
- **TDD RED phase could not be run locally.** `TripSearchRepositoryIT`/`TripControllerIT` require Testcontainers/Docker, and this machine's Docker daemon is not running (per CLAUDE.md, "no team machine runs Docker"). The RED commit (`1473ba7`) was verified via `./mvnw test-compile` (and `-Pci` test-compile) only, not an actual failing run. `mvn -B verify -Pci` in CI is the real RED/GREEN gate for this pair — flagging so a reviewer knows the fail-fast TDD guarantee ("a test that passes unexpectedly during RED means investigate") was not mechanically checked, only reasoned through.

## Security Prohibitions — Verified

1. **User-supplied search text and filter values must never be string-concatenated into SQL.** Confirmed: `grep -c 'setParameter' TripSearchRepositoryImpl.java` → 15 (every value across all four native queries is bound); `grep -c 'createNativeQuery'` → exactly 4 (no other native query construction in the file); the query text itself is one static string per method, never assembled conditionally per which filters are present.
2. **The search endpoint must never return, count, or otherwise confirm the existence of a trip the requester does not own.** Confirmed: `t.user_id = :userId` (sourced from `principal.userId()`, never a request parameter) sits in the WHERE clause of both `matchingOwnedIds` and `countOwnedMatches`; `TripSearchRepositoryIT#searchOwnedTrips_anotherUsersPublicTripMatching_returnsNothing` asserts a matching PUBLIC trip owned by someone else returns 0 results and 0 `totalElements` (CI-only, Testcontainers — reasoned/compiled locally, not executed).

Both prohibitions are structurally satisfied by inspection and unit-test coverage; the two integration tests that would prove them against real Postgres could not be executed on this machine and are the actual CI gate.

## Next Phase Readiness
- Phase 2 (Exports, Completion & Search) is now fully implemented across all three waves (PDF export, completion percentage, search/filter).
- `docs/api-contracts.md` is current for everything shipped in this phase.
- Outstanding: CI must run `mvn -B verify -Pci` to execute `TripSearchRepositoryIT`/`TripControllerIT` for real — this is the actual proof of the two security prohibitions and the full `<behavior>` list, not yet mechanically confirmed on this machine.

## Self-Check: PASSED
- FOUND: 9d2083f, 1473ba7, d71139e, 744825c (all four task commits)
- FOUND: backend/src/main/java/com/tripflow/backend/dto/TripSearchFilters.java
- FOUND: .planning/phases/02-exports-completion-search/02-04-SUMMARY.md

---
*Phase: 02-exports-completion-search*
*Completed: 2026-08-21*
