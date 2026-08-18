---
phase: 01-auth-seam-hardening
plan: 03
subsystem: auth
tags: [refresh-tokens, revocation, reuse-detection, csrf, rate-limiting, jpa-bulk-update, transactions, testcontainers]

# Dependency graph
requires:
  - phase: 01-02
    provides: RefreshTokenService.issue/rotate, RefreshTokenRepository.findByTokenHash, RefreshToken used_at/revoked_at columns, AuthController cookie + X-Requested-With helpers, RefreshTokenProperties
provides:
  - RefreshTokenRepository.revokeAllForUser — bulk @Modifying revoke of every unrevoked row for a user (D-03)
  - RefreshTokenService.revoke — idempotent single-token revocation (D-04)
  - POST /api/auth/logout — cookie-authenticated, CSRF-gated, always 204, clears the cookie
  - reuse detection on rotate with a greppable REFRESH_TOKEN_REUSE_DETECTED WARN marker (checkpoint option-c)
  - app.ratelimit.refresh.* — 60/hour per client IP on POST /api/auth/refresh
  - RefreshTokenServiceTest + six AuthControllerIT lifecycle scenarios
affects: [01-04 frontend silent refresh and logout wiring, Render dashboard env config]

actuals:
  tokens: 10000
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "@Transactional(noRollbackFor = ...) where the method's own thrown exception must not undo a security-motivated write"
    - "Single cookie-attribute builder shared by the issuing and clearing Set-Cookie, so a clear can never silently mismatch the original"
    - "Distinguishable uppercase log marker for a security event that is expected to have benign false positives, so its real-world rate is measurable"

key-files:
  created:
    - backend/src/test/java/com/tripflow/backend/service/RefreshTokenServiceTest.java
  modified:
    - backend/src/main/java/com/tripflow/backend/repository/RefreshTokenRepository.java
    - backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java
    - backend/src/main/java/com/tripflow/backend/controller/AuthController.java
    - backend/src/main/java/com/tripflow/backend/ratelimit/RateLimitProperties.java
    - backend/src/main/resources/application.properties
    - backend/src/test/resources/application-test.properties
    - backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java
    - docs/auth.md
    - docs/api-contracts.md
    - docs/deployment.md
    - backend/README.md

key-decisions:
  - "Checkpoint D-03 resolved as option-c by the developer: ship the user-wide revoke as written AND log a distinguishable audit event on every mass revoke. Implemented as a WARN carrying an uppercase REFRESH_TOKEN_REUSE_DETECTED marker, the user id and the affected row count — no token material."
  - "rotate is annotated @Transactional(noRollbackFor = InvalidRefreshTokenException.class). Without it the 401 thrown immediately after the mass revoke rolls that revoke back, so D-03 would have shipped as a no-op that looked correct in every mocked test."
  - "revokeAllForUser takes an explicit Instant rather than using JPQL CURRENT_TIMESTAMP. The plan's signature was one-arg; the timestamp is passed in because a JPQL function's Instant mapping could only have been validated in CI (no local Docker), and a parameter has no dialect risk."
  - "The refresh rate limit is keyed on the resolved client IP, not the token hash — keying on the presented value would let an attacker mint a fresh bucket per forged token."

patterns-established:
  - "A security response that ends in an exception needs its transaction semantics checked: default rollback-on-RuntimeException silently reverts the response"
  - "Any new rate limit needs a matching high-capacity entry in application-test.properties, because MockMvc gives every request the same synthetic remote address and the bucket is a JVM-lifetime singleton shared across test methods"

requirements-completed: [AUTH-04]

coverage:
  - id: D1
    description: "Replaying an already-redeemed refresh token revokes every refresh token that user holds and returns 401 (D-03)"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/service/RefreshTokenServiceTest.java#rotate_replayOfAlreadyUsedToken_revokesEveryTokenForThatUserAndThrows, #rotate_replayOfAlreadyUsedToken_doesNotIssueAReplacement"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#refresh_replayOfAlreadyRotatedCookie_returns401AndRevokesAllUserTokens, #refresh_afterMassRevoke_evenTheRotatedCookieIsRejected"
        status: unknown
    human_judgment: false
  - id: D2
    description: "A revoked-but-never-redeemed or naturally expired token is rejected without triggering the mass revoke — logout is not a compromise signal"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/service/RefreshTokenServiceTest.java#rotate_revokedButNeverUsedToken_throwsWithoutMassRevoke, #rotate_expiredToken_throwsWithoutMassRevoke"
        status: pass
    human_judgment: false
  - id: D3
    description: "Logging out revokes only the presented token, leaving the user's other devices signed in (D-04)"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/service/RefreshTokenServiceTest.java#revoke_stampsOnlyTheMatchingRowAndNeverMassRevokes"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#logout_revokesOnlyThePresentedToken"
        status: unknown
    human_judgment: false
  - id: D4
    description: "Logout is idempotent and is not an oracle — unknown, already-revoked, expired and absent cookies all complete without error"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/service/RefreshTokenServiceTest.java#revoke_unknownToken_isANoOpAndDoesNotThrow, #revoke_alreadyRevokedOrExpiredToken_isANoOpAndDoesNotThrow"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#logout_withNoCookie_returns204"
        status: unknown
    human_judgment: false
  - id: D5
    description: "Logout clears the cookie with attributes matching the issuing cookie, and the CSRF gate covers logout as well as refresh"
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#logout_clearsTheCookieWithMatchingAttributes, #logout_withoutCustomHeader_isRejected"
        status: unknown
    human_judgment: false
  - id: D6
    description: "The refresh endpoint is rate limited, bounding the force-logout denial-of-service surface reuse detection creates"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "full local unit suite — 39 test classes, Spring context binds the new RateLimitProperties.refresh component"
        status: pass
    human_judgment: false
  - id: D7
    description: "The service layer still holds no HTTP types after the changes"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/ArchitectureTest.java#services_must_not_have_http_concerns"
        status: pass
    human_judgment: false

# Metrics
duration: 20min
completed: 2026-08-14
status: complete
---

# Phase 1 Plan 03: Refresh-Token Revocation Summary

**Replaying a redeemed refresh token now revokes every session that user holds and says so in a greppable WARN, logging out ends exactly one session and clears its cookie, and the refresh endpoint is capped at 60/hour so the new mass-revoke cannot be aimed at a victim.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-08-14T21:44Z
- **Completed:** 2026-08-14T22:04Z
- **Tasks:** 3 (4 commits — task 1 was TDD, so RED and GREEN are separate)
- **Files created:** 1 · **Files modified:** 11

## Checkpoint Decision

The plan opens with a blocking `checkpoint:decision` on D-03 (one-way). The developer selected **option-c**: ship the user-wide revoke as written, and additionally log a distinguishable audit event on every mass revoke. Both consequences the checkpoint asked to be confirmed were accepted as part of that selection — multi-tab false positives are expected behavior, and access tokens issued before a revoke stay valid until their own 15-minute expiry.

Implemented as:

```
WARN REFRESH_TOKEN_REUSE_DETECTED all sessions revoked userId=42 revokedTokenCount=3
```

Uppercase marker first so it greps cleanly out of production logs; user id and affected row count only, never a raw token or a hash. The point of option-c is that the multi-tab false-positive rate becomes *measurable* — which is exactly the data a future decision to narrow D-03 would need, and which option-a would have left unknowable.

## Accomplishments

- **D-03 is a real revocation, not an assertion.** The bulk `@Modifying` update stamps `revoked_at` on every unrevoked row for the user in one statement — a load-and-loop could fail halfway and leave a device signed in, which on a compromise signal is worse than not trying.
- **The one bug that would have made this ship as theatre was caught.** See Deviations — `rotate` throws immediately after the mass revoke, and Spring's default rollback-on-RuntimeException would have undone it. Every mocked test still passed; the behavior would have been silently absent in production.
- **Ordering is now explicit: revoked/expired before used.** The tracer checked all three conditions in one `if`. Splitting them is what keeps a normal logout or a lapsed token from being reported as a stolen one — and there are unit tests that fail if the order regresses.
- **Logout cannot silently fail to log out.** The issuing and clearing `Set-Cookie` are built from one shared attribute builder, so a mismatched path or `SameSite` (the classic reason a browser keeps the original cookie) is impossible without changing both at once.
- **The rate limit was introduced in the same commit as the thing it bounds.** Reuse detection turns `/api/auth/refresh` into a force-logout weapon against a known victim; 60/hour per resolved client IP is ~15x what a legitimate 15-minute-token client needs, leaving headroom for shared-NAT users.

## Task Commits

1. **Task 1 (RED): failing reuse-detection and logout revocation cases** — `8930bf4` (test)
2. **Task 1 (GREEN): mass revoke on reuse, `POST /api/auth/logout`, refresh rate limit** — `8af3374` (feat)
3. **Task 2: six end-to-end lifecycle scenarios in `AuthControllerIT`** — `4f36cae` (test, also carries the `noRollbackFor` fix)
4. **Task 3: documentation of the shipped contract** — `a64df7f` (docs)

**Plan metadata:** not committed — `.planning/` is gitignored in this repo by deliberate onboarding decision, so SUMMARY/STATE/ROADMAP changes are local-only. Same as plans 01-01 and 01-02.

## Files Created/Modified

**Created**

- `RefreshTokenServiceTest.java` — eight Mockito cases across the two policies: mass revoke on replay (called exactly once, with that row's user id), no replacement issued on replay, revoked and expired tokens rejected *without* a mass revoke, single-row logout revocation, hash-not-raw-value lookup, and the three idempotent logout no-ops.

**Modified**

- `RefreshTokenRepository.java` — `revokeAllForUser(Long userId, Instant revokedAt)` with `TripLikeRepository`'s exact `@Modifying(clearAutomatically = true, flushAutomatically = true)` shape, returning the affected row count.
- `RefreshTokenService.java` — the tracer's placeholder branch replaced by the real D-03 response; revoked/expired checked first; `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`; new `revoke(String)` for D-04.
- `AuthController.java` — `refresh` now rate limited after the CSRF gate and before the cookie is read; new `POST /api/auth/logout`; `attachRefreshCookie` refactored onto a shared `refreshCookie(value)` builder that the clearing cookie also uses.
- `RateLimitProperties.java` / `application.properties` — a `refresh` `Limit` component and `app.ratelimit.refresh.capacity=60` / `.window=1h`, commented with what the cap actually protects.
- `application-test.properties` — `app.ratelimit.refresh.*` at 1000, matching the existing login/register treatment (see Deviations).
- `AuthControllerIT.java` — six new scenarios plus two small helpers (`loginAndCaptureRefreshCookie`, `unrevokedTokenCount`).
- `docs/auth.md`, `docs/api-contracts.md`, `docs/deployment.md`, `backend/README.md` — see Task 3 below.

## Decisions Made

- **`revokeAllForUser` takes an explicit `Instant`.** The plan specified a one-arg signature stamping "the current timestamp", which implies JPQL `CURRENT_TIMESTAMP`. A JPQL function's mapping onto an `Instant` attribute could only have been validated under `-Pci` (no machine here runs Docker), so a bound parameter was preferred — same behavior, zero dialect risk, and the unit test can assert on it.
- **The rate limit is keyed on the remote address, matching login/register.** Keying on the token hash would let an attacker cycle forged values for an unlimited bucket. `RemoteIpValve` + `CF-Connecting-IP` (SCRUM-312) already resolves the real client IP in production.
- **Logout is not itself rate limited.** It performs one indexed lookup and one row update, reveals nothing, and cannot be aimed at another user. Adding a second limiter key would be cost without a threat behind it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The D-03 mass revoke would have been rolled back by the 401 that follows it**

- **Found during:** Task 2, while reasoning about what the integration test would actually observe
- **Issue:** `rotate` is `@Transactional`, and `InvalidRefreshTokenException` is a `RuntimeException`. Spring's default rollback rule reverts the transaction on any unchecked exception — so the `revokeAllForUser` update issued microseconds earlier would have been rolled back by the very throw that signals the compromise. **Every mocked unit test still passes** (Mockito never sees a transaction), and the integration test would also have passed, because within the test's own outer transaction the rows still read as revoked before the rollback executes. This is a defect that all of the plan's specified verification would have reported as green.
- **Fix:** `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)` on `rotate`, with a comment naming why it is load-bearing. Every write this method performs is one we want kept even when the call ends in a 401.
- **Files modified:** `backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java`
- **Commit:** `4f36cae`

**2. [Rule 3 - Blocking] The new rate limit needed a test-profile override**

- **Found during:** Task 2
- **Issue:** `application-test.properties` deliberately raises `login`/`register` to 1000 because MockMvc gives every request the same synthetic remote address and `RateLimiterService`'s bucket map is a JVM-lifetime singleton shared across every test method in a context. `AuthControllerIT` now makes ~10 refresh calls; against the production 60/hour default that is safe today but is one added scenario away from an intermittent CI-only failure that nobody can reproduce locally.
- **Fix:** `app.ratelimit.refresh.capacity=1000` / `.window=1h` in `application-test.properties`, alongside the existing login/register entries.
- **Files modified:** `backend/src/test/resources/application-test.properties`
- **Commit:** `4f36cae`

### Blocked, not done

**`backend/.env.example` was not updated.** Task 3 required changing its `JWT_EXPIRY_MS` example to `900000` and adding a commented `REFRESH_TOKEN_EXPIRY_DAYS` line. This environment's permission settings deny all reads and writes under `backend/.env*` (a dotfile/secret guard), so the file could neither be read nor edited. The equivalent change was made to `backend/README.md`, which carries the same example block, and to `docs/deployment.md`. **The acceptance criterion `grep -c 'REFRESH_TOKEN_EXPIRY_DAYS' backend/.env.example >= 1` is therefore not met** and one manual edit is owed:

```
JWT_EXPIRY_MS=900000          # was 3600000
# REFRESH_TOKEN_EXPIRY_DAYS=30   (optional, omit to accept the default)
```

### Not a deviation, worth recording

The plan's Task 3 asked to correct `docs/auth.md`'s "typically one hour" access-token statement — plan 01-02 had already fixed it. Likewise most of the cookie/CSRF prose the plan asked for already existed from 01-02; Task 3's real content was reuse detection, logout, the rate limit, the residual, and the env vars.

## Issues Encountered

The transaction-rollback bug above is the one worth carrying forward: **a security response that ends in a thrown exception needs its transaction semantics checked explicitly.** It is invisible to mocked tests by construction, and invisible to a `@Transactional` integration test too, because the rollback happens after the assertions. The only ways to catch it are reading the annotation with the throw in mind, or a non-transactional test that re-reads the row in a new transaction.

## Verification Results

| # | Check | Result |
|---|---|---|
| 1 | `./mvnw.cmd -q test -Dtest=RefreshTokenServiceTest,ArchitectureTest` | exit 0 — all green, `services_must_not_have_http_concerns` still holds |
| 2 | `./mvnw.cmd -q test` (full local unit suite, no Docker) | exit 0 — 39 surefire reports, every one `Failures: 0, Errors: 0`; context binds the new `refresh` limit |
| 3 | `./mvnw.cmd -q test-compile` | exit 0 |
| 4 | `grep -Ec 'jakarta\.servlet\|springframework\.http' RefreshTokenService.java` | 0 (required 0) |
| 5 | `grep -c 'Modifying' RefreshTokenRepository.java` | 2 (required >= 1) |
| 6 | `grep -Ec 'app.ratelimit.refresh.(capacity\|window)' application.properties` | 2 (required 2) |
| 7 | `grep -c 'logout' AuthController.java` | 4 (required >= 1) |
| 8 | `@Test` count in `AuthControllerIT` | 22 → **28** (required >= +6) |
| 9 | `grep -c 'refresh_replayOfAlreadyRotatedCookie_returns401AndRevokesAllUserTokens'` / `'logout_revokesOnlyThePresentedToken'` | 1 / 1 |
| 10 | `grep -c 'api/auth/refresh'` / `'api/auth/logout'` in `docs/api-contracts.md` | 3 / 1 (required >= 1 each) |
| 11 | `grep -c 'REFRESH_TOKEN_EXPIRY_DAYS' docs/deployment.md` | 1 (required >= 1) |
| 12 | `grep -c '3600000'` in `backend/README.md`, `docs/deployment.md` | 0 / 0 — **`backend/.env.example` not checkable** (permission-denied, see Blocked above) |
| 13 | Task 3 touched no source | confirmed — `git status` showed only `docs/**` and `backend/README.md` |
| 14 | Log review: no `log.` call takes a raw token or hash | confirmed by reading every statement in `RefreshTokenService` and `AuthController` — user ids and row counts only |
| 15 | `mvn -B verify -Pci` — the six new `AuthControllerIT` scenarios | **CI only — not run locally.** No team machine runs Docker (CLAUDE.md). |

## Threat Model Outcome

| Threat ID | Disposition | Outcome |
|---|---|---|
| T-01-13 (Spoofing, replay of a redeemed token) | mitigate | Satisfied. Bulk revoke before the 401, asserted by `rotate_replayOfAlreadyUsedToken_revokesEveryTokenForThatUserAndThrows` locally and `refresh_replayOfAlreadyRotatedCookie_returns401AndRevokesAllUserTokens` in CI. The `noRollbackFor` fix is what makes this real rather than nominal. |
| T-01-14 (DoS, replay-driven forced logout) | mitigate | Satisfied. 60/hour per resolved client IP, introduced in the same commit as the mass revoke. |
| T-01-15 (CSRF, cross-site forced logout) | mitigate | Satisfied. Logout calls the same header gate as refresh; asserted by `logout_withoutCustomHeader_isRejected` (CI). |
| T-01-16 (Repudiation, no trace of a compromise revocation) | mitigate | Satisfied, at the option-c level: WARN with a distinguishable marker, user id, and affected row count. |
| T-01-17 (Info disclosure, logout as a validity oracle) | mitigate | Satisfied. 204 for every cookie state; three unit cases plus `logout_withNoCookie_returns204`. |
| T-01-18 (EoP, logout widened beyond the caller's session) | mitigate | Satisfied. `revoke` never calls the bulk method — asserted directly, and `logout_revokesOnlyThePresentedToken` fails if the scope widens. |
| T-01-19 (Spoofing, access token valid after a mass revoke) | accept | Accepted as planned; now written down in `docs/auth.md` as a known residual bounded by the 15-minute lifetime. |
| T-01-20 (Tampering, supply chain) | accept | Satisfied. Zero new dependencies. |

**Threat surface scan:** one new endpoint (`POST /api/auth/logout`), one new bulk-update query, one new rate-limit key. All three are in the plan's register above. No new surface outside it.

## Known Stubs

None. No `TODO`/`FIXME` markers, no skipped tests, no placeholder values reaching a response.

One item is *outstanding but not a stub*: `backend/.env.example` needs the one manual edit described under Deviations → Blocked.

## User Setup Required

Unchanged from plan 01-02 and still open — the Render dashboard items (`JWT_EXPIRY_MS` → `900000`, exact `CORS_ALLOWED_ORIGINS`, optional `REFRESH_TOKEN_EXPIRY_DAYS`). `docs/deployment.md` now states explicitly that the dashboard value overrides the code default, which is the trap: without that change production keeps long-lived access tokens, and the window in which a mass-revoked session stays usable is correspondingly longer.

Plus the one manual `backend/.env.example` edit above.

## Next Phase Readiness

- **Ready for 01-04 (frontend).** The backend contract is complete and documented: `POST /api/auth/refresh` (200 + rotated cookie) and `POST /api/auth/logout` (204, always). Both require `X-Requested-With` and `withCredentials: true`.
- **Carry-forward for 01-04:** a failed silent refresh may now mean *every* device was signed out, not just this tab — the D-06 "session expired" experience should not assume the user can simply retry. The 60/hour refresh cap also bounds how aggressively a retry/backoff loop may run.
- **Carry-forward for 01-04:** the multi-tab false positive is now a live production behavior. If it proves noisy, the `REFRESH_TOKEN_REUSE_DETECTED` log line is the measurement, and cross-tab token sync (out of phase scope) is the fix — not loosening D-03.
- **Unverified in this session:** all `AuthControllerIT` scenarios (CI only), and the browser-observable cookie behavior, which MockMvc cannot check. R2 still owes a Postman/browser regression pass after merge.

## Self-Check: PASSED

- `backend/src/test/java/com/tripflow/backend/service/RefreshTokenServiceTest.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/repository/RefreshTokenRepository.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/controller/AuthController.java` — FOUND
- `docs/auth.md` — FOUND
- `.planning/phases/01-auth-seam-hardening/01-03-SUMMARY.md` — FOUND
- Commit `8930bf4` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `8af3374` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `4f36cae` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `a64df7f` — FOUND on `worktree-gsd-phase1-auth-seam`

---
*Phase: 01-auth-seam-hardening*
*Completed: 2026-08-14*
