---
phase: 01-auth-seam-hardening
verified: 2026-08-17T23:55:00Z
status: passed
score: 4/4 roadmap success criteria verified (18/18 plan truths)
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 2/4 roadmap success criteria
  gaps_closed:

    - "A user's session survives normal access-token expiry via silent refresh, and logout revokes the refresh token server-side (SC4 / AUTH-04)"
    - "The four SCRUM-55 gap scenarios have dedicated passing tests (SC3 / AUTH-03)"
  gaps_remaining: []
  regressions: []
  fix_verified: >-
    Commit dc732cb changes exactly one line of production content —
    V12__create_refresh_tokens.sql token_hash CHAR(64) -> VARCHAR(64) — plus an explanatory
    comment. No other file in the repository was touched, so the fix has no regression
    surface beyond the schema it corrects. The entity was correctly left alone: Hibernate's
    validator derives the expected JDBC type code from the Java field type (String -> VARCHAR)
    and ignores columnDefinition, so the migration was the only side that could move.
known_gaps_not_actionable_here:

  - item: "backend/.env.example still has JWT_EXPIRY_MS=3600000 and no REFRESH_TOKEN_EXPIRY_DAYS line"
    reason: "Permission settings deny access to backend/.env* for every agent in this run — confirmed unchanged vs main by git diff. Manual edit required."

  - item: "Render production env needs JWT_EXPIRY_MS=900000 and a non-wildcard CORS_ALLOWED_ORIGINS"
    reason: "Deployment configuration, not code. Prod overrides the new 900000 default, so D-02 is not in effect in prod until set."

  - item: "WR-08 — RateLimiterService bucket map is an unbounded ConcurrentHashMap, and this phase adds a third unauthenticated key prefix"
    reason: "Deliberately deferred by the fix pass; needs a new Maven dependency (Caffeine). Confirmed still open at RateLimiterService.java:22."

  - item: "docs/auth.md:83 names AuthControllerIntegrationIT, which does not exist (the class is AuthControllerIT)"
    reason: "Review item IN-04, info severity, never picked up by the fix pass. Documentation-only inaccuracy."
human_verification:

  - test: "With the backend running and JWT_EXPIRY_MS temporarily lowered (e.g. 90000), log in, leave the tab idle past expiry, then confirm the session renews silently; then revoke/expire the refresh token and confirm the inline banner appears without navigation, and that the next click raises the session-expired dialog leading to /login."
    expected: "Silent renewal is invisible to the user; on refresh failure the banner appears in place, nothing force-navigates, and the first subsequent click opens exactly one dialog whose action lands on /login with the server-side session revoked."
    why_human: "Plan 01-04 task 3's outstanding <human-check>. Requires a running backend, a real browser cookie jar, and observation of timing/visual behaviour that unit tests with a mocked HttpClient cannot establish. No backend or browser available in this session."
---

# Phase 1: Auth Seam Hardening Verification Report

**Phase Goal:** Auth boundary is type-safe, correctly coded (401 vs 403), covered by real integration tests, and supports persistent login via refresh tokens
**Verified:** 2026-08-17
**Status:** human_needed
**Re-verification:** Yes — after closure of the V12 schema gap (commit dc732cb)

## Re-verification Summary

The single blocker from the initial verification is closed. Commit `dc732cb` changes `token_hash` from `CHAR(64)` to `VARCHAR(64)` in `V12__create_refresh_tokens.sql` and nothing else. Leaving `RefreshToken.java` as a plain `@Column(length = 64)` is the correct call: Hibernate's schema validator derives the expected JDBC type code from the Java field type — `String` is always `VARCHAR` — and does not consult `columnDefinition`, which only influences DDL generation. The migration was the only side that could move.

I re-ran the two suites that previously failed rather than relying on the reported result:

```
./mvnw -B verify -Pci -Dtest=RefreshTokenServiceTest -Dit.test='AuthControllerIT,TripControllerIT'
  RefreshTokenServiceTest   9/9  pass
  AuthControllerIT         28/28 pass  (was 28 errors)
  TripControllerIT         20/20 pass  (was 20 errors)
  BUILD SUCCESS
```

Failsafe XML confirms zero `<failure>` and zero `<error>` elements in both classes, and that the specific cases backing SC3 and SC4 are present and green by name — not merely that the totals moved. No regressions: the change is one SQL line, and every previously verified item was left untouched.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Unauthenticated requests to protected endpoints return 401 with a JSON `ApiError`; forbidden-but-authenticated returns 403 | ✓ VERIFIED | `SecurityErrorWriter.write` passes `null` for `fieldErrors` (SecurityErrorWriter.java:24), matching `GlobalExceptionHandler`'s non-validation shape; both handler unit tests assert `fieldErrors` is null and pass. End-to-end now proven too: `listTrips_noAuthentication_returns401ViaJsonEntryPoint` and `deleteTrip_nonOwner_returns403` pass through the real filter chain. |
| 2 | Controllers resolve the current user via typed `UserPrincipal`, not string-parsed principal | ✓ VERIFIED | All 6 controllers take `@AuthenticationPrincipal UserPrincipal`; zero `authentication.getName()` or raw `Principal` parameters. The reflection gate `controllers_resolveCurrentUserViaTypedPrincipal_notNameString` now executes and passes. `ArchitectureTest` 6/6. |
| 3 | The four SCRUM-55 gap scenarios have dedicated passing tests | ✓ VERIFIED | All four pass by name: `getTrip_nonExistentId_returns404`, `deleteTrip_nonOwner_returns403`, `listTrips_noAuthentication_returns401ViaJsonEntryPoint`, `createTrip_withRealJwt_authenticatesThroughFilterAndPersists` — each asserting an `ApiError` body (`$.status`, `$.path`), not just a status code. |
| 4 | A user's session survives normal access-token expiry via silent refresh, and logout revokes the refresh token server-side | ✓ VERIFIED | Backend lifecycle green end-to-end: `refresh_withValidCookie_returnsNewAccessTokenAndRotatesCookie`, `refresh_replayOfAlreadyRotatedCookie_returns401AndRevokesAllUserTokens`, `refresh_afterMassRevoke_evenTheRotatedCookieIsRejected`, `logout_revokesOnlyThePresentedToken`, `logout_clearsTheCookieWithMatchingAttributes`, `refreshTokensTable_storesOnlyTheHash`. Frontend half proven by 342/342 passing specs. |

**Score:** 4/4 verified.

### Plan-Level Truths

| Plan | Truth | Status | Evidence |
|------|-------|--------|----------|
| 01-01 | Filter-layer 401/403 carry `fieldErrors: null`, not `[]` | ✓ VERIFIED | SecurityErrorWriter.java:24; both handler unit tests pass |
| 01-01 | No controller resolves the user from a principal-name string | ✓ VERIFIED | Grep across `controller/`; gate test now runs and passes |
| 01-01 | All four SCRUM-55 scenarios assert an `ApiError` body | ✓ VERIFIED | Assertions at TripControllerIT :368-369, :381-382, :392; all pass |
| 01-02 | Login returns an access token and sets an httpOnly refresh cookie | ✓ VERIFIED | `login_setsHttpOnlyRefreshTokenCookie` passes; cookie attributes built in `refreshCookie()` (AuthController :136-142) |
| 01-02 | `/api/auth/refresh` returns a fresh access token and rotates the cookie | ✓ VERIFIED | `refresh_withValidCookie_returnsNewAccessTokenAndRotatesCookie` passes |
| 01-02 | Raw refresh token never in a JSON body, only its SHA-256 digest persisted | ✓ VERIFIED | `login_refreshTokenValueIsNotInResponseBody` and `refreshTokensTable_storesOnlyTheHash` pass; ArchUnit confirms the service holds no HTTP types |
| 01-02 | Refresh without the non-simple custom header is rejected before any token lookup | ✓ VERIFIED | `requireCsrfGateHeader` is the first statement of `refresh` and `logout` (:86, :106); `refresh_withoutCustomHeader_returns400BeforeAnyTokenLookup` passes |
| 01-02 | Access tokens expire in 15 minutes; credentialed cross-site CORS enabled | ✓ VERIFIED | `${JWT_EXPIRY_MS:900000}` in application.properties:31 and application-prod.properties:51; `setAllowCredentials(true)` with an explicit origin list and `X-Requested-With` allow-listed (SecurityConfig :83-90) |
| 01-03 | Replaying a redeemed token revokes every session that user holds and returns 401 | ✓ VERIFIED | `refresh_replayOfAlreadyRotatedCookie_returns401AndRevokesAllUserTokens` passes under `Propagation.NOT_SUPPORTED`, so the revoke must survive a real commit |
| 01-03 | Logout revokes only the presented token | ✓ VERIFIED | `logout_revokesOnlyThePresentedToken` passes; `RefreshTokenServiceTest` 9/9 |
| 01-03 | Logout clears the refresh cookie in the browser | ✓ VERIFIED | `logout_clearsTheCookieWithMatchingAttributes` passes — `Max-Age=0` cookie built from the same builder, so attributes match exactly |
| 01-03 | The refresh endpoint is rate limited | ✓ VERIFIED | `checkLimit("refresh:" + ip, ...)` AuthController :87; `app.ratelimit.refresh.capacity=60/1h` |
| 01-03 | Docs describe the shipped refresh/logout contract | ✓ VERIFIED | docs/auth.md "Refresh Tokens" (D-03/D-04, cookie attributes, `X-Requested-With`, rate-limit rationale); docs/api-contracts.md +47 lines |
| 01-04 | User stays signed in past 15-minute expiry via proactive refresh | ✓ VERIFIED | 60s-buffer timer (session-state.service.ts:8, :61-62); spec "refreshes exactly once at the buffer point" passes |
| 01-04 | Silent refresh survives a page reload | ✓ VERIFIED | `expiresAt` seeded from the stored token (auth.service.ts:25); spec "is populated from the stored token on construction" passes |
| 01-04 | Failed refresh keeps the user in place with an inline banner | ✓ VERIFIED | Interceptor only calls `markExpired()` and re-throws; banner behind `@if (sessionStatus() === 'expired')`; specs "leaves the user where they are" and "shows the inline banner" pass |
| 01-04 | The first post-failure action is intercepted with a dialog to /login | ✓ VERIFIED | `@HostListener('document:click')` with `expiryHandled` guard (app.component.ts:32-50); specs "opens exactly one dialog no matter how fast the user clicks" and "logs out through AuthService when the dialog action is taken" pass |
| 01-04 | Logout tells the server to revoke before clearing local state | ✓ VERIFIED | `AuthService.logout` POSTs with `withCredentials` + CSRF header, clears on both outcomes (auth.service.ts:74-84); 3 specs pass |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/.../security/SecurityErrorWriter.java` | null fieldErrors | ✓ VERIFIED | Wired into both handlers |
| `backend/.../db/migration/V12__create_refresh_tokens.sql` | refresh_tokens schema | ✓ VERIFIED | `VARCHAR(64)`, matches the entity mapping; context starts |
| `backend/.../domain/RefreshToken.java` | entity | ✓ VERIFIED | Plain `@Column(length = 64)`, correctly unchanged |
| `backend/.../repository/RefreshTokenRepository.java` | markUsed / revokeAllForUser / deleteExpiredBefore | ✓ VERIFIED | All three present and wired |
| `backend/.../service/RefreshTokenService.java` | issue/rotate/revoke | ✓ VERIFIED | HTTP-free (ArchUnit green) |
| `backend/.../controller/AuthController.java` | refresh + logout endpoints | ✓ VERIFIED | Single `REFRESH_COOKIE` constant both directions |
| `backend/.../schedule/ExpiredRefreshTokenCleanupJob.java` | daily prune | ✓ VERIFIED | `@Scheduled(cron = "0 30 3 * * *")`, `@EnableScheduling` in SchedulingConfig |
| `frontend/.../session-state.service.ts` | proactive timer | ✓ VERIFIED | 11 specs pass |
| `frontend/.../auth.service.ts` | credentialed calls | ✓ VERIFIED | `withCredentials: true` on login/register/refresh/logout |
| `frontend/.../auth.guard.ts` | refresh-not-revoke | ✓ VERIFIED | 3 specs pass |
| `frontend/.../app.component.html` | banner | ✓ VERIFIED | Rendered from `sessionStatus()` |
| `backend/.env.example` | JWT_EXPIRY_MS 900000 | ✗ MISSING | Unchanged vs main (permission-blocked, see known gaps) |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `AuthController.login` | `refresh_tokens` row | `RefreshTokenService.issue` (hash only), cookie built in the controller | ✓ WIRED |
| Browser cookie | `POST /api/auth/refresh` | `@CookieValue(REFRESH_COOKIE)` → `rotate` → `JwtService.generateToken` | ✓ WIRED |
| `rotate` used-token branch | every session for that user | `revokeAllForUser` under `noRollbackFor` | ✓ WIRED |
| `POST /api/auth/logout` | single row `revokedAt` + `Max-Age=0` | `RefreshTokenService.revoke` | ✓ WIRED |
| `AuthService.expiresAt` signal | `setTimeout` → `refresh()` → re-arm | `SessionStateService` effect | ✓ WIRED |
| refresh failure | banner + click interception → dialog → `/login` | status `'expired'` → `AppComponent` | ✓ WIRED |
| `sessionExpiryInterceptor` | HTTP pipeline | `withInterceptors([...])` in main.ts:20 | ✓ WIRED |
| `deleteExpiredBefore` | scheduler | `ExpiredRefreshTokenCleanupJob` + `@EnableScheduling` | ✓ WIRED |

### Behavioural Spot-Checks

| Behaviour | Command | Result | Status |
|-----------|---------|--------|--------|
| Refresh/logout lifecycle end-to-end | `./mvnw verify -Pci -Dit.test=AuthControllerIT` | 28/28 pass | ✓ PASS |
| SCRUM-55 gap scenarios + principal gate | `./mvnw verify -Pci -Dit.test=TripControllerIT` | 20/20 pass | ✓ PASS |
| Refresh-token service logic | `./mvnw verify -Pci -Dtest=RefreshTokenServiceTest` | 9/9 pass | ✓ PASS |
| Security error shape, CSRF gate, arch rules | `./mvnw test -Dtest=Json*Test,AuthControllerTest,ArchitectureTest` | 18/18 pass | ✓ PASS |
| Frontend session/auth behaviour | `npm run test:ci` | 342/342 SUCCESS, statements 95.02% | ✓ PASS |
| Full backend suite | `./mvnw -q -B verify -Pci` (run by team-lead) | exit 0, 26 failsafe suites, 0 errors | ✓ PASS (reported; the two suites material to this phase independently re-run above) |

### Requirements Coverage

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| AUTH-01 | 01-01 | ✓ SATISFIED | Filter-layer `ApiError` shape correct, unit- and IT-proven |
| AUTH-02 | 01-01 | ✓ SATISFIED | Typed `UserPrincipal` throughout; gate test green |
| AUTH-03 | 01-01 | ✓ SATISFIED | All four scenarios pass with body assertions |
| AUTH-04 | 01-02, 01-03, 01-04 | ✓ SATISFIED IN CODE | Full lifecycle green backend and frontend; one browser QA item outstanding |

No orphaned requirements — REQUIREMENTS.md maps exactly AUTH-01..04 to Phase 1 and all four are claimed by plans.

### Code Review Fix Confirmation

Confirmed in the source, not from the fixer's report (carried forward from the initial verification, re-checked as unchanged):

| ID | Fix | Landed? |
|----|-----|---------|
| CR-01 | `withCredentials: true` on login and register | ✓ auth.service.ts:32, :41 |
| CR-02 | `authGuard` refreshes instead of revoking on access-token expiry | ✓ auth.guard.ts:21-24 — calls `refresh()`, never `logout()` |
| WR-01 | Conditional-update redemption | ✓ `markUsed` with `usedAt IS NULL`; zero rowcount = reuse |
| WR-02 | Expired-row pruning | ✓ daily job at 03:30 (no `expires_at` index added — minor, the review suggested one) |
| WR-03 | Reuse IT outside the test transaction | ✓ `Propagation.NOT_SUPPORTED`; now actually executes and passes |
| WR-04 | One cookie-name constant both directions | ✓ `REFRESH_COOKIE` |
| WR-05 | Local-only teardown on failed refresh | ✓ `clearLocalSession()` on 401 only |
| WR-06 | Logout resets session status | ✓ `clearSession()` → `SessionStateService.reset()` |
| WR-07 | Transport failure ≠ expiry | ✓ refresh exempt from the 8s timeout; `'expired'` only on 401 |
| WR-08 | Bounded rate-limiter map | ✗ Deferred as agreed |

Info items IN-01, IN-02, IN-03, IN-05, IN-06, IN-07, IN-08, IN-09 remain open and are non-blocking. IN-04 (docs/auth.md:83 naming a nonexistent `AuthControllerIntegrationIT`) is still unfixed.

### Human Verification Required

#### 1. Two-stage session-expiry experience (D-05 / D-06)

**Test:** With the backend running and `JWT_EXPIRY_MS` temporarily lowered, log in and idle past expiry to observe silent renewal; then force the refresh token dead and confirm the banner-then-dialog behaviour.
**Expected:** Renewal is invisible; on failure the banner appears in place with no navigation, and the first click afterwards opens exactly one dialog leading to `/login`.
**Why human:** Plan 01-04 task 3's outstanding `<human-check>`. Needs a running backend, a real cookie jar and visual/timing observation. Deferred, not failed — the underlying behaviour is proven by 342 passing specs and a green backend lifecycle; what remains is confirming it feels right in a browser.

Now unblocked: the backend starts again, so this can be run whenever a browser session is available.

### Gaps Summary

No gaps. All four roadmap success criteria and all 18 plan-level truths are verified, with behavioural evidence rather than presence checks for every state transition the phase claims — rotation, reuse-triggered family revoke, single-device logout revoke, cookie clearing, and the frontend timer/banner/dialog sequence.

The phase is code-complete. What stands between it and "done" is one browser QA pass and three environment/config items that no agent in this session could touch: the `backend/.env.example` edit, the Render production settings (`JWT_EXPIRY_MS=900000`, non-wildcard `CORS_ALLOWED_ORIGINS`), and the deliberately deferred WR-08 rate-limiter bound.

---

_Verified: 2026-08-17 (re-verification after dc732cb)_
_Verifier: Claude (gsd-verifier)_
