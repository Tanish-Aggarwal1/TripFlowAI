---
phase: 06-community-social
plan: 06
subsystem: api
tags: [spring-boot, jpa, postgres, native-query, discovery-feed, documentation]

requires:
  - phase: 06-01
    provides: "TripService.listFeed(viewerId, pageable), FeedTripResponse, the authenticated GET /api/discovery/feed endpoint"
  - phase: 06-05
    provides: "User.interests TEXT[] column, mirroring Trip.tags exactly"
provides:
  - "TripRepository.findPublicRankedByInterests: native unnest+IN ranked query with countQuery, closing SOCIAL-06"
  - "TripService.listFeed branches ranked-vs-recency on the viewer's stored interests, and rejects a client-supplied sort"
  - "docs/api-contracts.md documents every endpoint phase 6 shipped (feed, discovery auth correction, save/unsave/saved-list, rate/rating-summary, profile get/update)"
  - "docs/auth.md's permitAll table no longer lists the discovery surface as public"
affects: []

actuals:
  tokens: 9750
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "unnest(array) + IN (:collection) as the injection-safe substitute for Postgres's && array-overlap operator when the array elements are free-text and user-supplied — avoids ever constructing a text[] array literal from untrusted input"
    - "Strip Pageable's Sort before passing it to a native @Query with an explicit ORDER BY: Spring Data JPA appends its own ORDER BY built from the raw JPA property name for native queries (no column-name resolution), which silently breaks any native query that already carries its own ordering"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/tripflow/backend/repository/TripRepository.java
    - backend/src/main/java/com/tripflow/backend/service/TripService.java
    - backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java
    - docs/api-contracts.md
    - docs/auth.md

key-decisions:
  - "Used unnest(t.tags) + tag IN (:interests) instead of 06-RESEARCH.md Pattern 4's && array-overlap sketch — && requires binding a text[] parameter, which means assembling a Postgres array literal string from free-text, user-supplied interest values; a value containing a comma, brace, or double quote would silently corrupt that literal. The unnest+IN form binds an ordinary Spring Data collection parameter expanded into regular placeholders, so no literal is ever constructed."
  - "Rule 1 fix (found via Task 1's own <verify>, before commit): the ranked query failed every non-empty-interests test with a 500 (column t.createdat does not exist) because Spring Data JPA appends an ORDER BY built from the raw JPA property name onto native queries carrying a non-empty Pageable Sort, and has no way to resolve createdAt to the actual created_at column for a native query. Fixed by stripping the Sort (PageRequest.of(pageNumber, pageSize)) before calling the ranked repository method — the ranking query's own explicit ORDER BY already encodes the only ordering this endpoint supports, so nothing is lost."
  - "Task 2's sort-rejection reuses TripService.searchPublicTrips's existing InvalidRequestException mechanism verbatim rather than inventing a new one, matching the established convention for 'client sort would silently defeat a fixed server-side ordering'."

patterns-established:
  - "unnest+IN as the codebase's canonical injection-safe alternative to && for free-text array-overlap ranking — the next feature needing a similar match-boolean-in-ORDER-BY should reach for this, not &&."

requirements-completed: [SOCIAL-06]

coverage:
  - id: D1
    description: "PUBLIC trips whose tags overlap the viewer's stored profile interests are ordered first, recency second, both within groups and as the whole-feed fallback for a viewer with no stored interests (D-05/D-06); PRIVATE trips never surface under ranking; ranking never reads the viewer's own trip tags"
    requirement: "SOCIAL-06"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java#getFeed_olderMatchingTripOutranksNewerNonMatchingTrip, getFeed_bothMatchingTrips_moreRecentAppearsFirst, getFeed_neitherTripMatches_moreRecentAppearsFirst, getFeed_viewerWithEmptyInterests_isPureRecencyOrderWithNoError, getFeed_privateTripWithMatchingTag_neverAppears, getFeed_twoViewersWithDifferentInterests_receiveDifferentOrderings, getFeed_rankingUsesStoredInterests_notViewersOwnTripTags"
        status: pass
    human_judgment: false
  - id: D2
    description: "Paging the ranked feed never duplicates or skips a trip across pages, the reported total-element count excludes PRIVATE trips, a client-supplied sort parameter is rejected rather than silently honoured, and an out-of-range page returns 200 with empty content"
    requirement: "SOCIAL-06"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java#getFeed_pagingOverRankedFeed_yieldsDistinctIdsNoDuplicateNoOmission, getFeed_totalElementCount_excludesPrivateTrips, getFeed_clientSuppliedSort_isRejected, getFeed_pageBeyondLast_returns200WithEmptyContent"
        status: pass
    human_judgment: false
  - id: D3
    description: "docs/api-contracts.md documents every endpoint phase 6 added (feed with ranking rules, corrected discovery auth requirement, save/unsave/saved-list, rate/rating-summary, profile get/update, User.interests field limit), and docs/auth.md's permitAll table no longer lists the discovery surface as public"
    verification:
      - kind: other
        ref: "grep -q 'api/discovery/feed' docs/api-contracts.md && grep -q 'rate' docs/api-contracts.md && grep -q 'api/profile' docs/api-contracts.md && grep -q 'saved' docs/api-contracts.md && ! grep -q 'api/discovery' docs/auth.md (all pass)"
        status: pass
    human_judgment: false
  - id: D4
    description: "RISK-R2 manual check: a real HTTP client (Postman/browser, not MockMvc) confirms GET /api/discovery/trips with no Authorization header returns 401 against a running instance"
    verification: []
    human_judgment: true
    rationale: "Owed since 06-01, explicitly requires a real HTTP client against a deployed/running instance — this executor has no such instance to hit. Logged as WINDOWS.md entry #7 (unrun-verify) rather than silently dropped."

duration: ~65min (approx — start time not captured before the mandatory pre-flight fetch/merge step)
completed: 2026-08-31
status: complete
---

# Phase 06 Plan 06: Interest-Ranked Feed Ordering and Phase Documentation Summary

**Postgres `unnest`+`IN` interest-overlap ranking on `GET /api/discovery/feed` (closing SOCIAL-06), plus the phase-wide `docs/api-contracts.md`/`docs/auth.md` truth-up covering all five prior plans' shipped endpoints.**

## Performance

- **Duration:** ~65 min (approx)
- **Completed:** 2026-08-31T22:40:34Z
- **Tasks:** 3 (1 tracer, 1 auto, 1 auto)
- **Files modified:** 5 (0 created, 5 modified)

## Accomplishments
- `TripRepository.findPublicRankedByInterests`: a native `unnest(t.tags) EXISTS ... IN (:interests)` ranked query with a matching `countQuery`, deliberately avoiding the `&&` array-overlap operator sketched in 06-RESEARCH.md because it would require assembling a Postgres array literal from free-text, user-supplied interest values
- `TripService.listFeed` now loads the viewer's stored `User.interests` and branches: non-empty interests call the ranked query, empty interests keep using the existing recency finder (an empty SQL `IN (...)` list is a syntax error, not a no-match query)
- A non-default client sort on the feed is rejected with the same mechanism `searchPublicTrips` already uses — the ranking IS the endpoint's contract, so it can't be silently defeated
- `DiscoveryFeedControllerIT` grew from 6 to 18 tests: 7 covering the ranking behaviors (match-then-recency, empty-interests fallback, PRIVATE exclusion under ranking, per-viewer differing order, and the D-06 guarantee that ranking never reads the viewer's own trip tags) and 4 covering paging stability, total-element count, sort rejection, and out-of-range paging
- `docs/api-contracts.md` gained new sections for `GET /api/discovery/feed`, the save/unsave/saved-list trio, rate/rating-summary, and profile get/update, corrected the `GET /api/discovery/trips`/`search` auth requirement from "none" to Bearer-required, and added a `User.interests` field-limit row
- `docs/auth.md`'s permitAll table no longer lists the discovery surface as public, with a note on when/why it became authenticated

## Task Commits

Each task was committed atomically:

1. **Task 1 (tracer): End-to-end interest ranking** - `cd0b82e` (feat)
2. **Task 2: Paging stability and ranking-order regression coverage** - `9437bd7` (feat)
3. **Task 3: Phase documentation pass** - `ae5e2ba` (docs)

_Task 1 is `type="tracer"` — its `<verify>` (`./mvnw -B verify -Pci -Dit.test=DiscoveryFeedControllerIT`) was re-run to completion (all 14 tests passing) before this task was committed, and again after Task 2's additions (all 18 tests passing), per the tracer feedback gate._

## Files Created/Modified
- `backend/src/main/java/com/tripflow/backend/repository/TripRepository.java` - `findPublicRankedByInterests(Collection<String>, Pageable)`, native query + countQuery
- `backend/src/main/java/com/tripflow/backend/service/TripService.java` - `listFeed` branches ranked-vs-recency, strips Sort before the native ranked call, rejects a non-default client sort
- `backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java` - 12 new tests (7 ranking behaviors, 4 paging/regression, plus the `createTrip`/`createTestUserWithInterests`/`setCreatedAt` helper additions they needed)
- `docs/api-contracts.md` - new Discovery/`feed` section, corrected discovery auth, new Saved Trips / Trip Ratings / Profile sections, `User.interests` field-limit row
- `docs/auth.md` - permitAll table correction, prose note on the discovery-surface auth change

## Decisions Made
See `key-decisions` in frontmatter above: the `unnest`+`IN` deviation from the research's `&&` sketch (T-06-06-01's stated mitigation), the Rule 1 Sort-stripping fix, and reusing `searchPublicTrips`'s sort-rejection mechanism verbatim for Task 2.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Native ranked query returned 500 on every non-empty-interests request**
- **Found during:** Task 1, first `<verify>` run
- **Issue:** `mvnw -B verify -Pci -Dit.test=DiscoveryFeedControllerIT` failed 5 of the new ranking tests with `500 Internal Server Error` / `PSQLException: column t.createdat does not exist`. Root cause: Spring Data JPA's native-query paging support appends its own `ORDER BY` built from the raw JPA property name in the incoming `Pageable`'s `Sort` (it has no way to resolve `createdAt` to the mapped `created_at` column for a native query), which both duplicated and broke the ranked query's own explicit `ORDER BY`.
- **Fix:** `TripService.listFeed`'s ranked branch now calls the repository with `PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())` — a sort-free `Pageable` — instead of forwarding the controller's `Pageable` (which always carries the `@PageableDefault`'s `createdAt desc` sort) directly. The ranked query's own fixed `ORDER BY` already encodes the only ordering this endpoint supports, so nothing is lost; the returned `Page`'s metadata still uses the original `pageable` for the response shape.
- **Files modified:** `backend/src/main/java/com/tripflow/backend/service/TripService.java`
- **Verification:** All 14 `DiscoveryFeedControllerIT` tests passed after the fix; re-confirmed with all 18 after Task 2's additions.
- **Committed in:** `cd0b82e` (Task 1 commit)

**2. [Rule 3 - Blocking] 25-trip paging test hit the per-user trip-create rate limit**
- **Found during:** Task 2, first `<verify>` run
- **Issue:** `application-test.properties` caps `app.ratelimit.trip-create` at 5/user/hour; the new 25-trip paging test originally created all 25 trips under one owner and failed with `429` on the 6th `POST /api/trips` call.
- **Fix:** Spread the 25 trips across 5 distinct owners (5 trips each), staying within each owner's rate-limit budget.
- **Files modified:** `backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java`
- **Verification:** All 18 tests pass.
- **Committed in:** `9437bd7` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug found via the task's own verify loop, 1 blocking test-environment fix)
**Impact on plan:** Both were required to complete the plan's own `<verify>` command; no scope creep beyond what SOCIAL-06 required.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None.

## Threat Flags
None beyond what the plan's own `<threat_model>` already covers — T-06-06-01 through T-06-06-05 and T-06-06-SC are all mitigated as specified: interests bound as a JPA collection parameter (never concatenated, never a Postgres array literal), the visibility filter confined to the query's `WHERE` clause so no reordering widens visibility, the client-sort rejection pinning the ranking against override, the documentation correction closing the stale-docs disclosure risk, and the existing `@PageableDefault(size = 20)` plus the ranked query's own `countQuery` bounding both page size and count cost.

## Next Phase Readiness / Owed Manual Checks

Two phase-level manual checks remain owed and are **not** discharged by this plan (neither is reachable from this executor's environment):

- **RISK-R2** (owed since 06-01): a real HTTP client (Postman/browser, not MockMvc) confirming `GET /api/discovery/trips` with no `Authorization` header returns 401 against a running instance. Logged as `.planning/WINDOWS.md` entry #7 (`unrun-verify`).
- **Nested-swipe touch-device check** (owed since 06-02/06-03): `nested=true` diagonal-drag gesture disambiguation on a real touch device or DevTools touch emulation. Already logged as `.planning/WINDOWS.md` entry #4 — not duplicated here.

Phase 6 (Community & Social) is otherwise functionally complete across all 6 plans: discovery feed, engagement actions (like/save/clone), ratings, profile/interests, and now interest-based ranking plus documentation truth-up. The pre-existing frontend function-coverage gate shortfall (WINDOWS.md entries #5/#6) remains open and unrelated to this plan — untouched frontend files.

---
*Phase: 06-community-social*
*Completed: 2026-08-31*

## Self-Check: PASSED

All 7 claimed files verified present (`TripRepository.java`, `TripService.java`, `DiscoveryFeedControllerIT.java`, `docs/api-contracts.md`, `docs/auth.md`, this SUMMARY, `.planning/WINDOWS.md`). All 3 claimed commit hashes (`cd0b82e`, `9437bd7`, `ae5e2ba`) verified present in `git log --oneline`.
