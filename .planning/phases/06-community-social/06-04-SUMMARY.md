---
phase: 06-community-social
plan: 04
subsystem: social
tags: [spring-data-jpa, postgresql, upsert, angular-signals, ionic, feed]

# Dependency graph
requires:
  - phase: 06-community-social
    provides: "06-03's feed-action-rail (like/save/clone) and its TripLike/SavedTrip upsert-adjacent join-table pattern"
provides:
  - "trip_ratings table (V16) — composite PK (user_id, trip_id), CHECK (rating BETWEEN 1 AND 5)"
  - "TripRatingRepository.upsertRating — ON CONFLICT DO UPDATE, the toggle-vs-value distinction from likes/saves"
  - "POST /api/trips/{id}/rate and GET /api/trips/{id}/rating"
  - "Five-star rating control on the feed action rail, fetched once per active card"
affects: [06-06, docs/api-contracts.md]

# Actuals (#2632)
actuals:
  tokens: 13014
  tasks: 3
  commits: 3

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Upsert via native @Modifying INSERT ... ON CONFLICT DO UPDATE (vs. likes/saves' DO NOTHING) for a value a user can change, not toggle"
    - "@JdbcTypeCode(SqlTypes.SMALLINT) to pin the JDBC type Hibernate's schema validator checks against a SMALLINT column (columnDefinition string alone doesn't change it)"
    - "Angular signal-input-driven effect() gating a one-time-per-activation HTTP fetch, tested with TestBed.tick() per the codebase's existing session-state.service.spec.ts convention"

key-files:
  created:
    - backend/src/main/resources/db/migration/V16__create_trip_ratings.sql
    - backend/src/main/java/com/tripflow/backend/domain/TripRating.java
    - backend/src/main/java/com/tripflow/backend/domain/TripRatingId.java
    - backend/src/main/java/com/tripflow/backend/repository/TripRatingRepository.java
    - backend/src/main/java/com/tripflow/backend/service/TripRatingService.java
    - backend/src/main/java/com/tripflow/backend/dto/RateTripRequest.java
    - backend/src/main/java/com/tripflow/backend/dto/TripRatingSummaryResponse.java
    - backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java
  modified:
    - backend/src/main/java/com/tripflow/backend/controller/TripController.java
    - backend/src/test/java/com/tripflow/backend/controller/TripControllerRateLimitTest.java
    - frontend/src/app/core/services/trip.service.ts
    - frontend/src/app/core/services/trip.service.spec.ts
    - frontend/src/app/core/models/feed.model.ts
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.ts
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.html
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.scss
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts
    - frontend/src/app/pages/feed/components/feed-card/feed-card.component.ts
    - frontend/src/app/pages/feed/components/feed-card/feed-card.component.html
    - frontend/src/app/pages/feed/feed.page.html

key-decisions:
  - "Rating summary is fetched per active feed card (not folded into FeedTripResponse) to avoid a second per-trip aggregate on every feed page or a denormalized column to keep in sync"
  - "No unrate endpoint — a rating is a value the user changes via re-rating, not a toggle; removing a rating is out of scope for SOCIAL-07"
  - "TripRatingRepository.findAverageAndCountByTripId returns Object[] (not a projection DTO) — Spring Data wraps an array-typed aggregate query result in a one-element outer array; TripRatingService unwraps it once"

patterns-established:
  - "Value-changing join tables (ratings) use ON CONFLICT DO UPDATE; toggle join tables (likes/saves) use DO NOTHING — same @Modifying native-query mechanism, different conflict action chosen per semantics"
  - "SMALLINT-backed Integer entity fields need @JdbcTypeCode(SqlTypes.SMALLINT), not just a columnDefinition string, or ddl-auto=validate fails at boot"

requirements-completed: [SOCIAL-07]

coverage:
  - id: D1
    description: "A user can give a PUBLIC (or their own PRIVATE) trip a 1-5 star rating; re-rating replaces the value without creating a duplicate row or double-counting the average"
    requirement: "SOCIAL-07"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java#rateTrip_calledTwiceBySameUser_replacesRatingWithoutDuplicateRow"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java#rateTrip_byTwoDifferentUsers_producesTwoRows"
        status: pass
    human_judgment: false
  - id: D2
    description: "Out-of-range ratings are rejected at the API (400 with fieldErrors) and independently at the database (CHECK constraint)"
    requirement: "SOCIAL-07"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java#directInsert_outOfRangeRating_isRejectedByCheckConstraint"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java#directInsert_zeroRating_isRejectedByCheckConstraint"
        status: pass
    human_judgment: false
  - id: D3
    description: "Rating or reading the rating summary of a foreign PRIVATE trip returns 404, not 403; the caller's own average/count/myRating are retrievable in one request"
    requirement: "SOCIAL-07"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java#rateTrip_foreignPrivateTrip_throwsResourceNotFound"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java#getSummary_foreignPrivateTrip_throwsResourceNotFound"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java#getSummary_unratedTrip_returnsNullAverageAndZeroCount"
        status: pass
    human_judgment: false
  - id: D4
    description: "A star rating control on the feed action rail: fetches per active card only, taps set/replace the rating optimistically, revert-on-failure, one request per double-tap"
    requirement: "SOCIAL-07"
    verification:
      - kind: unit
        ref: "frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts#fetches the rating summary once the card becomes active"
        status: pass
      - kind: unit
        ref: "frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts#reverts the star display to the previous value when a rate request fails, and surfaces a toast"
        status: pass
      - kind: unit
        ref: "frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts#a rapid double-tap on a star issues exactly one outstanding HTTP request"
        status: pass
    human_judgment: false

# Metrics
duration: 95min
completed: 2026-08-31
status: complete
---

# Phase 6 Plan 04: Trip Ratings Summary

**Trip-level 1-5 star ratings (SOCIAL-07) via an upsert join table (`trip_ratings`, `ON CONFLICT DO UPDATE`), a rate + rating-summary endpoint pair, and a five-star control on the feed action rail that fetches per active card.**

## Performance

- **Duration:** ~95 min
- **Started:** 2026-08-31T21:20:00Z (approx.)
- **Completed:** 2026-08-31T22:04:14Z
- **Tasks:** 3
- **Files modified:** 20 (8 created, 12 modified) across the three task commits

## Accomplishments
- `trip_ratings` table (V16) with a composite PK and a `CHECK (rating BETWEEN 1 AND 5)`, backing an idempotent-by-value upsert instead of likes/saves' idempotent-by-presence toggle
- `POST /api/trips/{id}/rate` and `GET /api/trips/{id}/rating` — 404-not-403 existence hiding on both, matching the SCRUM-274 convention
- Rating summary (average, count, caller's own rating) computed on read, never denormalized, never folded into the feed payload
- Five-star control on `FeedActionRailComponent`, wired through a new `active` input threaded from `FeedPage`'s outer-swiper index, fetching a rating summary exactly once per card activation

## Task Commits

Each task was committed atomically:

1. **Task 1: Rating vertical slice — migration through POST /api/trips/{id}/rate** - `bbfee6d` (feat, tracer)
2. **Task 2: Rating summary endpoint** - `687ab58` (feat)
3. **Task 3: Star rating control on the feed action rail** - `0c1d221` (feat)
4. **Task 3 follow-up: rating-summary-fetch error path coverage** - `b6e66fe` (test)

**Plan metadata:** `618d80d` (docs: complete plan)

## Files Created/Modified
- `backend/src/main/resources/db/migration/V16__create_trip_ratings.sql` - trip_ratings table, composite PK, CHECK constraint, trip_id index
- `backend/src/main/java/com/tripflow/backend/domain/TripRating.java` / `TripRatingId.java` - mapping-only entity mirroring TripLike's @EmbeddedId/@MapsId shape, plus a SMALLINT-backed rating column
- `backend/src/main/java/com/tripflow/backend/repository/TripRatingRepository.java` - native upsert, JPQL average/count aggregate, caller's-own-rating finder
- `backend/src/main/java/com/tripflow/backend/service/TripRatingService.java` - `rateTrip`/`getSummary`, both ownership-checked first
- `backend/src/main/java/com/tripflow/backend/dto/RateTripRequest.java` / `TripRatingSummaryResponse.java` - request bounds, boxed-Double response
- `backend/src/main/java/com/tripflow/backend/controller/TripController.java` - `POST /{id}/rate`, `GET /{id}/rating`
- `backend/src/test/java/com/tripflow/backend/service/TripRatingServiceIT.java` - 13 Testcontainers-backed IT tests
- `backend/src/test/java/com/tripflow/backend/controller/TripControllerRateLimitTest.java` - updated constructor call site (Rule 3, pre-existing test broken by the new controller dependency)
- `frontend/src/app/core/services/trip.service.ts` / `.spec.ts` - `rateTrip`/`getTripRating`
- `frontend/src/app/core/models/feed.model.ts` - `TripRatingSummary` interface
- `frontend/src/app/pages/feed/components/feed-action-rail/*` - five-star control, `active` input, `TestBed.tick()`-based effect tests
- `frontend/src/app/pages/feed/components/feed-card/*`, `frontend/src/app/pages/feed/feed.page.html` - threaded the `active` input from the outer swiper's index down to the action rail

## Decisions Made
- Rating summary fetched per active card, not folded into `FeedTripResponse` — avoids a second aggregate per trip on every feed page or a denormalized average column to keep in sync (documented in both the repository's and service's Javadoc for future readers)
- No unrate endpoint: SOCIAL-07 treats a rating as a value, not a toggle
- `TripRatingRepository.findAverageAndCountByTripId` returns `Object[]` rather than a constructor-expression DTO — a two-column aggregate with no other consumer doesn't earn a dedicated projection class (ponytail: fewer files)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `TripControllerRateLimitTest` broke on the new `TripRatingService` constructor parameter**
- **Found during:** Task 1, first verify run
- **Issue:** Adding `TripRatingService` to `TripController`'s constructor broke the pre-existing rate-limit unit test's manual `new TripController(...)` call site (compile error)
- **Fix:** Added a `@Mock TripRatingService tripRatingService` field and passed it through the constructor call
- **Files modified:** `backend/src/test/java/com/tripflow/backend/controller/TripControllerRateLimitTest.java`
- **Verification:** `./mvnw -B verify -Pci -Dit.test=TripRatingServiceIT` and later the full `-Pci` suite both compile and pass
- **Committed in:** `bbfee6d` (Task 1 commit)

**2. [Rule 1 - Bug] `Integer rating` field failed `ddl-auto=validate` against the SMALLINT column**
- **Found during:** Task 1, first verify run
- **Issue:** A plain `@Column Integer rating` maps to Postgres `integer` by default; `V16`'s `rating SMALLINT` column made Hibernate's schema validator reject the mapping at boot (`SchemaManagementException: wrong column type`). A `columnDefinition = "smallint"` string alone did not fix it — the validator compares JDBC type codes, not the DDL string.
- **Fix:** Added `@JdbcTypeCode(SqlTypes.SMALLINT)` to pin the JDBC type code Hibernate validates against
- **Files modified:** `backend/src/main/java/com/tripflow/backend/domain/TripRating.java`
- **Verification:** `TripRatingServiceIT` boots and all 8 (then 13) tests pass
- **Committed in:** `bbfee6d` (Task 1 commit)

**3. [Rule 1 - Bug] `Object[]`-typed aggregate query was double-wrapped, not the row tuple directly**
- **Found during:** Task 2, first verify run
- **Issue:** `TripRatingRepository.findAverageAndCountByTripId` declared as returning `Object[]` actually came back as a one-element `Object[]` whose single element was itself the `[Double, Long]` row — a `ClassCastException` when the service tried to cast index 0 directly to `Double`
- **Fix:** Unwrapped one level in `TripRatingService.getSummary` (`(Object[]) repo.findAverageAndCountByTripId(tripId)[0]`), documented in both the repository Javadoc and the service's inline comment
- **Files modified:** `backend/src/main/java/com/tripflow/backend/service/TripRatingService.java`, `backend/src/main/java/com/tripflow/backend/repository/TripRatingRepository.java`
- **Verification:** All 13 `TripRatingServiceIT` tests pass
- **Committed in:** `687ab58` (Task 2 commit)

**4. [Rule 2 - Missing Critical] Added a coverage test for the rating-summary-fetch error path**
- **Found during:** Task 3, `npm run test:ci` run
- **Issue:** The rating-summary effect's `error` callback (toast on a failed `GET /rating`) had no test, leaving that branch uncovered
- **Fix:** Added a test flushing a 500 on the summary fetch and asserting the toast fires
- **Files modified:** `frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts`
- **Verification:** `feed-action-rail.component.ts` now reports 19/19 (100%) function coverage
- **Committed in:** `0c1d221` (Task 3 commit)

**5. [Plan gap — files outside frontmatter's `files_modified`] Wired `active` through `FeedCardComponent`/`FeedPage`**
- **Found during:** Task 3
- **Issue:** The plan's frontmatter `files_modified` list only named `trip.service.ts`/`.spec.ts` and the four `feed-action-rail.component.*` files, but action item 5 explicitly requires deriving `active` from the outer swiper's index and threading it down through `FeedCardComponent` — files not in that list
- **Fix:** Added an `active` signal input to `FeedCardComponent`, forwarded to the action rail, and set it from `FeedPage`'s `@for` loop index compared against `activeIndex()`
- **Files modified:** `frontend/src/app/pages/feed/components/feed-card/feed-card.component.ts`, `.html`, `frontend/src/app/pages/feed/feed.page.html`
- **Verification:** `feed-card.component.spec.ts` and `feed.page.spec.ts` (both pre-existing suites) pass unchanged — confirmed the new effect-driven HTTP fetch does not fire in those specs since neither calls `TestBed.tick()`/`ApplicationRef.tick()`
- **Committed in:** `0c1d221` (Task 3 commit)

---

**Total deviations:** 5 auto-fixed (1 blocking test-compile fix, 2 bugs, 1 missing coverage, 1 plan-scope gap for files the action text required but frontmatter omitted)
**Impact on plan:** All auto-fixes necessary for correctness (schema validation, ClassCastException) or completeness (coverage, the `active`-wiring the plan's own action text mandated). No scope creep beyond what SOCIAL-07 required.

## Issues Encountered

**`npm run test:ci` fails the frontend's global function-coverage floor (90%), pre-existing and unrelated to this plan.** Measured after all three commits: functions 89.22% (floor 90%), all 455 individual specs pass. The shortfall traces to files this plan never touched — `app.routes.ts` (0/18 functions, last touched by sibling plan 06-05), `stop-photo.service.ts` (70%, untouched since SCRUM-164), and three page components below floor. This plan's own new code (`feed-action-rail.component.ts`, `TripService.rateTrip`/`getTripRating`) is 100% function-covered by its own new tests. Logged in detail, with a recommended follow-up, in `.planning/phases/06-community-social/deferred-items.md` per the executor's scope-boundary rule (do not fix unrelated pre-existing gaps).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- SOCIAL-07 is closed: `trip_ratings`, the rate/rating-summary endpoints, and the feed star control are all live and tested
- `docs/api-contracts.md` entries for `POST /api/trips/{id}/rate` and `GET /api/trips/{id}/rating` are deferred to plan 06-06's documentation task, which owns that file for the phase (per this plan's own `<verification>` note)
- The pre-existing frontend coverage-floor shortfall (see Issues Encountered) should be picked up as a follow-up ticket before `/gsd-ship` for this phase, independent of 06-04

---
*Phase: 06-community-social*
*Completed: 2026-08-31*

## Self-Check: PASSED

All created files verified present (SQL migration, domain/repository/service/DTO/controller/test files, frontend service/model/component files, this SUMMARY, and deferred-items.md). All three task commits (`bbfee6d`, `687ab58`, `0c1d221`) verified present in `git log`.
