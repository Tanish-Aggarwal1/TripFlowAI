---
phase: 01-auth-seam-hardening
plan: 02
subsystem: auth
tags: [spring-security, refresh-tokens, cookies, cors, csrf, flyway, jpa, archunit, testcontainers]

# Dependency graph
requires:
  - phase: 01-01
    provides: uniform null-fieldErrors ApiError shape across filter and handler layers
  - phase: pre-GSD work already on main
    provides: AuthService, AuthResponse, JwtService, BaseEntity, RateLimiterService, ArchitectureTest, GlobalExceptionHandler
provides:
  - refresh_tokens table (V12) storing only SHA-256 hex digests, with used_at/revoked_at columns pre-provisioned for plan 01-03
  - RefreshTokenService.issue/rotate plus the IssuedToken/RotatedSession seams plan 01-03 extends
  - POST /api/auth/refresh — single-use cookie redemption returning a new access token and a rotated cookie
  - httpOnly, host-only, SameSite=None refresh cookie issued on both register and login
  - X-Requested-With CSRF gate as the SameSite replacement for cross-site cookie delivery
  - credentialed CORS against an explicit origin list, with X-Requested-With allow-listed
  - 15-minute default access-token lifetime (D-02)
affects: [01-03 revocation and reuse detection, 01-04 frontend silent refresh, Render dashboard env config]

actuals:
  tokens: 12000
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Opaque random token stored as a SHA-256 hex digest in a CHAR(64) column — the presentable value exists only in the cookie"
    - "Non-simple custom request header as a CSRF control where SameSite is unavailable due to cross-subdomain topology"
    - "Cookie construction confined to the controller so the ArchUnit services_must_not_have_http_concerns rule keeps holding"

key-files:
  created:
    - backend/src/main/resources/db/migration/V12__create_refresh_tokens.sql
    - backend/src/main/java/com/tripflow/backend/domain/RefreshToken.java
    - backend/src/main/java/com/tripflow/backend/repository/RefreshTokenRepository.java
    - backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java
    - backend/src/main/java/com/tripflow/backend/config/RefreshTokenProperties.java
    - backend/src/main/java/com/tripflow/backend/config/RefreshTokenConfig.java
    - backend/src/main/java/com/tripflow/backend/dto/RefreshResponse.java
    - backend/src/main/java/com/tripflow/backend/exception/InvalidRefreshTokenException.java
  modified:
    - backend/src/main/java/com/tripflow/backend/controller/AuthController.java
    - backend/src/main/java/com/tripflow/backend/exception/GlobalExceptionHandler.java
    - backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java
    - backend/src/main/resources/application.properties
    - backend/src/main/resources/application-prod.properties
    - backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java
    - backend/src/test/java/com/tripflow/backend/controller/AuthControllerTest.java
    - docs/auth.md
    - docs/api-contracts.md

key-decisions:
  - "D-07 (new): refresh-token lifetime is 30 days, fixed from issuance rather than sliding. Rotation already re-issues on every refresh, so an active session survives indefinitely — the 30 days bounds how long a user may be away, which is the right thing to bound, and a stolen-but-unused token cannot extend its own life."
  - "D-01 amended per RESEARCH.md Pattern 3: SameSite=None; Secure instead of Lax/Strict, because the deployed frontend and backend are different onrender.com subdomains and therefore cross-site. CSRF is carried by a required X-Requested-With header instead. This is the escape hatch D-01 itself authorized, not a unilateral override."
  - "used_at and revoked_at ship in V12 unwritten rather than in a later ALTER — migrations are append-only here and both columns are already known to be needed by plan 01-03."
  - "RefreshToken.userId is a plain id column, not a @ManyToOne — the only access patterns are hash lookup and a bulk update keyed by user id, so an association buys nothing and costs a lazy proxy."
  - "Four new AuthControllerTest slice tests were added beyond the plan (which only specified AuthControllerIT scenarios) because *IT runs under -Pci only. Without them the CSRF gate and cookie attributes would have had zero local coverage on a repo where no machine runs Docker."

patterns-established:
  - "Security-relevant source gates in acceptance criteria are grep-literal, so explanatory comments must avoid quoting the forbidden symbol — two comments were reworded rather than deleted"
  - "@TestConfiguration-supplied real @ConfigurationProperties record in a @WebMvcTest slice, rather than a mocked record, when the property values are themselves under test"

requirements-completed: [AUTH-04]

coverage:
  - id: D1
    description: "Login and register set an httpOnly, host-only refresh cookie with Secure, SameSite=None, Path=/api/auth and no Domain attribute, without changing the AuthResponse body shape"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerTest.java#login_attachesHttpOnlyHostOnlyRefreshCookie"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#login_setsHttpOnlyRefreshTokenCookie"
        status: unknown
    human_judgment: false
  - id: D2
    description: "The raw refresh token never appears in a JSON response body and is never persisted — only its SHA-256 hex digest reaches the database"
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#login_refreshTokenValueIsNotInResponseBody, #refreshTokensTable_storesOnlyTheHash"
        status: unknown
    human_judgment: false
  - id: D3
    description: "POST /api/auth/refresh redeems the cookie once, returning a fresh access token and a different cookie value"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerTest.java#refresh_withCookieAndHeader_returnsNewTokenAndRotatedCookie"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#refresh_withValidCookie_returnsNewAccessTokenAndRotatesCookie"
        status: unknown
    human_judgment: false
  - id: D4
    description: "A refresh call without the X-Requested-With header is refused with 400 before any token lookup; a call with the header but no cookie is refused with 401"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerTest.java#refresh_withoutCustomHeader_returns400AndNeverCallsRotate, #refresh_withHeaderButNoCookie_returns401"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java#refresh_withoutCustomHeader_returns400BeforeAnyTokenLookup, #refresh_withNoCookie_returns401"
        status: unknown
    human_judgment: false
  - id: D5
    description: "The service layer holds no HTTP types — cookie construction stayed in the controller"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/ArchitectureTest.java#services_must_not_have_http_concerns"
        status: pass
    human_judgment: false
  - id: D6
    description: "Credentialed cross-site CORS is enabled against an explicit origin list and the Spring context still loads"
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "full local unit suite — 38 test classes, context loads with setAllowCredentials(true)"
        status: pass
    human_judgment: false

# Metrics
duration: 15min
completed: 2026-08-14
status: complete
---

# Phase 1 Plan 02: Refresh-Token Tracer Summary

**A login now hands back an httpOnly, host-only, cross-site-capable refresh cookie whose single redemption at `POST /api/auth/refresh` yields a fresh 15-minute access token and a rotated cookie — with only SHA-256 digests in the database and zero HTTP types in the service layer.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-08-14T21:22Z
- **Completed:** 2026-08-14T21:37Z
- **Tasks:** 2 (3 commits — the tracer was TDD, so RED and GREEN are separate)
- **Files created:** 8 · **Files modified:** 9

## Accomplishments

- **The whole refresh architecture is proven on one path, not sketched.** `V12__create_refresh_tokens.sql`, the `RefreshToken` entity, SHA-256 hashing, the real cookie attributes and the CSRF gate all landed as production code. Nothing here is a throwaway that plan 01-03 replaces; 01-03 extends the seams (`used_at`/`revoked_at` already exist in the schema, and `rotate`'s already-redeemed branch is the deliberate hook for the D-03 mass-revoke).
- **The load-bearing risk was the ArchUnit constraint, and it held.** `ResponseCookie` lives in `org.springframework.http`, which `services_must_not_have_http_concerns` forbids in `..service..`. `RefreshTokenService` returns plain records (`IssuedToken`, `RotatedSession`) and `AuthController` builds the `Set-Cookie` header from them. `ArchitectureTest` passes with no rule relaxed and no exclusion added.
- **The second load-bearing risk — cross-site cookie topology — is now configured, not assumed.** `setAllowCredentials(true)` plus `X-Requested-With` in the allowed headers, with `setAllowedOrigins`' explicit list untouched. The full unit suite passing is the meaningful signal here: a credentialed-CORS-plus-wildcard misconfiguration fails at *bean creation*, so a green context load is the check.
- **Two stale security comments were rewritten, not flipped.** Both the `csrf(...)` and the `setAllowCredentials` comments asserted that no cookie-based state exists anywhere in this app. That stopped being true in task 1. Each now names the cookie, its `Path=/api/auth` scope, the `X-Requested-With` preflight gate that actually protects it, and why SameSite could not carry the protection — so a future reader can find the real control from the comment.
- **Local coverage exists for the security-critical bits despite no Docker.** The plan specified six `AuthControllerIT` scenarios, all of which run under `-Pci` only. Four `AuthControllerTest` slice tests were added so the CSRF gate, the cookie attributes, and the rotation wiring fail a plain `./mvnw.cmd test` if they regress.

## Task Commits

1. **Task 1 (RED): failing refresh-flow scenarios in `AuthControllerIT`** — `0f1d20f` (test)
2. **Task 1 (GREEN): issue and rotate httpOnly refresh tokens at `POST /api/auth/refresh`** — `7cb098a` (feat)
3. **Task 2: credentialed cross-site CORS and 15-minute access tokens** — `ecdda8d` (feat)

**Plan metadata:** not committed — `.planning/` is gitignored in this repo by deliberate onboarding decision (the team does not use GSD), so SUMMARY/STATE/ROADMAP changes are local-only. Same as plan 01-01.

## Files Created/Modified

**Created**

- `V12__create_refresh_tokens.sql` — `refresh_tokens` with `token_hash CHAR(64) NOT NULL UNIQUE`, `expires_at`, nullable `used_at`/`revoked_at`, FK to `users` with `ON DELETE CASCADE`, and `idx_refresh_tokens_user_id`. No separate index on `token_hash`: the UNIQUE constraint already provides one, matching `users.email` in V1.
- `RefreshToken.java` — extends `BaseEntity`; `userId` as a plain column, `tokenHash` (`length = 64`, unique), `expiresAt`, `usedAt`, `revokedAt`.
- `RefreshTokenRepository.java` — one derived method, `findByTokenHash`. The bulk-revoke query is 01-03's to add.
- `RefreshTokenService.java` — `issue(Long)` and `rotate(String)`, `SecureRandom` 32-byte URL-safe token generation, `MessageDigest` SHA-256 hex via `HexFormat`. Logs user ids only, never token material.
- `RefreshTokenProperties.java` / `RefreshTokenConfig.java` — `app.refresh-token.*` binding with compact-constructor validation (rejects non-positive `expirationDays`, and rejects `SameSite=None` paired with `cookie-secure=false`, which browsers drop outright).
- `RefreshResponse.java` — `(token, tokenType, expiresAt)`. No `userId`/`username`: the SPA already holds them.
- `InvalidRefreshTokenException.java` — fixed generic message, mapped to 401.

**Modified**

- `AuthController.java` — `RefreshTokenService` + `RefreshTokenProperties` injected; `attachRefreshCookie` helper (never sets a `Domain` attribute); `requireCsrfGateHeader` helper called as the first statement of `refresh`; `register`/`login` now attach a cookie with unchanged JSON bodies.
- `GlobalExceptionHandler.java` — seven lines mapping `InvalidRefreshTokenException` to a 401 `ApiError`, with a WARN naming the path and no token material.
- `SecurityConfig.java` — `setAllowCredentials(true)`, `X-Requested-With` allow-listed, both security comments rewritten.
- `application.properties` — new `# Refresh tokens` block; `app.jwt.expiration-ms` default `3600000` → `900000`.
- `application-prod.properties` — same JWT default change, with a comment that the Render dashboard value wins.
- `AuthControllerIT.java` — six new scenarios plus `JdbcTemplate`/`EntityManager` fields and two cookie-parsing helpers.
- `AuthControllerTest.java` — `RefreshTokenService` mock, real `RefreshTokenProperties` via `@TestConfiguration`, four new slice tests.
- `docs/auth.md` / `docs/api-contracts.md` — refresh endpoint contract, cookie attributes, and the SameSite/CSRF reasoning.

## Decisions Made

- **D-07: 30-day refresh lifetime, fixed from issuance.** `RESEARCH.md` flagged this as unspecified and told the planner to decide. Fixed-from-issuance beats sliding because rotation already keeps an active session alive indefinitely — so the number really answers "how long may a user be away before re-login", and a stolen-but-unused token must not be able to extend its own window by being refreshed.
- **`SameSite=None; Secure` with a header-based CSRF gate, overriding D-01's `Lax`/`Strict` preference.** The deployed frontend and backend are different `onrender.com` subdomains — cross-*site* for cookie purposes — so `Lax` would work perfectly on localhost and silently drop the cookie in production. That is the exact "unless research surfaces a reason otherwise" escape hatch D-01 wrote in.
- **Four extra slice tests beyond the plan.** The plan's verification is `AuthControllerIT`, which only runs under `-Pci`. On a repo where no machine runs Docker, that means the CSRF gate would have had no test a developer could run before pushing. Four `@WebMvcTest` tests close that.
- **Real `RefreshTokenProperties` in the slice, not a mock.** The cookie attributes *are* the assertion in `login_attachesHttpOnlyHostOnlyRefreshCookie`; stubbing `cookieSecure()`/`cookieSameSite()` would have made the test assert its own stubs.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `AuthControllerTest` could not construct `AuthController` after the new dependencies**
- **Found during:** Task 1
- **Issue:** `@WebMvcTest(AuthController.class)` constructs the real controller. Adding `RefreshTokenService` and `RefreshTokenProperties` to its constructor broke every existing test in that slice with an unsatisfied-dependency failure.
- **Fix:** `@MockitoBean RefreshTokenService` with a `@BeforeEach` stub for `issue(...)`, plus a nested `@TestConfiguration` supplying a real `RefreshTokenProperties(30, true, "None", "refresh_token", "/api/auth")`. Four new tests were added in the same file while it was open (see Decisions).
- **Files modified:** `backend/src/test/java/com/tripflow/backend/controller/AuthControllerTest.java`
- **Commit:** `7cb098a`

**2. [Rule 2 - Missing critical] Two grep-literal acceptance gates were tripped by explanatory comments**
- **Found during:** Tasks 1 and 2
- **Issue:** Two acceptance criteria are literal greps — `grep -Ec 'jakarta\.servlet|springframework\.http'` over `RefreshTokenService` must return 0, and `grep -c 'setAllowedOriginPatterns'` over `SecurityConfig` must return 0. Both initially returned 1, entirely from javadoc/comment prose that *named* the forbidden symbol in order to explain why it is forbidden.
- **Fix:** Reworded both comments to say the same thing without quoting the literal ("servlet and Spring HTTP types"; "the pattern-accepting variant"). The explanation was preserved; only the grep-visible token changed. Neither is a behavior change.
- **Files modified:** `RefreshTokenService.java`, `SecurityConfig.java`
- **Commits:** `7cb098a`, `ecdda8d`

**3. [CLAUDE.md directive] `docs/api-contracts.md` and `docs/auth.md` updated**
- **Found during:** Task 2
- **Issue:** The plan's `files_modified` list does not include the docs, but `CLAUDE.md` states `docs/api-contracts.md` is authoritative and must be updated when a contract changes. This plan adds an endpoint, a response body, a cookie, and a new required header.
- **Fix:** Added the refresh-cookie and `POST /api/auth/refresh` sections to `api-contracts.md`; added a "Refresh Tokens" section to `auth.md` and widened the `/api/auth/**` permitAll row to mention refresh and its missing rate limit. CLAUDE.md takes precedence over the plan's file list.
- **Commit:** `ecdda8d`

### Not a deviation, worth recording

The plan called for `RefreshTokenProperties` to be "registered the same way `JwtProperties` is registered (see `JwtConfig`)". That required a new 8-line `RefreshTokenConfig` class, which is not in the plan's `files_modified` list but is entailed by the instruction — `@ConfigurationPropertiesScan` is not enabled in this project.

## Issues Encountered

None blocking. The one thing worth flagging for future executors: **acceptance criteria written as literal greps will fire on comments.** Twice, the correct code tripped a gate purely because a comment explained the rule by naming the thing the rule forbids. Both times the right answer was to reword the comment, not delete it and not weaken the gate — but it is a five-minute trap that will recur in plan 01-03 if its criteria are written the same way.

## Verification Results

| # | Check | Result |
|---|---|---|
| 1 | `./mvnw.cmd -q test -Dtest=ArchitectureTest` | exit 0 — 6/6, `services_must_not_have_http_concerns` green |
| 2 | `./mvnw.cmd -q test` (full local unit suite, no Docker) | exit 0 — 38 report files, all `Failures: 0, Errors: 0`; context loads with credentialed CORS |
| 3 | `./mvnw.cmd -q test-compile` | exit 0 |
| 4 | `grep -Ec 'jakarta\.servlet\|springframework\.http' RefreshTokenService.java` | 0 (required 0) |
| 5 | `grep -c 'ResponseCookie' AuthController.java` | 2 (required >= 1) |
| 6 | `grep -Eic '\.domain(' AuthController.java` | 0 (required 0 — host-only cookie) |
| 7 | `git diff 07d257f..HEAD -- db/migration/` | only `V12__create_refresh_tokens.sql`; no V1–V11 file touched |
| 8 | `grep -c 'setAllowCredentials(true)'` / `(false)` in `SecurityConfig.java` | 1 / 0 (required 1 / 0) |
| 9 | `grep -c 'X-Requested-With' SecurityConfig.java` | 3 (required >= 1) |
| 10 | `grep -c 'setAllowedOriginPatterns' SecurityConfig.java` | 0 (required 0 — explicit list survived) |
| 11 | `grep -c '900000'` / `'3600000'` in `application.properties` and `application-prod.properties` | 1 / 0 in each (required 1 / 0) |
| 12 | `AuthControllerTest` (4 new slice tests) | 10/10 pass |
| 13 | `mvn -B verify -Pci` — the six `AuthControllerIT` scenarios | **CI only — not run locally.** No team machine runs Docker (CLAUDE.md), so Testcontainers `*IT` tests execute in GitHub Actions only. |
| 14 | Manual `curl` smoke against a local `spring-boot:run` | **Not performed.** Requires a local Postgres plus a populated `backend/.env`; the filesystem check for that file was declined in this session. Non-blocking per the plan, but it means the `Set-Cookie` header has been verified only through MockMvc, never against a real browser or a real Postgres `CHAR(64)` column. |

## Threat Model Outcome

| Threat ID | Disposition | Outcome |
|---|---|---|
| T-01-04 (Info Disclosure, token readable by injected script) | mitigate | Satisfied. Cookie is `HttpOnly`; the raw value is never placed in a JSON body (`login_refreshTokenValueIsNotInResponseBody`, CI). |
| T-01-05 (Info Disclosure, `refresh_tokens` in a DB dump) | mitigate | Satisfied. Only the SHA-256 hex digest is persisted (`refreshTokensTable_storesOnlyTheHash`, CI). |
| T-01-06 (Tampering, cross-site forced refresh) | mitigate | Satisfied. `X-Requested-With` required as the first statement of the handler; asserted locally by `refresh_withoutCustomHeader_returns400AndNeverCallsRotate`, which also verifies `rotate` is never reached. |
| T-01-07 (Info Disclosure, cookie leaking to PaaS tenants) | mitigate | Satisfied. No `Domain` attribute is ever set; source-gated (`.domain(` count 0) and asserted in both the slice and IT cookie tests. |
| T-01-08 (Spoofing, replay of a stolen token) | mitigate | Partially satisfied by design. `usedAt` is stamped on redemption and a replayed token is rejected as invalid — fail-closed. The D-03 mass-revoke compromise response is plan 01-03's. |
| T-01-09 (EoP, credentialed CORS opening the API) | mitigate | Satisfied. Explicit `setAllowedOrigins` list retained, `setAllowedOriginPatterns` source-gated to 0, and Spring's bean-creation failure on wildcard+credentials is the backstop. |
| T-01-10 (DoS, flooding `/api/auth/refresh`) | mitigate | **Deferred as planned to 01-03.** The endpoint is currently unauthenticated and unrate-limited. Tracked below under Known Stubs. |
| T-01-11 (Info Disclosure, token material in logs) | mitigate | Satisfied. Every log statement in `RefreshTokenService` and the new handler takes a user id or a path, never a token or hash. |
| T-01-12 (Tampering, supply chain) | accept | Satisfied. Zero new dependencies — `ResponseCookie`, `SecureRandom`, `MessageDigest`, `HexFormat` are all already on the classpath. |

**Threat surface scan:** one new endpoint (`POST /api/auth/refresh`), one new migration (V12), one new DTO, one new cookie, and a CORS policy change — all of which were in the plan's `<threat_model>` and are dispositioned above. No new surface outside the register.

## Known Stubs

Three, all deliberate and all owned by plan 01-03:

| Item | File | Reason |
|---|---|---|
| `POST /api/auth/refresh` has no rate limit | `AuthController.refresh` | Deferred by plan design (T-01-10). Rate limiting must be designed together with reuse-detection's mass-revoke, since a rate-limited endpoint that force-logs-out a target user is itself a DoS vector. |
| A replayed already-used token is rejected as generically invalid instead of triggering the D-03 mass-revoke | `RefreshTokenService.rotate` | The plan explicitly leaves this seam obvious. Current behavior is fail-closed, never fail-open. |
| `used_at` / `revoked_at` columns exist but `revoked_at` is never written | `V12__create_refresh_tokens.sql`, `RefreshToken` | Logout (D-04) and revocation land in 01-03. Pre-provisioned because migrations are append-only here. |

No `TODO`/`FIXME` markers, no skipped tests, no placeholder values that reach a response.

## User Setup Required

**Required before the deployed behavior matches this plan** (Render dashboard, backend service `tripflowai` → Environment):

1. **`JWT_EXPIRY_MS` → `900000`.** Prod sets this explicitly, so it overrides the new code default; without this change production keeps 1-hour access tokens and D-02 is not actually in effect.
2. **`CORS_ALLOWED_ORIGINS`** must list the exact deployed frontend origin (`https://tripflowai-frontend.onrender.com`) with no wildcard. Credentialed CORS paired with a wildcard fails at bean creation — the app will not start.
3. **`REFRESH_TOKEN_EXPIRY_DAYS`** — optional; omit to accept the 30-day default (D-07).

## Next Phase Readiness

- **Ready for 01-03.** Every seam that plan named exists: `RefreshTokenService.issue/rotate`, `RefreshTokenRepository` (add the `@Modifying` bulk-revoke query alongside `findByTokenHash`), and the `used_at`/`revoked_at` columns.
- **Carry-forward for 01-03:** the already-used branch in `rotate` is the single place the D-03 mass-revoke slots in; rate limiting on `/api/auth/refresh` and logout (D-04) are both still open, as is `RefreshResponse`'s lack of any revocation surface.
- **Carry-forward for 01-04 (frontend):** the SPA must send `X-Requested-With` on the refresh call and use `withCredentials: true`, or the request will be rejected at the gate or arrive with no cookie. Access tokens are now 15 minutes, so the D-05 proactive timer should be sized against that, not the old hour.
- **Risk R2 honored.** `SecurityConfig` was touched. The `permitAll` set was not changed (`/api/auth/**` already covers `/refresh`), and `authEndpoints_reachableWithoutBearerToken` still passes — but the CORS credentials change is browser-observable and cannot be caught by MockMvc, so a Postman/browser regression check against the deployed environment is warranted after merge.
- **Unverified in this session:** the manual `curl` smoke (row 14 above) and all six `AuthControllerIT` scenarios, which need CI.

## Self-Check: PASSED

- `backend/src/main/resources/db/migration/V12__create_refresh_tokens.sql` — FOUND
- `backend/src/main/java/com/tripflow/backend/domain/RefreshToken.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/repository/RefreshTokenRepository.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/config/RefreshTokenProperties.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/config/RefreshTokenConfig.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/dto/RefreshResponse.java` — FOUND
- `backend/src/main/java/com/tripflow/backend/exception/InvalidRefreshTokenException.java` — FOUND
- `.planning/phases/01-auth-seam-hardening/01-02-SUMMARY.md` — FOUND
- Commit `0f1d20f` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `7cb098a` — FOUND on `worktree-gsd-phase1-auth-seam`
- Commit `ecdda8d` — FOUND on `worktree-gsd-phase1-auth-seam`

---
*Phase: 01-auth-seam-hardening*
*Completed: 2026-08-14*
