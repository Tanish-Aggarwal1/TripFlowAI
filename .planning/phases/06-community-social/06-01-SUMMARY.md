---
phase: 06-community-social
plan: 01
subsystem: api
tags: [spring-security, jpa, spring-data, discovery-feed, angular, http-client]

requires: []
provides:
  - "Authenticated GET /api/discovery/feed returning FeedTripResponse (owner/description/tags/stops)"
  - "SecurityConfig with /api/discovery/** removed from permitAll — whole discovery surface authenticated"
  - "Batched stop-photo fetch (StopPhotoRepository.findByStopIdInOrderByCreatedAtAsc) — one query per feed page"
  - "Angular DiscoveryService.getFeed(page, size) + FeedTrip/FeedStop models"
affects: [06-02, 06-06]

actuals:
  tokens: 9800
  tasks: 4
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Full-entity paged repository read (TripRepository.findByVisibility) mapped through a dedicated FeedTripMapper, distinct from the card-projection TripSummaryResponse query"
    - "Batch child-row fetch across a whole page: collect ids first, one IN query, group in memory (StopPhotoRepository.findByStopIdInOrderByCreatedAtAsc)"

key-files:
  created:
    - backend/src/main/java/com/tripflow/backend/dto/FeedTripResponse.java
    - backend/src/main/java/com/tripflow/backend/mapper/FeedTripMapper.java
    - backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java
    - frontend/src/app/core/models/feed.model.ts
    - frontend/src/app/core/services/discovery.service.ts
    - frontend/src/app/core/services/discovery.service.spec.ts
  modified:
    - backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java
    - backend/src/main/java/com/tripflow/backend/controller/DiscoveryController.java
    - backend/src/main/java/com/tripflow/backend/repository/TripRepository.java
    - backend/src/main/java/com/tripflow/backend/service/TripService.java
    - backend/src/main/java/com/tripflow/backend/repository/StopPhotoRepository.java
    - backend/src/test/java/com/tripflow/backend/controller/DiscoveryControllerIT.java
    - backend/src/test/java/com/tripflow/backend/service/TripServiceTest.java
    - frontend/package.json
    - frontend/package-lock.json

key-decisions:
  - "Task 1 checkpoint auto-selected option (a): removed the whole /api/discovery/** entry from permitAll, not just /feed — CONTEXT.md's Phase Boundary covers the entire discovery surface, and carving out only /feed would leave /trips and /search publicly enumerating every PUBLIC trip."
  - "viewerId is accepted by TripService.listFeed now but unused for ranking — 06-06 needs the parameter and adding it later would ripple through the controller and both test classes."
  - "Fixed a pre-existing package.json version-pin bug (@angular/core stuck at 22.1.3 while every other @angular/* package moved to 22.1.4) that blocked npm install entirely in this worktree — not new dependency work, just realigning an already-declared package's version."

patterns-established:
  - "Feed-shaped DTOs get their own mapper (FeedTripMapper) rather than stretching an existing card-projection DTO — mirrors the codebase's existing TripSummaryResponse vs TripResponse split."

requirements-completed: [SOCIAL-01]

coverage:
  - id: D1
    description: "GET /api/discovery/feed requires a valid JWT and returns paged PUBLIC trips with ownerUsername, description, tags, and an ordered stops array"
    requirement: "SOCIAL-01"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java#getFeed_noAuth_returns401, getFeed_authenticated_returnsFeedShapedPublicTrip, getFeed_authenticated_excludesPrivateTrips, getFeed_ownerUsernameIsSeededOwner_notViewer"
        status: pass
    human_judgment: false
  - id: D2
    description: "The whole discovery surface (/feed, /trips, /search) requires authentication — the pre-existing permitAll hole is closed"
    requirement: "SOCIAL-01"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/DiscoveryControllerIT.java#search_noAuth_missingQParam_returns401, listPublicTrips_noAuth_returns401"
        status: pass
    human_judgment: false
  - id: D3
    description: "A feed page load issues exactly one stop-photo query regardless of stop count, and zero-photo trips render with empty photoUrls (D-03 text-fallback data precondition)"
    requirement: "SOCIAL-01"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java#getFeed_zeroPhotoTrip_stillPresentWithEmptyPhotoUrls, getFeed_batchedPhotoFetch_preservesCreatedAtAscendingOrder"
        status: pass
    human_judgment: false
  - id: D4
    description: "Angular DiscoveryService.getFeed(page, size) calls GET /api/discovery/feed and maps 401 to a rejected observable"
    requirement: "SOCIAL-01"
    verification:
      - kind: unit
        ref: "frontend/src/app/core/services/discovery.service.spec.ts#all 4 specs"
        status: pass
    human_judgment: false
  - id: D5
    description: "RISK-R2 manual Postman/browser regression check confirming a real client sees 401 (not just MockMvc) after the SecurityConfig change"
    verification: []
    human_judgment: true
    rationale: "RISK-R2 explicitly requires a real HTTP client check against a running instance, which this plan's own <verification> section defers to the phase-level checklist — MockMvc coverage alone does not discharge it."

duration: 55min
completed: 2026-08-31
status: complete
---

# Phase 06 Plan 01: Authenticated Discovery Feed Summary

**Authenticated `GET /api/discovery/feed` returning full-card `FeedTripResponse` (owner/description/tags/ordered stops with batched photo fetch), plus the Angular `DiscoveryService` data seam — closing the pre-existing `/api/discovery/**` permitAll hole in the same pass.**

## Performance

- **Duration:** 55 min
- **Started:** 2026-08-31T05:00:00Z (approx)
- **Completed:** 2026-08-31T05:39:35Z
- **Tasks:** 4 (1 checkpoint:decision auto-selected, 3 executed)
- **Files modified:** 14 (6 created, 8 modified — excludes package-lock.json diff noise)

## Accomplishments
- Removed `/api/discovery/**` entirely from `SecurityConfig`'s permitAll list — the whole discovery surface (`/feed`, `/trips`, `/search`) now requires a valid JWT, closing the contradiction RESEARCH.md flagged between the shipped code and SOCIAL-01/CONTEXT.md's Phase Boundary
- New `FeedTripResponse`/`FeedTripMapper`: a full-card feed shape (owner username, description, tags, ordered stops with per-stop name/notes/photoUrls) distinct from the card-projection `TripSummaryResponse`
- `TripService.listFeed` batches the whole page's stop-photo lookup into exactly one query via `StopPhotoRepository.findByStopIdInOrderByCreatedAtAsc`, short-circuiting on an empty page
- `DiscoveryController` gained `GET /api/discovery/feed` (`@AuthenticationPrincipal UserPrincipal`); existing handlers' Swagger descriptions corrected to no longer claim auth is optional
- `DiscoveryControllerIT`'s three previously-anonymous-success tests now assert 401, each with a new authenticated counterpart so endpoint behavior stays covered
- Angular `DiscoveryService.getFeed(page, size)` + `FeedTrip`/`FeedStop` models, following `TripService`'s existing `mapApiError`/`handleError` convention

## Task Commits

Each task was committed atomically:

1. **Task 1: [BLOCKING] Decision gate** — auto-selected option (a) in auto mode (no separate commit; recorded here and folded into Task 2's implementation)
2. **Task 2: End-to-end authenticated feed tracer** — `9a6d138` (feat)
3. **Task 3: Batch stop-photo fetch + DiscoveryControllerIT auth corrections** — `dd49d93` (feat)
4. **Task 4: Angular DiscoveryService feed data seam** — `bd955b9` (feat)

_Task 2 is `type="tracer"` — its `<verify>` was re-run immediately after commit (passed, exit 0) before proceeding to Task 3, per the tracer feedback gate._

## Files Created/Modified
- `backend/src/main/java/com/tripflow/backend/dto/FeedTripResponse.java` - new feed-card DTO with nested `FeedStop`
- `backend/src/main/java/com/tripflow/backend/mapper/FeedTripMapper.java` - trip+photos → `FeedTripResponse` mapping
- `backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java` - `/api/discovery/**` removed from permitAll
- `backend/src/main/java/com/tripflow/backend/controller/DiscoveryController.java` - new `/feed` handler, corrected `@Operation` descriptions
- `backend/src/main/java/com/tripflow/backend/repository/TripRepository.java` - `findByVisibility(TripVisibility, Pageable)`
- `backend/src/main/java/com/tripflow/backend/repository/StopPhotoRepository.java` - `findByStopIdInOrderByCreatedAtAsc(List<Long>)`
- `backend/src/main/java/com/tripflow/backend/service/TripService.java` - `listFeed(viewerId, pageable)`, batched photo fetch
- `backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java` - new IT (401/200/private-exclusion/owner-identity/zero-photo/batch-ordering)
- `backend/src/test/java/com/tripflow/backend/controller/DiscoveryControllerIT.java` - 3 tests corrected to 401 + 3 authenticated counterparts added
- `backend/src/test/java/com/tripflow/backend/service/TripServiceTest.java` - constructor call updated for 2 new `TripService` deps
- `frontend/src/app/core/models/feed.model.ts` - `FeedTrip`/`FeedStop` interfaces
- `frontend/src/app/core/services/discovery.service.ts` - `DiscoveryService.getFeed(page, size)`
- `frontend/src/app/core/services/discovery.service.spec.ts` - 4 specs
- `frontend/package.json`, `frontend/package-lock.json` - `@angular/core` pinned to `22.1.4` (Rule 3 fix, see below)

## Decisions Made
- **Task 1 checkpoint (auto-selected under `auto_advance: true`, no `gate="blocking-human"` on the task):** Option (a) — remove the entire `/api/discovery/**` entry, not a `/feed`-only carve-out. Rationale: CONTEXT.md's Phase Boundary wording covers the whole discovery surface; a `/feed`-only carve-out would leave `/api/discovery/trips` and `/api/discovery/search` publicly enumerating every PUBLIC trip, the exact information-disclosure posture SOCIAL-01 exists to close.
- `TripService.listFeed(viewerId, pageable)` accepts `viewerId` now even though it doesn't branch on it yet — 06-06 (interest-based ranking) needs it, and adding it later would ripple through the controller and both test classes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `TripServiceTest` constructor call broke after adding 2 new `TripService` dependencies**
- **Found during:** Task 3 verify (`mvn verify -Pci`)
- **Issue:** `TripServiceTest.setUp()` constructed `TripService` positionally; adding `StopPhotoRepository`/`FeedTripMapper` fields broke compilation.
- **Fix:** Added `@Mock private StopPhotoRepository stopPhotoRepository` and a real `FeedTripMapper`, passed both into the constructor call.
- **Files modified:** `backend/src/test/java/com/tripflow/backend/service/TripServiceTest.java`
- **Verification:** `mvn verify -Pci` green.
- **Committed in:** `dd49d93` (Task 3 commit)

**2. [Rule 1 - Bug] New IT test's local `ObjectMapper` couldn't deserialize `Instant`**
- **Found during:** Task 3 verify, `DiscoveryFeedControllerIT.getFeed_batchedPhotoFetch_preservesCreatedAtAscendingOrder`
- **Issue:** The test's plain `new ObjectMapper()` (no JSR310 module) failed deserializing `TripResponse.createdAt` (`Instant`).
- **Fix:** Switched to `objectMapper.readTree(body)` and pulled only the needed `stops[0].id` field via `JsonNode`, avoiding full-record deserialization entirely.
- **Files modified:** `backend/src/test/java/com/tripflow/backend/controller/DiscoveryFeedControllerIT.java`
- **Verification:** Test passes.
- **Committed in:** `dd49d93` (Task 3 commit)

**3. [Rule 3 - Blocking] `@angular/core` pinned one minor behind its own peers, blocking `npm install` entirely**
- **Found during:** Task 4, first `npm test` attempt in this worktree
- **Issue:** `frontend/package.json` had `@angular/core: 22.1.3` while every other `@angular/*` package (`animations`, `common`, `compiler`, `forms`, `platform-browser`, `router`, `service-worker`, `cli`, `compiler-cli`, `language-service`) was already at `22.1.4` — a miss from the recent dependabot minor/patch-bump commit (`4720adb`, top of this session's git log). `npm install`/`npm ci` both failed with an `ERESOLVE` peer conflict, blocking the worktree from running *any* frontend test, not just the new one.
- **Fix:** Bumped `@angular/core` to `22.1.4` in `package.json`, re-ran `npm install` to regenerate the small corresponding `package-lock.json` diff (5 lines).
- **Files modified:** `frontend/package.json`, `frontend/package-lock.json`
- **Verification:** `npm install` succeeds; `npm test`, `npm run test:ci`, `npm run lint` all green afterward.
- **Committed in:** `bd955b9` (Task 4 commit)

---

**Total deviations:** 3 auto-fixed (1 blocking test-compile fix, 1 bug fix in new test code, 1 blocking dependency-pin fix)
**Impact on plan:** All three were required to complete verification; none represent scope creep beyond what each task's own `<verify>` demanded.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- 06-02 (feed UI) can now build against a real, authenticated `GET /api/discovery/feed` and a typed Angular `DiscoveryService`/`FeedTrip` model — no stub data.
- 06-06 (interest-based ranking) has `TripService.listFeed(viewerId, pageable)` already accepting `viewerId`; it only needs to add the interest-overlap `ORDER BY` and stop ignoring the parameter.
- **RISK-R2 owed at phase level:** a real Postman/browser check that `GET /api/discovery/trips` with no `Authorization` header returns 401 against a running instance — MockMvc coverage here is not a substitute per the project's own risk register.
- `docs/auth.md`'s permitAll table still lists `/api/discovery/**` as public; per this plan's Task 2 action note, that documentation correction is deferred to plan 06-06, not done here.

---
*Phase: 06-community-social*
*Completed: 2026-08-31*

## Self-Check: PASSED

All 7 claimed files verified present (`FeedTripResponse.java`, `FeedTripMapper.java`, `DiscoveryFeedControllerIT.java`, `feed.model.ts`, `discovery.service.ts`, `discovery.service.spec.ts`, this SUMMARY). All 3 claimed commit hashes (`9a6d138`, `dd49d93`, `bd955b9`) verified present in `git log --oneline --all`.
