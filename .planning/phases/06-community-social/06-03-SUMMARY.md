---
phase: 06-community-social
plan: 03
subsystem: social
tags: [spring-boot, jpa, postgres, angular, ionic, standalone-components, flyway]

requires:
  - phase: 06-02
    provides: "FeedCardComponent's reserved right-edge region and the /feed full-screen swipe surface"
provides:
  - "trip_saves join table (V14) and TripSaveService.saveTrip/unsaveTrip, idempotent and 404-not-403 on foreign PRIVATE trips (SOCIAL-04)"
  - "GET /api/trips/saved: paged, owner-scoped saved-trips list resolving ahead of the {id} path template"
  - "Angular TripService seam: likeTrip/unlikeTrip/saveTrip/unsaveTrip/cloneTrip/listSavedTrips"
  - "FeedActionRailComponent: on-card like/save/clone rail (D-04) wired into every feed card"
affects: [06-04, 06-05, 06-06]

actuals:
  tokens: 14424
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "trip_saves mirrors trip_likes exactly (composite PK entity, ON CONFLICT DO NOTHING native insert, JPQL bulk delete) minus the denormalized count column — no save count is displayed anywhere in this phase"
    - "GET /api/trips/saved declared above GET /api/trips/{id} in TripController so Spring's literal-segment path matching resolves it correctly; pinned by a MockMvc IT, not just source ordering"
    - "ARIA toggle-button on Ionic ion-button: static aria-label + reactive [color]/icon binding, NOT a dynamic aria-* attribute — Ionic's Stencil componentWillLoad snapshots aria-* attributes off the host exactly once and never re-reads them, so any attribute-bound state (aria-label text, aria-pressed) silently freezes after first render"
    - "Single shared busy signal guards all three action-rail handlers so a rapid double-tap (same or different control) collapses to one outstanding request, mirroring FeedPage's loadingMore convention from 06-02"

key-files:
  created:
    - backend/src/main/resources/db/migration/V14__create_trip_saves.sql
    - backend/src/main/java/com/tripflow/backend/domain/SavedTrip.java
    - backend/src/main/java/com/tripflow/backend/domain/SavedTripId.java
    - backend/src/main/java/com/tripflow/backend/repository/SavedTripRepository.java
    - backend/src/main/java/com/tripflow/backend/service/TripSaveService.java
    - backend/src/test/java/com/tripflow/backend/service/TripSaveServiceIT.java
    - backend/src/test/java/com/tripflow/backend/controller/TripSaveControllerIT.java
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.ts
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.html
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.scss
    - frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts
  modified:
    - backend/src/main/java/com/tripflow/backend/controller/TripController.java
    - backend/src/test/java/com/tripflow/backend/controller/TripControllerRateLimitTest.java
    - frontend/src/app/core/services/trip.service.ts
    - frontend/src/app/core/services/trip.service.spec.ts
    - frontend/src/app/pages/feed/components/feed-card/feed-card.component.ts
    - frontend/src/app/pages/feed/components/feed-card/feed-card.component.html

key-decisions:
  - "Added TripSaveControllerIT (mirroring the existing TripLikeControllerIT) rather than cramming HTTP-routing/401 assertions into the @DataJpaTest-based TripSaveServiceIT the plan named — a repository-slice test cannot exercise Spring's path-matching or SecurityConfig's auth filter, both of which the plan's own behavior list required proving."
  - "Switched the like/save controls from a dynamic aria-label ternary to a static aria-label plus reactive [color]/icon state, after confirming against @ionic/core's own source that ion-button's Stencil componentWillLoad snapshots aria-* attributes off the host exactly once (by design, per its own doc comment) and never re-reads them — a dynamic aria-label would have silently frozen at whatever it was on first render, which is worse for screen-reader users than a static-but-honest label."
  - "Seeded FeedActionRailComponent's likeCount signal in ngOnInit rather than a field initializer, since a required signal input is not guaranteed resolved at constructor-run time when a component is created via TestBed.createComponent + componentRef.setInput (NG0950) rather than a template binding."
  - "Backfilled catchError-path unit tests for the six TripService methods added in Task 2, closing a coverage gap introduced by this plan's own new code (unrelated to the pre-existing project-wide function-coverage shortfall documented below)."

patterns-established:
  - "Save/bookmark join-table mirror of trip_likes, minus the count column — the template for any future no-count toggle relationship in this codebase"

requirements-completed: [SOCIAL-02, SOCIAL-03, SOCIAL-04]

coverage:
  - id: D1
    description: "A user can like, save and clone a trip from the on-card action rail without leaving the full-screen feed (D-04)"
    requirement: "SOCIAL-02"
    verification:
      - kind: unit
        ref: "frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts#tapping like calls TripService.likeTrip and flips to active state; tapping again unlikes"
        status: pass
      - kind: unit
        ref: "frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts#tapping save calls TripService.saveTrip and flips to saved; tapping again unsaves"
        status: pass
      - kind: unit
        ref: "frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts#tapping clone calls TripService.cloneTrip and navigates to the returned trip edit route on success"
        status: pass
    human_judgment: true
    rationale: "Unit specs assert component/service wiring but cannot exercise real touch gestures on a live device to confirm the rail never gets misread as a swipe by the surrounding Swiper gesture surfaces (06-02's nested-swiper concern) — recorded as a phase-level human check, not a claim of this plan alone."
  - id: D2
    description: "Saving an already-saved trip is a no-op that still returns success — no duplicate row, no error"
    requirement: "SOCIAL-04"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripSaveServiceIT.java#saveTrip_calledTwice_doesNotInsertDuplicateRow"
        status: pass
    human_judgment: false
  - id: D3
    description: "Saving, unsaving or liking a PRIVATE trip owned by someone else returns 404, not 403 (SCRUM-274)"
    requirement: "SOCIAL-04"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripSaveServiceIT.java#saveTrip_foreignPrivateTrip_throwsResourceNotFound"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripSaveServiceIT.java#unsaveTrip_foreignPrivateTrip_throwsResourceNotFound"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/TripSaveControllerIT.java#saveTrip_privateTripOtherUser_returns404"
        status: pass
    human_judgment: false
  - id: D4
    description: "A user can retrieve their own saved-trips list; it contains exactly the trips they saved and none saved by anyone else"
    requirement: "SOCIAL-04"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/service/TripSaveServiceIT.java#listSaved_tripSavedByUserA_isAbsentFromUserBsList"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/TripSaveControllerIT.java#listSavedTrips_resolvesToSavedListHandler_notSwallowedByIdTemplate"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/TripSaveControllerIT.java#listSavedTrips_noAuth_returns401"
        status: pass
    human_judgment: false
  - id: D5
    description: "Cloning from the rail creates a new PRIVATE trip owned by the actor and navigates them to it"
    requirement: "SOCIAL-03"
    verification:
      - kind: unit
        ref: "frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.spec.ts#tapping clone calls TripService.cloneTrip and navigates to the returned trip edit route on success"
        status: pass
    human_judgment: false

duration: ~90min
completed: 2026-08-31
status: complete
---

# Phase 06 Plan 03: Save/Bookmark Backend and On-Card Action Rail Summary

**`trip_saves` join table mirroring `trip_likes` (SOCIAL-04), a paged `GET /api/trips/saved` list, and a three-button like/save/clone action rail wired into every feed card (D-04) with optimistic UI and revert-on-failure.**

## Performance

- **Duration:** ~90 min
- **Tasks:** 3 (1 tracer, 2 auto)
- **Files modified:** 17 (11 created, 6 modified)

## Accomplishments
- `V14__create_trip_saves.sql`, `SavedTrip`/`SavedTripId` entities, and `SavedTripRepository` — an exact structural mirror of `TripLike`/`TripLikeId`/`TripLikeRepository`, minus the denormalized count column
- `TripSaveService.saveTrip`/`unsaveTrip`, gated by `TripOwnershipService.loadVisibleTripLite` as their first statement (the 404-not-403 SCRUM-274 convention), backed by atomic `INSERT ... ON CONFLICT DO NOTHING` / JPQL bulk delete — idempotency is a database property, not a Java race
- `POST`/`DELETE /api/trips/{id}/save` and `GET /api/trips/saved` on `TripController`, the list handler declared above `GET /{id}` and pinned by a dedicated `TripSaveControllerIT` MockMvc suite (routing + 401 coverage a repository-slice test cannot provide)
- `TripSaveServiceIT` (9 tests) covering idempotent save/unsave, cross-user list isolation, foreign-PRIVATE 404s, nonexistent-trip 404, and owner-can-save-own-private-trip
- Angular `TripService`: `likeTrip`/`unlikeTrip`/`saveTrip`/`unsaveTrip`/`cloneTrip`/`listSavedTrips`, all through the existing `handleError` contract, each with a success-path and error-path spec
- `FeedActionRailComponent`: standalone, `OnPush`, three `ion-button` controls (like/save/clone) with optimistic state, single `busy` signal collapsing rapid double-taps into one outstanding request, revert-plus-toast on failure, clone-only navigation to the new trip's edit route
- `FeedCardComponent` now renders `<app-feed-action-rail>` in the region 06-02 reserved

## Task Commits

Each task was committed atomically:

1. **Task 1: [BLOCKING] Save/bookmark vertical slice — migration through endpoint, mirroring trip_likes** - `b6a596d` (feat)
2. **Task 2: Saved-trips list endpoint and the Angular action-method seam** - `ee832aa` (feat)
3. **Task 3: On-card action rail (D-04) — like, save and clone without leaving the feed** - `b7e00d0` (feat)

_Task 1 is `type="tracer"` — its `<verify>` (`./mvnw -B verify -Pci -Dit.test=TripSaveServiceIT`) passed (8/8 tests) before Task 2 began, per the tracer feedback gate._

## Files Created/Modified
- `backend/src/main/resources/db/migration/V14__create_trip_saves.sql` - `trip_saves` table, composite PK, `idx_trip_saves_trip_id`
- `backend/src/main/java/com/tripflow/backend/domain/SavedTrip.java` / `SavedTripId.java` - mapping-only entity + embeddable composite PK
- `backend/src/main/java/com/tripflow/backend/repository/SavedTripRepository.java` - `insertIfAbsent`, `deleteByUserIdAndTripId`, `findSavedTripsByUserId`
- `backend/src/main/java/com/tripflow/backend/service/TripSaveService.java` - `saveTrip`, `unsaveTrip`, `listSaved`
- `backend/src/main/java/com/tripflow/backend/controller/TripController.java` - `saveTrip`/`unsaveTrip`/`listSavedTrips` handlers, `TripSaveService` wired in
- `backend/src/test/java/com/tripflow/backend/controller/TripControllerRateLimitTest.java` - updated manual constructor call for the new `TripSaveService` param
- `backend/src/test/java/com/tripflow/backend/service/TripSaveServiceIT.java` - 9 service-layer integration tests
- `backend/src/test/java/com/tripflow/backend/controller/TripSaveControllerIT.java` - 6 MockMvc end-to-end tests (routing, 401, cross-user isolation)
- `frontend/src/app/core/services/trip.service.ts` - six new feed-action methods
- `frontend/src/app/core/services/trip.service.spec.ts` - success + error-path spec per new method
- `frontend/src/app/pages/feed/components/feed-action-rail/*` - new standalone `FeedActionRailComponent` (ts/html/scss/spec)
- `frontend/src/app/pages/feed/components/feed-card/feed-card.component.{ts,html}` - renders the action rail in the reserved region

## Decisions Made
- **Task 2:** Added `TripSaveControllerIT` (full `@SpringBootTest` + `MockMvc`, mirroring `TripLikeControllerIT`) instead of trying to prove HTTP-routing and 401 behavior inside the `@DataJpaTest`-based `TripSaveServiceIT` the plan named for that purpose — a `@DataJpaTest` never loads Spring MVC or `SecurityConfig`'s auth filter, so neither behavior is reachable from that test class. `TripSaveServiceIT` still carries the cross-user-list-isolation test at the service layer, since that one *is* reachable there.
- **Task 3:** Switched from a `liked()`/`saved()`-dependent `aria-label` ternary to a static label plus reactive `[color]`/icon binding, after tracing the actual failure through `@ionic/core`'s compiled source: `ion-button`'s Stencil `componentWillLoad` calls `inheritAriaAttributes`, which snapshots every `aria-*` attribute off the host **exactly once** and moves it into the shadow-DOM native button — by its own doc comment, "this does not need to be reactive as changing attributes on the host element does not trigger a re-render." A dynamic aria-label (or `aria-pressed`) would have silently frozen at its initial value forever, which is a worse accessibility outcome than an honest static label; the visual state (icon + `[color]`) is still fully reactive since those are ordinary `@Input` properties, not attribute-inherited.
- **Task 3:** `likeCount` signal is seeded in `ngOnInit`, not a field initializer, because a required signal input (`input.required<FeedTrip>()`) is only guaranteed resolved by the component's first lifecycle hook — reading it in a field initializer throws `NG0950` when the component is constructed via `TestBed.createComponent` + `componentRef.setInput` (as `feed-action-rail.component.spec.ts` does) rather than a template binding.
- **Task 3:** Backfilled `catchError`-path tests for the six `TripService` methods added in Task 2 and a failed-save revert test for the rail, closing coverage gaps this plan's own new code introduced.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated `TripControllerRateLimitTest`'s manual constructor call**
- **Found during:** Task 1
- **Issue:** `TripController` uses `@AllArgsConstructor`; adding the `TripSaveService` field shifted the constructor's positional argument list, breaking the test's `new TripController(...)` call.
- **Fix:** Added a `TripSaveService` mock and passed it in the correct constructor position.
- **Files modified:** `backend/src/test/java/com/tripflow/backend/controller/TripControllerRateLimitTest.java`
- **Verification:** `TripControllerRateLimitTest` passes as part of the full `mvn verify -Pci` run.
- **Committed in:** `b6a596d` (Task 1 commit)

**2. [Rule 2 - Missing Critical] Added `TripSaveControllerIT` for HTTP-routing and 401 coverage**
- **Found during:** Task 2
- **Issue:** The plan's acceptance criteria required proving that `GET /api/trips/saved` resolves to the literal-segment handler (not the `{id}` template) and that the endpoint returns 401 unauthenticated — both HTTP/security-filter concerns that `TripSaveServiceIT` (a `@DataJpaTest`) structurally cannot exercise.
- **Fix:** Added a new `TripSaveControllerIT` mirroring the existing `TripLikeControllerIT`'s `@SpringBootTest` + `MockMvc` harness.
- **Files modified:** `backend/src/test/java/com/tripflow/backend/controller/TripSaveControllerIT.java` (new)
- **Verification:** All 6 tests pass.
- **Committed in:** `ee832aa` (Task 2 commit)

**3. [Rule 1 - Bug] Static aria-label instead of a state-dependent ternary on `ion-button`**
- **Found during:** Task 3
- **Issue:** The originally planned `[attr.aria-label]="liked() ? 'Unlike trip' : 'Like trip'"` compiled and rendered correctly on first paint, but traced against `@ionic/core`'s actual Stencil source, `ion-button` snapshots `aria-*` attributes off its host exactly once at mount and never re-reads them — any later change would have silently frozen the accessible name at its initial value, misleading screen-reader users about the button's actual state after the first toggle.
- **Fix:** Static `aria-label` per control; active/inactive state communicated via the already-reactive `[color]` and icon-name bindings instead.
- **Files modified:** `frontend/src/app/pages/feed/components/feed-action-rail/feed-action-rail.component.html`
- **Verification:** `feed-action-rail.component.spec.ts` passes (10/10); manually confirmed via the compiled `@ionic/core` source (`inheritAriaAttributes`/`inheritAttributes` in `node_modules/@ionic/core/dist/esm/helpers-*.js` and `ion-button_2.entry.js`).
- **Committed in:** `b7e00d0` (Task 3 commit)

---

**Total deviations:** 3 auto-fixed (1 blocking constructor fix, 1 missing-critical test coverage, 1 bug fix for a real a11y correctness issue found while implementing)
**Impact on plan:** All three were necessary for correctness/completeness of this plan's own deliverables. No scope creep — no other files or features were touched.

## Issues Encountered
- Frontend `node_modules` was absent in this worktree; `npm ci` was run before any test/lint command could execute (routine worktree setup, not a plan deviation).
- `npm run test:ci` (full suite with coverage) reports function coverage at 88.64%, below the project's 90% Karma threshold — but this shortfall is **pre-existing and unrelated to this plan**: `app.routes.ts` (0/16, lazy route loaders never invoked in unit tests), `stop-photo.service.ts`, `dashboard.page.ts`, `trip-edit.page.ts`, `trip-view.page.ts`, and `testing/a11y.ts` all sit below 100% function coverage independent of anything this plan touched, and together their gap alone exceeds the shortfall. This plan's own new files (`feed-action-rail.component.ts` at 90.9%, `trip.service.ts` at 74.3% before this plan's own backfill, improved by the error-path tests added in Task 3) are not the cause. Per the deviation rules' scope boundary, out-of-scope pre-existing gaps were not fixed — logged here for phase-level visibility.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- 06-04 (trip ratings) and 06-05 (profile/interests) can proceed independently; neither depends on this plan's artifacts beyond the already-shared `TripOwnershipService`/`TripController` patterns.
- 06-06 (documentation) owns writing the `docs/api-contracts.md` entries for `POST`/`DELETE /api/trips/{id}/save` and `GET /api/trips/saved` — not done in this plan per the plan's own `<verification>` note.
- **Known limitation (recorded per the plan's `<output>` instruction):** `FeedActionRailComponent`'s `liked`/`saved` signals start `false` every time a card mounts and reflect only actions taken in the current session — `FeedTripResponse` carries no per-viewer `likedByViewer`/`savedByViewer` membership flag. A later phase adding those fields to the feed response should wire them into this component's initial state (alongside the `likeCount` seed already done in `ngOnInit`).
- Pre-existing frontend coverage gate shortfall (see Issues Encountered) is unaddressed and orthogonal to this plan; flagging for whichever phase/plan next touches `app.routes.ts`, `dashboard.page.ts`, `trip-edit.page.ts`, `trip-view.page.ts`, `stop-photo.service.ts`, or `testing/a11y.ts`.

---
*Phase: 06-community-social*
*Completed: 2026-08-31*

## Self-Check: PASSED

All 11 claimed created files verified present. All 3 claimed commit hashes (`b6a596d`, `ee832aa`, `b7e00d0`) verified present in `git log --oneline --all`.
