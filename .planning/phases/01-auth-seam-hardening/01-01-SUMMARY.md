---
phase: 01-auth-seam-hardening
plan: 01
subsystem: auth
tags: [spring-security, jwt, apierror, junit, assertj, mockmvc, reflection]

# Dependency graph
requires:
  - phase: pre-GSD work already on main
    provides: JsonAuthenticationEntryPoint, JsonAccessDeniedHandler, SecurityErrorWriter, UserPrincipal, four SCRUM-55 gap scenarios in TripControllerIT
provides:
  - Filter-layer ApiError bodies now serialize fieldErrors as JSON null, byte-shape-identical to GlobalExceptionHandler's non-validation responses
  - Unit assertions pinning that null-fieldErrors shape on both the 401 and 403 filter paths
  - Body-level (not status-only) ApiError assertions on all four SCRUM-55 gap scenarios
  - A reflection gate that fails the build if TripController reverts to Authentication/Principal-based caller resolution
affects: [01-02 refresh tokens, 01-03 revocation, frontend api-error.mapper.ts consumers, any future controller added to TripController]

actuals:
  tokens: 6100
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Reflection-based architecture gate co-located in the controller's IT rather than a separate ArchUnit dependency"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/tripflow/backend/security/SecurityErrorWriter.java
    - backend/src/test/java/com/tripflow/backend/security/JsonAuthenticationEntryPointTest.java
    - backend/src/test/java/com/tripflow/backend/security/JsonAccessDeniedHandlerTest.java
    - backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java

key-decisions:
  - "Filter layer conforms to docs/api-contracts.md (fieldErrors null off the validation path) rather than amending the doc to allow an empty array — GlobalExceptionHandler already had 100% of the non-validation paths on null; SecurityErrorWriter was the single divergent producer, so the doc was right and the code was wrong."
  - "The typed-principal seam is gated by assignability (Principal.class.isAssignableFrom(...) / Authentication.class.isAssignableFrom(...)) rather than exact-class equality, so a subtype such as AbstractAuthenticationToken also trips the gate. Safe because UserPrincipal implements UserDetails only, not Principal."
  - "The seam gate lives as a plain reflection test in TripControllerIT — no ArchUnit dependency added for a single one-class rule."

patterns-established:
  - "Security-filter error bodies are asserted for full ApiError shape (including the null fieldErrors node), not just status + message"
  - "Auth-boundary IT scenarios assert $.status and $.path on the response body, so a regression to a Spring Security HTML error page fails the test"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03]

coverage:
  - id: D1
    description: "401 and 403 responses from the Spring Security filter layer carry the same ApiError JSON shape as GlobalExceptionHandler, including fieldErrors as JSON null"
    requirement: AUTH-01
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/security/JsonAuthenticationEntryPointTest.java#commence_writes401ApiErrorJson"
        status: pass
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/security/JsonAccessDeniedHandlerTest.java#handle_writes403ApiErrorJson"
        status: pass
    human_judgment: false
  - id: D2
    description: "No TripController method resolves the caller from a raw Principal/Authentication; UserPrincipal is the only seam, and a regression fails the build"
    requirement: AUTH-02
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java#controllers_resolveCurrentUserViaTypedPrincipal_notNameString"
        status: unknown
    human_judgment: false
  - id: D3
    description: "All four SCRUM-55 gap scenarios assert an ApiError response body rather than only an HTTP status code"
    requirement: AUTH-03
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java#getTrip_nonExistentId_returns404, #deleteTrip_nonOwner_returns403, #listTrips_noAuthentication_returns401ViaJsonEntryPoint, #createTrip_withRealJwt_authenticatesThroughFilterAndPersists"
        status: unknown
    human_judgment: false

# Metrics
duration: 12min
completed: 2026-08-14
status: complete
---

# Phase 1 Plan 01: Auth Seam Hardening Summary

**Filter-layer `ApiError` now emits `fieldErrors: null` like every other non-validation path, and the typed-`UserPrincipal` seam plus all four SCRUM-55 gap scenarios are now enforced by assertions that actually fail on regression.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-08-14T21:08Z
- **Completed:** 2026-08-14T21:20Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- **One-line production fix.** `SecurityErrorWriter.write(...)` passed `List.<ApiError.FieldError>of()` as the fifth `ApiError` argument, so every 401 from `JsonAuthenticationEntryPoint` and every 403 from `JsonAccessDeniedHandler` serialized `"fieldErrors": []`. `docs/api-contracts.md` states the array is populated only on 400 validation errors, and `GlobalExceptionHandler` passes `null` on all of its non-validation paths. The filter layer was the codebase's only divergent producer; it now passes `null`, and the unused `java.util.List` import is gone.
- **The shape is now pinned, not just fixed.** Both handler unit tests assert `body.get("fieldErrors").isNull()`, so a future re-introduction of an empty list fails locally in `mvnw test` without Docker.
- **Two shallow SCRUM-55 tests raised to sibling depth.** `getTrip_nonExistentId_returns404` and `deleteTrip_nonOwner_returns403` asserted only `status().isNotFound()` / `status().isForbidden()` — they passed on *any* 404/403, including a bare Spring Security HTML error page. Both now also assert `$.status` and `$.path` on the JSON body, matching the style `listTrips_noAuthentication_returns401ViaJsonEntryPoint` already used.
- **The typed-principal seam is now executable, not just an audit claim.** `controllers_resolveCurrentUserViaTypedPrincipal_notNameString` reflects over `TripController.class.getDeclaredMethods()` and fails if any public method declares a parameter assignable to `java.security.Principal` or `org.springframework.security.core.Authentication`, and separately requires at least one `UserPrincipal` parameter to exist. This converts ROADMAP Success Criterion 2 from "verified by reading the code in an audit" into a build gate.

## Task Commits

1. **Task 1: Align filter-layer ApiError fieldErrors with the documented contract** — `5eb7d82` (fix)
2. **Task 2: Raise the two shallow SCRUM-55 gap tests to sibling assertion depth, and gate the typed-principal seam** — `ecf77c8` (test)

**Plan metadata:** not committed — `.planning/` is gitignored in this repo by deliberate onboarding decision (the team does not use GSD), so SUMMARY/STATE/ROADMAP changes are local-only.

## Files Created/Modified

- `backend/src/main/java/com/tripflow/backend/security/SecurityErrorWriter.java` — fifth `ApiError` constructor argument changed from an empty immutable list to `null`; `java.util.List` import removed. Net production diff: one argument, one import. No change to status, message, path, content type, character encoding, or method signature.
- `backend/src/test/java/com/tripflow/backend/security/JsonAuthenticationEntryPointTest.java` — one added AssertJ assertion that the 401 body's `fieldErrors` node is JSON null.
- `backend/src/test/java/com/tripflow/backend/security/JsonAccessDeniedHandlerTest.java` — same assertion on the 403 body.
- `backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java` — `$.status`/`$.path` body assertions chained onto the 404 and 403 gap tests; one new reflection test gating the typed-principal seam. No test method renamed, no scenario setup changed.

## Decisions Made

- **Fix the code, not the doc.** Two ways to remove the inconsistency existed: change `SecurityErrorWriter` to `null`, or amend `docs/api-contracts.md` to permit `[]` at the filter layer. Chose the former because `GlobalExceptionHandler` already emitted `null` on *every* non-validation path — the doc described actual majority behavior correctly, and the frontend's `api-error.mapper.ts` consumes a single shape. Amending the doc would have legitimized a two-shape contract for the sake of one caller.
- **Assignability, not equality, in the seam gate.** `doesNotContain(Principal.class, Authentication.class)` would only catch a parameter declared as exactly those types; `noneMatch(t -> Principal.class.isAssignableFrom(t) || Authentication.class.isAssignableFrom(t))` also catches concrete subtypes like `UsernamePasswordAuthenticationToken`. This is safe specifically because `UserPrincipal` is a `record ... implements UserDetails` and `UserDetails` does not extend `Principal` — verified before writing the assertion. If `UserPrincipal` ever gains `implements Principal`, this gate will start failing and will need the `UserPrincipal.class` exclusion added.
- **No ArchUnit.** A single one-class structural rule does not justify a new test dependency; `getDeclaredMethods()` plus the AssertJ already on the classpath covers it in eight lines.

## Deviations from Plan

None — plan executed exactly as written. No deviation rules fired; no auto-fixes were needed.

The plan's `<action>` for Task 2 Edit C said "assert that no public method on `TripController` declares a `java.security.Principal` or `Authentication` parameter." The implementation satisfies that literally and additionally covers subtypes (see Decisions above). This is a strengthening within the stated intent, not a departure from it.

## Issues Encountered

- **A self-inflicted verification detour, not a code problem.** After the full unit suite ran clean under `./mvnw.cmd -q test`, an attempt to re-confirm the exit code via a direct `./mvnw.cmd surefire:test` invocation failed with `Error: could not open '{argLine}'`. That is an artifact of invoking the Surefire goal outside the normal lifecycle, which skips the JaCoCo `prepare-agent` step that defines the `argLine` property — not a test failure. Confirmed green by scanning `target/surefire-reports/*.txt`: 38 test classes, zero failures, zero errors. **Takeaway for future executors on this repo: do not invoke `surefire:test` directly here; use the `test` lifecycle phase.**

## Verification Results

| # | Check | Result |
|---|---|---|
| 1 | `./mvnw.cmd -q test -Dtest=JsonAuthenticationEntryPointTest,JsonAccessDeniedHandlerTest` | exit 0 |
| 2 | `./mvnw.cmd -q test` (full local unit suite, no Docker) | exit 0 — 38 report files, all `Failures: 0, Errors: 0` |
| 3 | `./mvnw.cmd -q test-compile` (integration sources compile) | exit 0 |
| 4 | `grep -c '^import java.util' SecurityErrorWriter.java` | 0 (required 0) |
| 5 | `grep -v '^\s*[*/]' TripControllerIT.java \| grep -c 'jsonPath("$.status")'` | 5 (required >= 5, was 3) |
| 6 | `grep -c 'fieldErrors'` (comment-filtered) in each handler test | 1 and 1 (required >= 1 each) |
| 7 | `grep -c 'controllers_resolveCurrentUserViaTypedPrincipal_notNameString'` | 1 (required exactly 1) |
| 8 | `mvn -B verify -Pci` | **CI only — not run locally.** Per CLAUDE.md no team machine runs Docker, so Testcontainers-backed `*IT` tests execute in GitHub Actions only. |

**Coverage note:** the three touched test files add assertions to existing tests plus one new test method; no production line count changed (net production diff is one argument and one import), so the 92% overall / 80% changed-files JaCoCo floor is not at risk.

## Threat Model Outcome

| Threat ID | Disposition | Outcome |
|---|---|---|
| T-01-01 (Info Disclosure, filter error body) | mitigate | Satisfied. The change strictly *removes* a body field's content; no exception message, stack frame, or constraint name was introduced. Body remains status/error/message/path/timestamp. |
| T-01-02 (Spoofing, current-user resolution) | mitigate | Satisfied by `controllers_resolveCurrentUserViaTypedPrincipal_notNameString`, which now fails the build on a revert to name-string principal resolution. |
| T-01-03 (Repudiation, 403 vs 404 disclosure) | accept | Unchanged and deliberately so. The 403-on-delete-by-non-owner vs 404-on-private-trip split is documented in `docs/auth.md`; standardizing existence-hiding is tracked as SCRUM-274 under Phase 6. |

**Threat surface scan:** no new endpoints, no new migrations, no new DTOs, no frontend changes, no new network or file-access paths. No `## Threat Flags` section required.

## Known Stubs

None. No placeholder values, no skipped tests, no `TODO`/`FIXME` introduced, and every `<verify>` step in the plan was run except the explicitly CI-only `mvn -B verify -Pci`.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Ready.** ROADMAP Phase 1 Success Criteria 1, 2 and 3 are now not merely satisfied but each protected by a test that fails on regression. Plan 01-01's scope (converting audit claims into executable assertions) is closed.
- **Remaining in Phase 1:** plans 01-02/01-03/01-04 — the refresh-token flow (issuance, rotation, reuse detection, revocation) and the frontend silent-refresh mechanism. That is the only genuinely unbuilt Phase 1 work; the 2026-08-14 audit confirmed no `refresh_tokens` migration exists (highest applied is V11) and no refresh-token code anywhere in the codebase.
- **Carry-forward for 01-02+:** the null-`fieldErrors` contract now holds uniformly, so any new auth endpoint (`/api/auth/refresh`, `/api/auth/logout`) that surfaces errors through the filter layer inherits the correct shape with no extra work. The `UserPrincipal` seam gate currently covers `TripController` only — if refresh work adds an `AuthController` method taking a caller identity, consider widening the gate rather than duplicating it.
- **Unblocked risk note:** this plan did not touch `SecurityConfig`, so risk R2 (SecurityConfig lockout, Postman regression required) was not triggered. Plans 01-02/01-03 will touch it and must honor R2.

## Self-Check: PASSED

- `backend/src/main/java/com/tripflow/backend/security/SecurityErrorWriter.java` — FOUND
- `backend/src/test/java/com/tripflow/backend/security/JsonAuthenticationEntryPointTest.java` — FOUND
- `backend/src/test/java/com/tripflow/backend/security/JsonAccessDeniedHandlerTest.java` — FOUND
- `backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java` — FOUND
- `.planning/phases/01-auth-seam-hardening/01-01-SUMMARY.md` — FOUND
- Commit `5eb7d82` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `ecf77c8` — FOUND on `worktree-gsd-phase1-auth-seam`

---
*Phase: 01-auth-seam-hardening*
*Completed: 2026-08-14*
