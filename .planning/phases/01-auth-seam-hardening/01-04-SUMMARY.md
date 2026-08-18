---
phase: 01-auth-seam-hardening
plan: 04
subsystem: auth
tags: [silent-refresh, angular-signals, effects, session-expiry, interceptors, ionic-alert, tdd]

# Dependency graph
requires:
  - phase: 01-02
    provides: POST /api/auth/refresh (200 + rotated httpOnly cookie), X-Requested-With CSRF gate, SameSite=None; Secure cookie
  - phase: 01-03
    provides: POST /api/auth/logout (always 204, clears the cookie), reuse-detection mass revoke, 60/hour refresh rate limit
provides:
  - AuthService.expiresAt — signal of the current access token's ISO expiry, seeded from the stored token at construction
  - AuthService.refresh() — credentialed POST carrying the CSRF header, storing the returned access token
  - AuthService.logout() — server-revoking, with local teardown on both the success and failure paths
  - SessionStateService — status signal (active/refreshing/expired) plus the effect-armed self-rearming refresh timeout and visibilitychange resume check
  - SessionStateService.markExpired() — the seam the 401 interceptor uses instead of logging out
  - sessionExpiryInterceptor — flips session status on a 401 and re-throws; no teardown, no routing
  - AppComponent session-expired banner + first-post-expiry-click AlertController dialog
affects: [any future frontend work that assumes a 401 logs the user out — it no longer does]

actuals:
  tokens: 9200
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "effect() on a signal owned by another service as the one-way arming mechanism for a timer — the dependency never has to point back"
    - "Single self-rearming setTimeout whose re-arm rides on the effect, plus a visibilitychange clock check, rather than setInterval"
    - "Status signal as the only thing a background service publishes; presentation and teardown stay with the component and AuthService"

key-files:
  created:
    - frontend/src/app/core/services/session-state.service.ts
    - frontend/src/app/core/services/session-state.service.spec.ts
  modified:
    - frontend/src/app/core/models/auth.model.ts
    - frontend/src/app/core/services/auth.service.ts
    - frontend/src/app/core/services/auth.service.spec.ts
    - frontend/src/app/core/interceptors/session-expiry.interceptor.ts
    - frontend/src/app/core/interceptors/session-expiry.interceptor.spec.ts
    - frontend/src/app/app.component.ts
    - frontend/src/app/app.component.html
    - frontend/src/app/app.component.spec.ts

key-decisions:
  - "No refresh-credentials interceptor was added, per the plan's simplification note: exactly two calls need credentials and both are issued from AuthService, so a blanket interceptor would only create the Mapbox/Cloudinary preflight failure mode it was meant to avoid."
  - "SessionStateService gained markExpired() beyond the plan's written surface — the plan's task 3 requires the interceptor to set the status to expired, and a readonly status signal offers no way to do that. It clears the timer as well as flipping the status, so a 401 also stops the loop."
  - "AppComponent guards the dialog with a boolean that the banner's own log-in button also sets. Without it the banner click bubbles to the document listener and raises the dialog the plan says a banner user should not see."
  - "Task 3's plan text did not ask for AppComponent specs, but the HostListener/dialog logic is branchy and karma.conf enforces coverage floors that fail test:ci directly — six cases were added rather than shipping the component untested."

requirements-completed: [AUTH-04]

coverage:
  - id: D1
    description: "A refresh call carries the httpOnly cookie and the CSRF header, and stores the new access token without touching the stored user record (D-05 precondition)"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/core/services/auth.service.spec.ts — refresh() request shape, storage, and expiresAt/isAuthenticated cases"
        status: pass
    human_judgment: false
  - id: D2
    description: "A 401 from refresh propagates to the caller without clearing storage or navigating"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/core/services/auth.service.spec.ts — refresh 401 case"
        status: pass
    human_judgment: false
  - id: D3
    description: "Logout posts to the server-side revocation endpoint and still clears local state when that call fails"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/core/services/auth.service.spec.ts — logout success and logout-failure cases"
        status: pass
    human_judgment: false
  - id: D4
    description: "The refresh timer fires roughly a minute before expiry, exactly once, and not early (D-05)"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/core/services/session-state.service.spec.ts#does not refresh early, and refreshes exactly once at the buffer point"
        status: pass
    human_judgment: false
  - id: D5
    description: "The loop sustains — a successful refresh re-arms the timer for the next window"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/core/services/session-state.service.spec.ts#re-arms after a successful refresh"
        status: pass
    human_judgment: false
  - id: D6
    description: "A past expiry fires immediately rather than scheduling a negative delay; a page reload re-arms from the stored token"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "session-state.service.spec.ts#refreshes immediately when the stored expiry is already in the past; auth.service.spec.ts — expiresAt seeded from a stored token at construction"
        status: pass
    human_judgment: false
  - id: D7
    description: "A failed refresh marks the session expired and does not re-arm, so a dead session cannot spin against the 60/hour cap (T-01-25)"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/core/services/session-state.service.spec.ts#marks the session expired on a failed refresh and does not re-arm"
        status: pass
    human_judgment: false
  - id: D8
    description: "A rapid re-login leaves exactly one live timer, and a backgrounded tab catches up on becoming visible"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "session-state.service.spec.ts#leaves exactly one live timer when the schedule is replaced, #refreshes when the tab becomes visible again past the scheduled point, #ignores a visibility change while the token is still comfortably valid"
        status: pass
    human_judgment: false
  - id: D9
    description: "A 401 from a normal API call flips the session status and leaves the route unchanged; login and refresh 401s are left to their own callers (D-06)"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/core/interceptors/session-expiry.interceptor.spec.ts — all six cases, including the explicit no-navigation assertion"
        status: pass
    human_judgment: false
  - id: D10
    description: "The expired session shows an inline banner, and the first post-expiry click raises exactly one dialog that logs out (D-06)"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "frontend/src/app/app.component.spec.ts — banner visibility, single-dialog-under-rapid-clicking, dialog action logs out, banner action does not also raise the dialog"
        status: pass
      - kind: manual
        ref: "01-04-PLAN.md task 3 <human-check> — banner-then-dialog QA with a running backend and a lowered JWT_EXPIRY_MS"
        status: unknown
    human_judgment: true

# Metrics
duration: 25min
completed: 2026-08-17
status: complete
---

# Phase 1 Plan 04: Frontend Silent Refresh Summary

**An open tab now renews its own access token about a minute before each 15-minute expiry and keeps doing so indefinitely; when renewal finally fails the user keeps their page and gets a banner, and the first thing they try to do afterwards is intercepted by a single dialog that logs them out server-side.**

## Performance

- **Duration:** ~25 min across two sessions (the first died mid-task-2 on a usage limit, not an error)
- **Completed:** 2026-08-17
- **Tasks:** 3 (5 commits — tasks 1 and 2 were TDD, so RED and GREEN are separate)
- **Files created:** 2 · **Files modified:** 8

## Accomplishments

- **AUTH-04's user-observable claim is now true.** Everything plans 01-02 and 01-03 shipped was invisible from the browser; the refresh endpoint had no caller. It has one now, and it fires on a schedule rather than after a failure.
- **The refresh is proactive, not reactive (D-05).** The timer is armed by an `effect()` on `AuthService.expiresAt`, so the re-arm after a successful refresh costs nothing: the new expiry re-runs the effect. `AuthService` never imports `SessionStateService`, so there is no injector cycle to unpick later.
- **A single self-rearming timeout, not a polling interval.** Backed up by a `visibilitychange` check that trusts the clock rather than the timer, because a backgrounded tab's timeout can be throttled or fire late — the case a bare `setTimeout` gets wrong on laptop sleep/wake.
- **A dead session cannot spin.** A failed refresh sets `expired` and does not re-arm; there is a spec case that ticks a further 60 minutes and asserts exactly one call. That is the client half of T-01-25, the backend half being 01-03's 60/hour cap.
- **The 401 handler no longer yanks people out of their work (D-06).** The interceptor used to call `logout()` — storage clear plus a `navigate` — on any non-login 401. It now flips a signal and re-throws, and the two-stage banner/dialog experience lives entirely in `AppComponent`.
- **Zero new dependencies and no new interceptor**, exactly as the plan's simplification note required. Signals, `setTimeout`, `visibilitychange`, `HttpClient` options and Ionic's `AlertController` were all already present.

## Task Commits

1. **Task 1 (RED): failing refresh and server-revoking logout cases** — `2d4532d` (test)
2. **Task 1 (GREEN): credentialed `refresh()`, `expiresAt` signal, server-revoking `logout()`** — `39f409a` (feat)
3. **Task 2 (RED): failing silent-refresh timer cases** — `dfa287a` (test)
4. **Task 2 (GREEN): `SessionStateService` timer, visibility resume, `AppComponent` wiring** — `6fe8b2c` (feat)
5. **Task 3: interceptor rewrite, banner, first-click dialog** — `0d468db` (feat)

**Plan metadata:** not committed — `.planning/` is gitignored in this repo by deliberate onboarding decision, so SUMMARY/STATE/ROADMAP changes are local-only. Same as plans 01-01 through 01-03.

## Files Created/Modified

**Created**

- `session-state.service.ts` — ~73 lines. `status` signal, one private timer handle, an `effect()` that re-schedules on every `expiresAt` change, a `visibilitychange` listener torn down via `DestroyRef`, and `markExpired()` for the interceptor. No router, no storage, no UI.
- `session-state.service.spec.ts` — 9 `fakeAsync` cases against a mocked `AuthService` whose `expiresAt` is a real signal, so the effect-driven re-arm is exercised rather than mocked away.

**Modified**

- `auth.service.ts` — `expiresAt` signal seeded from `storedTokenExpiry()`; the JWT decode that was inlined in `hasValidToken` factored into that one private helper; `refresh()`; `logout()` now POSTs first and tears down locally on both paths.
- `auth.model.ts` — `RefreshResponse` (token / tokenType / expiresAt), deliberately not reusing `AuthResponse`, which carries user identity the refresh body omits.
- `session-expiry.interceptor.ts` — logout-and-navigate replaced by `sessionState.markExpired()` + re-throw. `SELF_HANDLED_PATHS` excludes login (wrong credentials) and refresh (its caller already owns that 401).
- `app.component.ts` / `.html` — banner above the router outlet using `@if`, and a `document:click` `HostListener` raising one `AlertController` dialog whose action calls `AuthService.logout()`.
- `auth.service.spec.ts`, `session-expiry.interceptor.spec.ts`, `app.component.spec.ts` — see Deviations for the last one.

## Decisions Made

- **`markExpired()` is public API the plan did not name.** Task 3 requires the interceptor to "set the session status to expired via `SessionStateService`", and task 2 specifies `status` as readonly. A mutator was the only way to satisfy both. It also clears the timer, so a 401 arriving before the scheduled point stops the loop instead of leaving it to fire into a dead session.
- **The banner's button sets the same guard the dialog uses.** A click on the banner bubbles to `document`, so without the guard a user who acted on the banner would immediately get the dialog too — the exact thing the plan says should not happen. The guard resets on any click once the status leaves `expired`, so a fresh login restores normal behavior.
- **The dialog uses `backdropDismiss: false` with one action.** No copy was specified (01-CONTEXT "Specific Ideas"), so this follows the repo's existing `AlertController` usage in `stop-photo-gallery` and `trip-edit`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `app.component.spec.ts` had no `HttpClient` provider**

- **Found during:** Task 2, first `npm run test:ci` run
- **Issue:** Injecting `SessionStateService` into `AppComponent` made the existing "should create the app" spec fail with `NG0201: No provider found for HttpClient. Path: SessionStateService -> AuthService -> HttpClient`. The spec predates any service injection in the root component.
- **Fix:** added `provideHttpClient()` + `provideHttpClientTesting()` to its TestBed, per the repo's documented spec convention.
- **Files modified:** `frontend/src/app/app.component.spec.ts`
- **Commit:** `6fe8b2c`

**2. [Rule 2 - Missing critical functionality] `AppComponent` specs for the banner and dialog**

- **Found during:** Task 3
- **Issue:** the plan's task 3 lists no spec file for `AppComponent`, but it adds a `HostListener`, an async dialog, a re-entrancy guard and a template branch. `karma.conf.js` enforces coverage floors (statements 93 / branches 84 / functions 90 / lines 94) and **fails `npm run test:ci` itself** when a metric drops — shipping this untested risked the plan's own automated gate, and left the "exactly one dialog" claim unasserted.
- **Fix:** six cases covering banner visibility in both states, no dialog while healthy, one dialog under three synchronous clicks, the dialog action logging out, and the banner action not also raising the dialog.
- **Files modified:** `frontend/src/app/app.component.spec.ts`
- **Commit:** `0d468db`

### Resumed, not re-done

This plan was executed across two sessions. The first ended mid-task-2 with the `SessionStateService` implementation and the `AppComponent` wiring uncommitted on disk. That work was read, checked against the task's `<action>` and the nine already-committed RED cases, and verified by running the suite rather than assumed correct — it passed as written, so it was committed unchanged apart from the spec-provider fix above. Tasks 1's two commits were verified present in `git log` and not repeated.

## Verification Results

| # | Check | Result |
|---|---|---|
| 1 | `cd frontend && npm run lint` | exit 0 — all files pass linting |
| 2 | `cd frontend && npm run test:ci` | exit 0 — **335 SUCCESS**, 0 failures (was 327 before this plan's task 3) |
| 3 | Coverage vs. karma floors | statements 94.96 / branches 89.83 / functions 93.16 / lines 95.83 — all above the 93/84/90/94 floors |
| 4 | `grep -c 'withCredentials' auth.service.ts` | 2 (required >= 2) |
| 5 | `grep -c 'X-Requested-With' auth.service.ts` | 2 (required >= 1) |
| 6 | `grep -rc 'refresh-credentials' core/interceptors/` | 0 (required 0) |
| 7 | `grep -Ec 'constructor\(' auth.service.ts` | 0 (required 0) |
| 8 | `grep -c 'setInterval' session-state.service.ts` | 0 (required 0) |
| 9 | `grep -c 'visibilitychange' session-state.service.ts` | 2 (required >= 1) |
| 10 | `grep -c 'SessionStateService' app.component.ts` | 2 (required >= 1) |
| 11 | `grep -c 'session-state' auth.service.ts` | 0 (required 0 — no injector cycle) |
| 12 | `grep -Ec 'router\|navigate\|localStorage' session-state.service.ts` | 0 (required 0) |
| 13 | `grep -Ec 'authService\.logout\|navigate' session-expiry.interceptor.ts` | 0 (required 0) |
| 14 | `grep -Ec '\*ngIf\|\*ngFor' app.component.html` | 0 (required 0) |
| 15 | `grep -c 'AlertController' app.component.ts` | 2 (required >= 1) |
| 16 | Task 3 `<human-check>` — banner-then-dialog manual QA | **NOT RUN** — see Known Stubs |
| 17 | Task 1 acceptance "cookie jar empty after logout, subsequent refresh 401" | **NOT RUN** — same reason: needs a running backend and a browser |
| 18 | `frontend-ci.yml` green | CI only — not run locally |

## Threat Model Outcome

| Threat ID | Disposition | Outcome |
|---|---|---|
| T-01-21 (Info disclosure, script exfiltrating the refresh token) | mitigate | Satisfied. No changed file contains any cookie-reading API; the client only ever sets `withCredentials`. Structurally guaranteed by `HttpOnly` from 01-02. |
| T-01-22 (Info disclosure, credentials on third-party requests) | mitigate | Satisfied. `withCredentials` appears exactly twice, both inside `AuthService`. No credentials interceptor exists (check 6), so Mapbox/Cloudinary requests are untouched. |
| T-01-23 (Spoofing, stale session appearing usable) | mitigate | Satisfied in code, **partially verified**. The status flip and the intercepted first click are asserted in specs; the on-screen behavior is what the outstanding human-check covers. |
| T-01-24 (Repudiation, logout that never reaches the server) | mitigate | Satisfied in code. `logout()` POSTs before the local clear and tears down on both paths. The browser-observable half (cookie actually gone) is unverified — see check 17. |
| T-01-25 (DoS, refresh loop hammering the endpoint) | mitigate | Satisfied. A failed refresh does not re-arm; a spec ticks 60 further minutes and asserts exactly one call. |
| T-01-26 (Tampering, CSRF header omitted) | mitigate | Satisfied. Header set at both call sites and asserted in `auth.service.spec.ts`; the backend answers 400 without it, so an omission would fail loudly at the first refresh. |
| T-01-27 (Tampering, supply chain) | accept | Satisfied. Zero new dependencies. |

**Threat surface scan:** no new endpoints, no new trust boundary, no schema change. Two new client-side surfaces, both inside the register above: the credentialed call sites (T-01-22) and the expired-session UI window (T-01-23). Nothing outside it.

## Known Stubs

No code stubs — no `TODO`/`FIXME` markers, no skipped tests, no placeholder values reaching the UI.

**One verification is outstanding, and this plan is not fully signed off without it:**

- **Task 3 `<human-check>` — banner-then-dialog manual QA. NOT RUN.** This session had no way to run the Spring backend and drive a browser interactively, so it was neither attempted nor fabricated. It needs a person with the backend running and `JWT_EXPIRY_MS` temporarily lowered to ~60000, walking the plan's five steps: (1) log in, kill the backend so the next silent refresh fails, confirm the app stays put and shows the banner; (2) confirm nothing further happens while idle; (3) click anywhere, confirm exactly one dialog that lands on `/login`; (4) click rapidly before dismissing, confirm no stacking; (5) log in again, confirm the banner is gone. Steps 3 and 4 have spec coverage (`app.component.spec.ts`); steps 1, 2 and 5 are the genuinely visual ones.
- **Task 1's browser-observable acceptance line — cookie jar empty after logout, subsequent manual refresh returns 401. NOT RUN**, same constraint. This is also the R2 Postman/browser regression pass that plans 01-02 and 01-03 already owe.

## User Setup Required

Nothing new. The carry-forward items from 01-02 and 01-03 are unchanged and still open:

- **Render dashboard:** `JWT_EXPIRY_MS=900000` (the dashboard value overrides the code default, so D-02's 15-minute lifetime — which this plan's one-minute buffer is sized against — is not in effect in production until it is set), exact non-wildcard `CORS_ALLOWED_ORIGINS`, optional `REFRESH_TOKEN_EXPIRY_DAYS`.
- **One manual edit still owed:** `backend/.env.example` needs `JWT_EXPIRY_MS=900000` and a commented `# REFRESH_TOKEN_EXPIRY_DAYS=30`. Permission settings deny access to `backend/.env*` in these sessions.

## Next Phase Readiness

- **Phase 1 Success Criterion 4 is met in code end-to-end**: a session survives normal access-token expiry via proactive silent refresh, and logout revokes the refresh token server-side. It is **not fully met in verified fact** until the human-check above is done.
- **Behavioral change other frontend work must know about:** a 401 no longer logs the user out. Anything that relied on `sessionExpiryInterceptor` to clear credentials must now go through `AuthService.logout()` explicitly.
- **Carry-forward from 01-03 that landed correctly:** a failed silent refresh may mean *every* device was signed out (D-03 mass revoke), so the D-06 experience deliberately offers a log-in action rather than a retry. There is no retry/backoff loop at all, which also keeps the client clear of the 60/hour cap.
- **Not exercised anywhere yet:** the multi-tab case. Two tabs each running their own timer will both attempt a refresh, and the second will present an already-rotated cookie — the exact `REFRESH_TOKEN_REUSE_DETECTED` false positive 01-03 predicted. Cross-tab token sync is out of phase scope; the log line is the measurement if it proves noisy.

## Self-Check: PASSED

- `frontend/src/app/core/services/session-state.service.ts` — FOUND
- `frontend/src/app/core/services/session-state.service.spec.ts` — FOUND
- `frontend/src/app/core/services/auth.service.ts` — FOUND
- `frontend/src/app/core/interceptors/session-expiry.interceptor.ts` — FOUND
- `frontend/src/app/app.component.html` — FOUND
- `frontend/src/app/app.component.ts` — FOUND
- `.planning/phases/01-auth-seam-hardening/01-04-SUMMARY.md` — FOUND
- Commit `2d4532d` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `39f409a` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `dfa287a` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `6fe8b2c` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `0d468db` — FOUND on `worktree-gsd-phase1-auth-seam`

---
*Phase: 01-auth-seam-hardening*
*Completed: 2026-08-17*
