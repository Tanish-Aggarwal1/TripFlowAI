---
phase: 06-community-social
plan: 05
subsystem: api
tags: [spring-boot, jpa, postgres-array, angular, standalone-components, profile]

requires:
  - phase: 06-02
    provides: "app.routes.ts's lazy-loadComponent + canActivate: [authGuard] route entry convention this plan extends"
provides:
  - "GET /api/profile and PATCH /api/profile/interests, principal-scoped only (no path/query user-id parameter exists on the controller)"
  - "users.interests TEXT[] column on User, same @JdbcTypeCode(SqlTypes.ARRAY)/columnDefinition pair as Trip.tags, so 06-06's feed-ranking overlap query needs no further schema change"
  - "/profile route: username, join date, and editable interest chips, gated by authGuard"
affects: [06-06]

actuals:
  tokens: 9600
  tasks: 4
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Free-text interests TEXT[] mirrors Trip.tags exactly (same Hibernate annotation pair, same 20-element/50-character Bean Validation limits from docs/api-contracts.md) rather than a fixed taxonomy — case/typo-sensitive matching is an accepted, documented consequence 06-06 inherits"
    - "PATCH on a sub-path (/api/profile/interests), not a whole-profile PUT, keeps username/joinedAt structurally unwritable through the API rather than merely unvalidated"
    - "Reused the existing InterestChipsComponent (shared by ai-trip-prompt/ai-preferences-form) for profile-interest editing by adding a maxInterests @Input (default unchanged at 10), rather than duplicating a near-identical chip-add/remove component"

key-files:
  created:
    - backend/src/main/resources/db/migration/V15__add_user_interests.sql
    - backend/src/main/java/com/tripflow/backend/dto/ProfileResponse.java
    - backend/src/main/java/com/tripflow/backend/dto/UpdateInterestsRequest.java
    - backend/src/main/java/com/tripflow/backend/service/ProfileService.java
    - backend/src/main/java/com/tripflow/backend/controller/ProfileController.java
    - backend/src/test/java/com/tripflow/backend/service/ProfileServiceTest.java
    - backend/src/test/java/com/tripflow/backend/controller/ProfileControllerIT.java
    - frontend/src/app/core/models/profile.model.ts
    - frontend/src/app/core/services/profile.service.ts
    - frontend/src/app/core/services/profile.service.spec.ts
    - frontend/src/app/pages/profile/profile.page.ts
    - frontend/src/app/pages/profile/profile.page.html
    - frontend/src/app/pages/profile/profile.page.scss
    - frontend/src/app/pages/profile/profile.page.spec.ts
  modified:
    - backend/src/main/java/com/tripflow/backend/domain/User.java
    - frontend/src/app/app.routes.ts
    - frontend/src/app/pages/trips/components/interest-chips/interest-chips.component.ts
    - frontend/src/app/pages/trips/components/interest-chips/interest-chips.component.spec.ts

key-decisions:
  - "Task 1 checkpoint (checkpoint:decision, one-way-ish per D-07, NOT gate=blocking-human): auto-selected option (a) — free-text TEXT[] mirroring Trip.tags exactly, 20 x 50 limits reused from docs/api-contracts.md — per the orchestrator's explicit checkpoint_guidance, which cites CONTEXT.md D-07's own delegation of this exact choice to research/planning and 06-RESEARCH.md/06-PATTERNS.md's already-completed recommendation. Rationale: Trip.tags (the other half of the D-05 overlap match) is itself unconstrained free text, so a fixed taxonomy on only one side of the comparison buys typo-safety that the other side immediately erodes; case/typo-sensitive matching is an accepted, documented consequence for 06-06."
  - "Task 4: reused the existing InterestChipsComponent (already shared by ai-trip-prompt.component and ai-preferences-form.component) for the profile page's chip add/remove editor instead of writing a new one, adding a maxInterests @Input (default 10, unchanged for existing callers) so the profile page can pass 20 — the backend limit this plan's own DTO enforces. Existing InterestChipsComponent spec coverage (11 specs) required no changes beyond the new default-preserving @Input; one new spec covers the override."
  - "Task 4: on a rejected PATCH (400), the draft-interests signal reverts to the last-known-good profile().interests rather than leaving the failed attempted array rendered — matches the plan's behavior spec ('leaves the previously-saved interests rendered') and avoids showing an invalid draft as if it had persisted."

requirements-completed: [SOCIAL-05]

coverage:
  - id: D1
    description: "A signed-in user can open a profile page showing their username, join date, and stored interests (D-07)"
    requirement: "SOCIAL-05"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/ProfileControllerIT.java#getProfile_authenticatedUser_returnsUsernameJoinDateAndEmptyInterests"
        status: pass
      - kind: unit
        ref: "frontend/src/app/pages/profile/profile.page.spec.ts#loads the profile on init and renders username, join date and interests"
        status: pass
    human_judgment: false
  - id: D2
    description: "A user can edit and persist their interests, and reloading the page shows the saved values"
    requirement: "SOCIAL-05"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/ProfileControllerIT.java#updateInterests_validArray_replacesWholesaleAndReturnsUpdatedProfile"
        status: pass
      - kind: unit
        ref: "frontend/src/app/pages/profile/profile.page.spec.ts#save() issues PATCH with the full resulting array after an add and updates from the response"
        status: pass
    human_judgment: false
  - id: D3
    description: "An interests payload with more than 20 entries, or any entry longer than 50 characters, is rejected with 400 carrying fieldErrors"
    requirement: "SOCIAL-05"
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/ProfileControllerIT.java#updateInterests_moreThan20Elements_returns400WithFieldError, #updateInterests_elementOver50Chars_returns400"
        status: pass
    human_judgment: false
  - id: D4
    description: "Profile reads and writes always resolve the caller from the authenticated principal — no request-supplied user id can retarget them"
    requirement: "SOCIAL-05"
    verification:
      - kind: unit
        ref: "grep -cE '@PathVariable|@RequestParam' ProfileController.java == 0"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/ProfileControllerIT.java#getProfile_twoDistinctUsers_eachReceivesOwnProfile, #updateInterests_cannotRetargetAnotherUser"
        status: pass
    human_judgment: false
  - id: D5
    description: "The stored interests column is queryable by Postgres array overlap for plan 06-06's ranking, with no schema change needed"
    requirement: "SOCIAL-05"
    verification:
      - kind: unit
        ref: "grep -c 'JdbcTypeCode' User.java >= 1; ./mvnw verify -Pci exits 0 (proves ddl-auto=validate accepts the TEXT[] mapping)"
        status: pass
    human_judgment: false

duration: ~50min, 2026-08-31 ~16:15-17:20 EDT
completed: 2026-08-31
status: complete
---

# Phase 06 Plan 05: User Profile — Username, Join Date, Editable Interests Summary

**`GET`/`PATCH /api/profile` backed by a new `users.interests TEXT[]` column mirroring `Trip.tags` exactly, plus a `/profile` page reusing the existing `InterestChipsComponent` for chip-based interest editing.**

## Performance

- **Duration:** ~50 min (Tasks 2-4 executed this session; Task 1 was a checkpoint auto-selected per orchestrator guidance)
- **Tasks:** 4 (1 checkpoint:decision auto-selected, 3 executed — Task 2 tracer, Tasks 3-4 expansion)
- **Files modified:** 18 (14 created, 4 modified)

## Accomplishments
- `V15__add_user_interests.sql`: `users.interests TEXT[] NOT NULL DEFAULT '{}'`, backfilling existing rows in the same statement
- `User.interests` mapped with the identical `@JdbcTypeCode(SqlTypes.ARRAY)` / `columnDefinition = "TEXT[]"` pair `Trip.tags` uses, initialized to an empty list so a fresh `User` never carries null
- `GET /api/profile`: username, `joinedAt` (from `BaseEntity.createdAt`), and interests for the authenticated principal only — no `@PathVariable`/`@RequestParam` anywhere on `ProfileController`
- `PATCH /api/profile/interests`: replace-wholesale update bounded to 20 elements x 50 characters (reusing `Trip.tags`'s already-documented `docs/api-contracts.md` limits, not new numbers), rejecting `null`/oversized/too-long payloads with the project's standard `fieldErrors` shape
- `ProfileService.updateInterests` logs the user id and resulting element count only, never the interest strings (matches `docs/LOGGING_STANDARD.md`)
- `/profile` page: loads the profile on init, renders username/join date/interests, and edits interests through the existing `InterestChipsComponent` (given a new `maxInterests` `@Input`, default unchanged at 10, passed 20 here) with an explicit "Save interests" control that sends the full draft array
- A rejected save (400) surfaces the backend's field-error message and reverts the rendered interests to the last-saved value, never leaving an unsaved invalid draft displayed as if it stuck

## Task Commits

Each task was committed atomically:

1. **Task 1: [BLOCKING] Decision gate — interests column shape** — checkpoint auto-selected per orchestrator `checkpoint_guidance` (option a, free-text `TEXT[]`); no separate commit, folded into Task 2
2. **Task 2 (tracer): Profile read vertical slice — migration through GET /api/profile** — `1481652` (feat)
3. **Task 3: Interests update endpoint with bounded validation** — `418c866` (feat)
4. **Task 4: Profile page — username, join date, editable interests** — `d7c2225` (feat)

_Task 2 is `type="tracer"`; its `<verify>` (`./mvnw -B verify -Pci -Dit.test=ProfileControllerIT`) passed (3/3 tests) before Task 3 began, per the tracer feedback gate — the plan's auto-mode was active (`workflow.auto_advance=true`), so the gate ran the automated re-verify path, not a human checkpoint._

## Files Created/Modified
- `backend/src/main/resources/db/migration/V15__add_user_interests.sql` - `ALTER TABLE users ADD COLUMN interests TEXT[] NOT NULL DEFAULT '{}'`
- `backend/src/main/java/com/tripflow/backend/domain/User.java` - added `interests` field with `Trip.tags`'s exact annotation pair
- `backend/src/main/java/com/tripflow/backend/dto/ProfileResponse.java` - `id`/`username`/`joinedAt`/`interests` record
- `backend/src/main/java/com/tripflow/backend/dto/UpdateInterestsRequest.java` - `@NotNull @Size(max=20) List<@Size(max=50) String>`
- `backend/src/main/java/com/tripflow/backend/service/ProfileService.java` - `getProfile`, `updateInterests` (replace-wholesale)
- `backend/src/main/java/com/tripflow/backend/controller/ProfileController.java` - `GET`/`PATCH /interests`, principal-scoped only
- `backend/src/test/java/com/tripflow/backend/service/ProfileServiceTest.java` - 5 mocked-repository unit specs
- `backend/src/test/java/com/tripflow/backend/controller/ProfileControllerIT.java` - 9 Testcontainers IT specs (auth, scoping, validation, wholesale-replace, empty-clear)
- `frontend/src/app/core/models/profile.model.ts` - `Profile`, `UpdateInterestsRequest` interfaces
- `frontend/src/app/core/services/profile.service.ts` - `getProfile()`, `updateInterests()`
- `frontend/src/app/core/services/profile.service.spec.ts` - 5 specs incl. 400/network-error mapping
- `frontend/src/app/pages/profile/profile.page.ts` - `ProfilePage`: load/draft/save signals
- `frontend/src/app/pages/profile/profile.page.html` - username/join-date/chips/empty-state/error rendering
- `frontend/src/app/pages/profile/profile.page.scss` - minimal layout only
- `frontend/src/app/pages/profile/profile.page.spec.ts` - 8 specs incl. authGuard route check
- `frontend/src/app/app.routes.ts` - `profile` route, `canActivate: [authGuard]`
- `frontend/src/app/pages/trips/components/interest-chips/interest-chips.component.ts` - added `maxInterests` `@Input` (default 10)
- `frontend/src/app/pages/trips/components/interest-chips/interest-chips.component.spec.ts` - 1 new spec for the override

## Decisions Made
See `key-decisions` in frontmatter above (Task 1 checkpoint auto-selection rationale, `InterestChipsComponent` reuse, and the revert-on-reject save behavior).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed a self-inflicted acceptance-criteria grep false positive**
- **Found during:** Task 2
- **Issue:** `ProfileController`'s javadoc comment literally contained the strings `@PathVariable`/`@RequestParam` (explaining their absence), which the acceptance criterion's `grep -cE '@PathVariable|@RequestParam'` matched as 2, not the required 0.
- **Fix:** Reworded the javadoc to describe the invariant without naming the annotations literally.
- **Files modified:** `backend/src/main/java/com/tripflow/backend/controller/ProfileController.java`
- **Verification:** `grep -cE '@PathVariable|@RequestParam' ProfileController.java` now returns `0`.
- **Committed in:** `1481652` (Task 2 commit)

**2. [Rule 2 - Missing critical] Added two tests to close a function-coverage gap introduced by this plan's own new files**
- **Found during:** Task 4, running `npm run test:ci`
- **Issue:** `ProfileService.getProfile`'s error-mapping arrow and `ProfilePage.ngOnInit`'s error callback were both unexercised by the initially-written specs, each file individually short of 100% function coverage.
- **Fix:** Added `getProfile network error` (profile.service.spec.ts) and `renders an error and does not throw when getProfile fails` (profile.page.spec.ts).
- **Files modified:** `frontend/src/app/core/services/profile.service.spec.ts`, `frontend/src/app/pages/profile/profile.page.spec.ts`
- **Verification:** Both new files now report 100% function coverage individually (`coverage/app/coverage-summary.json`).
- **Committed in:** `d7c2225` (Task 4 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking grep fix, 1 missing-coverage fix)
**Impact on plan:** Both were self-contained to this plan's own new files; no scope creep into unrelated code.

## Issues Encountered
- `npm run test:ci`'s project-wide function-coverage gate (floor 90%) still fails at 88.88% *after* this plan's files individually reached 100% function coverage. Investigated the arithmetic: subtracting this plan's fully-covered new functions from both numerator and denominator still leaves the baseline at ~88.7%, and sibling plan 06-03's own SUMMARY (WINDOWS.md entry #5) already recorded this exact gate as unmet *before* 06-05 touched anything (`app.routes.ts`'s 16-18 lazy `loadComponent` arrows are structurally never invoked by unit tests, plus several pre-existing gaps in `dashboard.page.ts`/`trip-edit.page.ts`/`trip-view.page.ts`/`stop-photo.service.ts`/`testing/a11y.ts`). This is pre-existing frontend-wide test debt, not something this plan introduced or is in scope to fix (Scope Boundary rule) — logged as WINDOWS.md entry #6. This plan's own gating verify commands (the plan's `<verify>` steps and `npm run lint`) all exit 0.
- Backend `./mvnw -B verify -Pci` and frontend `npm test`/`npm run lint` all required local Docker/`npm ci` setup in this worktree (routine, not a plan deviation): Docker was already available per `CLAUDE.md`; `frontend/node_modules` was absent and `npm ci` (54s) was run before any frontend command could execute.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None. Both endpoints and the `/profile` page are fully wired to live data; no placeholder values or unwired components.

## Threat Flags
None found beyond what the plan's own `<threat_model>` already covers (T-06-05-01 through T-06-05-SC), all of which are mitigated as specified: principal-only identity resolution (no path/query id param), bounded interests payload, structurally-unwritable username/joinedAt, no user-content logging, and Angular's default interpolation escaping for rendered interests.

## Next Phase Readiness
- `users.interests` is queryable via Postgres `&&` array-overlap against `trips.tags` with zero further schema change — 06-06's feed ranking can consume it directly.
- Case/typo-sensitive matching (e.g. "Hiking" vs "hiking") is an accepted, documented consequence of the Task 1 free-text decision; 06-06 inherits this and the cheap follow-up (lowercase-normalize both sides at write time) is noted as a future option, not required now.
- `docs/api-contracts.md` entries for `GET /api/profile` and `PATCH /api/profile/interests` are intentionally NOT added here — per this plan's own `<verification>` note, 06-06's documentation task owns that file for the phase.
- Pre-existing frontend function-coverage gate (WINDOWS.md #5, #6) remains open across the phase; out of scope for any single plan to fix, worth a dedicated coverage-catchup task before phase 06 ships.

---
*Phase: 06-community-social*
*Completed: 2026-08-31*

## Self-Check: PASSED

All 14 claimed created files verified present on disk. All 3 claimed commit hashes (`1481652`, `418c866`, `d7c2225`) verified present in `git log --oneline --all`.
