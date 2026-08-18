# Phase 1: Auth Seam Hardening - Research

**Researched:** 2026-08-14
**Domain:** Refresh-token issuance/rotation/reuse-detection (Spring Security 7 / Spring Boot 4.1) + Angular 20 proactive silent-refresh
**Confidence:** HIGH (backend schema/architecture, cross-origin cookie finding) / MEDIUM (exact refresh TTL, CSRF mitigation choice — flagged for confirmation)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Refresh token delivered via httpOnly cookie (Set-Cookie on login/refresh), not JSON body. Mitigates XSS token theft; CSRF risk on `/api/auth/refresh` and `/api/auth/logout` should be covered by `SameSite=Strict` or `Lax` rather than a separate CSRF token scheme, unless research surfaces a reason otherwise.
- **D-02:** Access-token lifetime set to 15 minutes, backed by silent refresh. — **Reversibility:** costly — changing `JWT_EXPIRY_MS` after clients are issued longer-lived tokens requires a rollout window where both old and new lifetimes are honored, or forces re-login for all active sessions.
- **D-03:** Refresh tokens are single-use (rotated on every refresh). On reuse detection (an already-rotated/consumed token replayed), treat it as a compromise signal and revoke **all** of that user's refresh tokens (all devices), forcing re-login everywhere. — **Reversibility:** one-way — this is a security policy baked into the `refresh_tokens` schema/revocation logic (Plan 01-04); loosening it later to per-token revocation only is a behavior change other clients may come to depend on (e.g. "logout on phone doesn't affect desktop" expectations around reuse handling specifically, as opposed to normal logout below).
- **D-04:** Logout revokes only the current device's refresh token (the one presented), not all of the user's sessions. Distinct from the reuse-detection case above, which intentionally revokes everything.
- **D-05:** Frontend schedules a proactive silent-refresh timer that fires shortly before the 15-minute access-token TTL expires, rather than waiting for a 401 to trigger reactive refresh.
- **D-06:** When silent refresh fails (refresh token expired/revoked): stay on the current page and show an inline "session expired" banner — do NOT force-navigate immediately. However, if the user then attempts any further action (any click/interaction) after expiry, intercept it and show a "your session expired" dialog that leads to the login page. This means the auth interceptor/guard needs to distinguish "silent refresh just failed, sitting idle" from "user tried to do something post-expiry."

**IMPORTANT — research surfaced a reason otherwise for D-01's SameSite assumption:** see Pattern 3 below. The frontend (`tripflowai-frontend.onrender.com`) and backend (`tripflowai.onrender.com`) are on different `onrender.com` subdomains, which is cross-*site* (not just cross-origin) for cookie purposes — `SameSite=Strict`/`Lax` will not attach the cookie to the refresh XHR in production. `SameSite=None; Secure` is required, with CSRF mitigated by a non-simple-request header gate instead of `SameSite`. This is exactly the escape hatch D-01 itself names ("unless research surfaces a reason otherwise") — flagged for the planner/user to confirm, not silently overridden.

### Claude's Discretion
- Exact cookie attributes (`Path`, `Domain`, `Secure` flag per environment) — follow existing cookie/session conventions in the codebase and `docs/auth.md` if any exist; otherwise standard secure defaults (`Secure`, `HttpOnly`, `SameSite=Lax`, scoped `Path=/api/auth`).
- Whether CSRF token protection is additionally needed alongside `SameSite` — resolve during research/planning based on actual browser/client support requirements documented in `docs/auth.md`.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

## Summary

01-01, 01-02, and 01-03 are confirmed shipped by the 2026-08-14 codebase audit (`.planning/REQUIREMENTS.md` marks AUTH-01/02/03 Done). **This research covers only 01-04 / AUTH-04: the refresh-token flow.**

The backend today is a textbook stateless-JWT API: `JwtAuthFilter` validates a bearer token per request, `SecurityConfig` runs `SessionCreationPolicy.STATELESS`, CSRF is disabled with an explicit comment ("no cookie-based session state exists anywhere in this app... revisit only if cookie-based auth is ever introduced"), and CORS has `setAllowCredentials(false)`. **D-01 (httpOnly cookie delivery) is exactly the condition that comment calls out** — three things in `SecurityConfig` must change together: CORS must allow credentials, CSRF must be reconsidered (not simply left disabled), and the disable-CSRF comment needs updating so a future reader doesn't think it's still accurate.

The most consequential finding is about deployment topology, not code: the frontend (`tripflowai-frontend.onrender.com`) and backend (`tripflowai.onrender.com`) are on **different subdomains of `onrender.com`**, and Render's own community forum confirms `onrender.com` is treated as a public-suffix-like boundary — subdomains cannot share cookies with each other, and are cross-*site*, not just cross-origin (`docs/deployment/frontend-setup.md:5-9`, `docs/deployment.md:37`; PSL-boundary claim is `[CITED: community-sourced, not confirmed against the raw PSL file this session]` — treat as needing empirical confirmation, but plan for the worst case regardless). This means **`SameSite=Strict` or `SameSite=Lax` — D-01's stated default — will silently fail to attach the refresh cookie to the `/api/auth/refresh` XHR/fetch call in production**, even though it will work fine in local dev (`localhost:8100` → `localhost:8080` are same-site, different origin, different ports). The only delivery option that works in both environments is **`SameSite=None; Secure`**, which in turn means **`SameSite` cannot be the CSRF mitigation** — D-01's own conditional ("unless research surfaces a reason otherwise") is triggered here. The recommended replacement is a **non-simple-request CSRF gate**: require a custom header (e.g. `X-Requested-With`) on `/api/auth/refresh` and `/api/auth/logout`, which forces a CORS preflight that only a page on an allowed origin can pass — cheap, no server-side CSRF token state, consistent with the app's existing stateless philosophy.

The second consequential finding is an ArchUnit rule already enforced in CI: `services_must_not_have_http_concerns` forbids anything in `..service..` from depending on `jakarta.servlet..` or `org.springframework.http..`. This means the new refresh-token service **cannot** build the `ResponseCookie` or touch `HttpServletRequest`/`HttpServletResponse` — that logic belongs in `AuthController`, with the service layer dealing only in plain token strings/records.

**Primary recommendation:** New `RefreshToken` domain entity (`refresh_tokens` table, `V12__create_refresh_tokens.sql`) storing only a SHA-256 hash of the raw token, single-use (`used_at`) with a simple "revoke all of this user's tokens" reuse-detection response (per D-03, no token-family/lineage tracking needed — the policy is user-wide, not family-scoped). `AuthController` builds and reads the `ResponseCookie`/`Cookie`; `RefreshTokenService` (new, in `service/`) does the rotation/reuse-detection logic and returns plain values. Frontend gets a `SessionStateService` driving a `setTimeout`-based proactive refresh timer plus a new functional interceptor (registered in `main.ts` alongside the three existing ones) that attaches `withCredentials: true` to auth-cookie-bearing requests.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-04 | Users can obtain a refresh token alongside their access token, silently refresh on expiry, and revoke sessions server-side on logout, with reuse-detection revoking the token family (FB-16) `[VERIFIED: .planning/REQUIREMENTS.md:17]` | Covered end-to-end by this document: schema (Code Examples), rotation/reuse logic (Pattern 2), cookie delivery (Pattern 3), frontend timer (Code Examples), and the Validation Architecture test map below. Note: "token family" in the requirement's own wording is resolved by CONTEXT.md D-03 to mean "all of the user's tokens," not a per-lineage family — see Pattern 2. |

AUTH-01/02/03 are out of scope for this research — already shipped and confirmed Done as of 2026-08-14 per `.planning/REQUIREMENTS.md:14-16`.
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Refresh-token issuance/rotation/reuse-detection | API / Backend | Database / Storage | Business logic + persisted state; must stay in `service/` per layered architecture, but per the ArchUnit rule below it must **not** touch HTTP types |
| httpOnly cookie Set-Cookie / cookie parsing | API / Backend (Controller only) | — | `ResponseCookie`/`HttpServletRequest` are HTTP concerns; `services_must_not_have_http_concerns` ArchUnit rule forbids this in `service/` |
| CORS credentialed-request policy | API / Backend (`SecurityConfig`) | — | Global filter-chain config, single source of truth |
| Proactive silent-refresh timer | Browser / Client | — | Must run relative to wall-clock token expiry inside the tab; no server push exists |
| "Session expired" banner + intercept-next-action | Browser / Client | Frontend Server (SSR) N/A — no SSR in this app | Angular is a client-rendered PWA; all UX state lives in `SessionStateService` (signal-based, same pattern as `AuthService.isAuthenticated`) |
| Access-token validation per request | API / Backend | — | Unchanged — `JwtAuthFilter` continues to own this |

## Package Legitimacy Audit

**No new external packages are required for this phase.** Backend: `ResponseCookie`/`Cookie` (Spring Web, already a transitive dep via `spring-boot-starter-webmvc`), `java.security.MessageDigest`/`java.security.SecureRandom` (JDK stdlib) for token hashing/generation — no new hashing library needed. Frontend: Angular's `HttpClient` already supports `withCredentials` per-request natively; no cookie-handling library needed (the browser manages the httpOnly cookie transparently — JS never reads it).

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| — | — | — | — | — | — | No packages to audit — this phase uses only JDK/Spring/Angular built-ins already in the dependency tree |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Standard Stack

No new libraries. Existing stack this phase extends:

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `jjwt-api`/`jjwt-impl`/`jjwt-jackson` | 0.13.0 `[VERIFIED: backend/pom.xml:20,142-158]` | Access-token signing/parsing (unchanged) | Already in use via `JwtService` |
| Spring Security | managed by `spring-boot-starter-parent` 4.1.0 (Spring Framework 7.0.8, Security 7.x line) `[CITED: Baeldung "Spring Boot 4 & Spring Framework 7", spring.io blog]` | Filter chain, CORS, stateless session policy | Already in use |
| `org.springframework.http.ResponseCookie` | bundled with `spring-boot-starter-webmvc` (no version pin needed — part of `spring-web`) | Building the `Set-Cookie` header with `HttpOnly`/`Secure`/`SameSite` attributes without hand-rolling header string concatenation | Spring's own typed cookie builder, avoids manually formatting `Set-Cookie` (a classic hand-roll trap — see Don't Hand-Roll below) |
| `java.security.MessageDigest` (`SHA-256`) | JDK 21 stdlib | Hash the raw refresh token before storing it | Never store a raw, presentable secret in the DB; SHA-256 is fast and appropriate here because the token itself is already high-entropy (unlike a user password) — bcrypt/Argon2 would be wasted CPU on every refresh call |
| `java.security.SecureRandom` | JDK 21 stdlib | Generate the raw refresh token (not JWT-encoded — a plain random secret is simpler and doesn't need to carry claims) | Cryptographically secure, no dependency needed |

**Installation:** none — no `pom.xml`/`package.json` changes required for the token-issuance mechanics themselves.

**Version verification:** `jjwt` version confirmed via `backend/pom.xml` read this session, not re-verified against the npm/Maven Central registry (unchanged dependency, no version bump needed for this phase).

## Architecture Patterns

### System Architecture Diagram

```
Angular SPA (tripflowai-frontend.onrender.com)
  │
  │ POST /api/auth/login {email,password}         (no credentials needed yet)
  ▼
AuthController.login()
  → AuthService.login()  [unchanged: password check via BCrypt]
  → JwtService.generateToken()        → 15-min access token (D-02)
  → RefreshTokenService.issue(userId) → raw refresh token (opaque, NOT a JWT)
  ← AuthController builds ResponseCookie(refresh_token, ..., HttpOnly, Secure,
     SameSite=None, Path=/api/auth) and adds it via response.addCookie(...)
  ← body: { token, tokenType, userId, username, expiresAt }  (unchanged shape)

Angular SPA stores access token in memory/localStorage (unchanged) and starts
a proactive refresh timer (SessionStateService) targeting ~1 min before expiresAt.

  │  ... 14 minutes later, timer fires ...
  ▼
POST /api/auth/refresh   (withCredentials: true → browser attaches the
                           httpOnly refresh_token cookie automatically;
                           custom header X-Requested-With forces a CORS
                           preflight — this IS the CSRF gate)
  ▼
AuthController.refresh(@CookieValue("refresh_token") String raw, HttpServletResponse res)
  → RefreshTokenService.rotate(raw)
       - hash raw with SHA-256, look up by hash
       - not found / expired / revoked           → 401 (clear cookie)
       - found, already used_at != null           → REUSE DETECTED (D-03):
             revoke ALL refresh_tokens for that user_id → 401
       - found, unused, not expired                → mark used_at = now(),
             issue new row + new raw token, return it
  ← new ResponseCookie (same attributes, new value/expiry)
  ← body: { token, tokenType, expiresAt }  (new short response DTO — no need
     to resend userId/username, the SPA already has them)

Silent-refresh failure (401 from /api/auth/refresh):
  → SessionStateService.markExpired() — sets a signal, does NOT navigate (D-06)
  → inline banner shown via existing ToastService-style pattern
  → next user interaction after that point is intercepted by a guard/directive
     that shows the "session expired" dialog → routes to /login

POST /api/auth/logout   (withCredentials: true, same CORS preflight gate)
  ▼
AuthController.logout(@CookieValue("refresh_token") String raw, HttpServletResponse res)
  → RefreshTokenService.revoke(raw)   [D-04: THIS token only, not all of the user's]
  ← Set-Cookie: refresh_token=; Max-Age=0  (clears the cookie)
```

### Recommended Project Structure

No new top-level folders — extends the existing layered structure:

```
backend/src/main/java/com/tripflow/backend/
├── domain/
│   └── RefreshToken.java              # extends BaseEntity — id/createdAt/updatedAt free
├── repository/
│   └── RefreshTokenRepository.java    # findByTokenHash, bulk-revoke @Modifying query
├── service/
│   └── RefreshTokenService.java       # issue/rotate/revoke — NO jakarta.servlet/http imports
├── dto/
│   └── RefreshResponse.java           # { token, tokenType, expiresAt } — no cookie fields
├── controller/
│   └── AuthController.java            # add refresh()/logout() — owns ResponseCookie + @CookieValue
├── exception/
│   ├── InvalidRefreshTokenException.java   # 401 — missing/expired/revoked/malformed cookie
│   └── (GlobalExceptionHandler: new @ExceptionHandler entry, same pattern as InvalidCredentialsException)
└── security/
    └── (unchanged — JwtAuthFilter/JwtService/SecurityConfig get targeted edits, not new files)

backend/src/main/resources/db/migration/
└── V12__create_refresh_tokens.sql

frontend/src/app/core/
├── services/
│   ├── auth.service.ts                # add refresh()/logout() calls with withCredentials: true
│   └── session-state.service.ts       # NEW — signal-based: 'active' | 'refreshing' | 'expired'
└── interceptors/
    └── refresh-credentials.interceptor.ts  # NEW — clones auth-cookie-bearing requests with
                                              # withCredentials: true; registered in main.ts
```

### Pattern 1: Service layer stays HTTP-agnostic (ArchUnit-enforced)

**What:** `RefreshTokenService` never imports `jakarta.servlet.*` or `org.springframework.http.*`. It takes/returns plain values (`String` raw token, a small record `IssuedToken(String rawToken, Instant expiresAt)`), never a `ResponseCookie` or `HttpServletResponse`.
**When to use:** Always, for this codebase — it's a CI-enforced rule, not a style preference.
**Example (rule that will fail the build if violated):**
```java
// Source: backend/src/test/java/com/tripflow/backend/ArchitectureTest.java:63-66 (read this session)
@ArchTest
static final ArchRule services_must_not_have_http_concerns = noClasses()
        .that().resideInAPackage("..service..")
        .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..", "org.springframework.http..")
        .because("the service layer must stay free of HTTP-specific types — that's the controller's job");
```
Cookie-building and `@CookieValue` extraction belong in `AuthController`, mirroring how `AuthController` already owns `HttpServletRequest` for the rate-limiter key (`AuthController.java:36,44` — `httpRequest.getRemoteAddr()`), never delegated into `AuthService`.

### Pattern 2: Single-use rotation + user-wide reuse revocation (not per-family)

**What:** Every `refresh_tokens` row has `used_at` (nullable). On `/api/auth/refresh`: look up by `token_hash`; if `used_at IS NOT NULL`, this is replay of an already-rotated token → revoke every row for that `user_id` (D-03 is explicitly user-wide, not per-lineage, which simplifies the schema — no `family_id`/`replaced_by_id` chain needed).
**When to use:** This specific policy — D-03 already resolved the tradeoff (simpler schema, coarser blast radius) so there's no design decision left here, just implementation.
**Example:**
```java
// Illustrative shape — not copy-paste from an external source, follows this
// codebase's existing @Modifying pattern (TripLikeRepository, read this session).
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP "
         + "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId);
}
```
```java
// RefreshTokenService — no jakarta.servlet/http imports (Pattern 1)
@Transactional
public IssuedToken rotate(String rawToken) {
    String hash = hash(rawToken); // SHA-256, see Code Examples
    RefreshToken existing = repository.findByTokenHash(hash)
            .orElseThrow(InvalidRefreshTokenException::new);

    if (existing.getRevokedAt() != null || existing.getExpiresAt().isBefore(Instant.now())) {
        throw new InvalidRefreshTokenException();
    }
    if (existing.getUsedAt() != null) {
        // ponytail: user-wide revoke is the D-03-mandated ceiling — a compromised
        // token nukes every device's session, not just the stolen lineage. Upgrade
        // to per-family revocation only if a future decision narrows the blast radius.
        repository.revokeAllForUser(existing.getUserId());
        throw new InvalidRefreshTokenException();
    }

    existing.setUsedAt(Instant.now());
    repository.save(existing);
    return issue(existing.getUserId()); // new row + new raw token
}
```

### Pattern 3: Cross-site cookie delivery (the load-bearing finding of this research)

**What:** `SameSite=None; Secure; HttpOnly; Path=/api/auth` — not `Strict`/`Lax`. CSRF mitigated by requiring a custom header on refresh/logout (forces CORS preflight, which only an allowed origin can pass), not by `SameSite`.
**When to use:** Both `/api/auth/refresh` and `/api/auth/logout`.
**Why (verbatim source of the topology claim):**
```
docs/deployment/frontend-setup.md:5-9
## Deployed URL
`https://tripflowai-frontend.onrender.com`
Deployed against the live backend at `https://tripflowai.onrender.com`
```
```
docs/deployment.md:37
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins | `https://tripflowai.app` |
```
Two different registrable-looking hostnames under `onrender.com` — Render's own community forum states cookies cannot be shared across `*.onrender.com` subdomains (public-suffix-like behavior) `[CITED: Render community discourse — not independently re-verified against the raw Public Suffix List this session; a WebFetch attempt against the raw PSL file did not conclusively confirm or deny an `onrender.com` entry]`. **Treat this as the safe-default assumption regardless of confirmation status**: `SameSite=None` degrades gracefully to "works" whether or not `onrender.com` is actually a PSL entry, whereas `SameSite=Lax`/`Strict` silently breaks refresh in prod if it is (and the CI/local dev environment, same-origin-port-only, would never catch this — it would only surface after a real prod deploy).
**Example (backend cookie construction — Controller only, per Pattern 1):**
```java
// Source: illustrative — spring-web's ResponseCookie API (part of spring-boot-starter-webmvc,
// no version pin needed). SameSite/Secure/HttpOnly builder methods are standard Spring Framework 6/7 API.
ResponseCookie cookie = ResponseCookie.from("refresh_token", issued.rawToken())
        .httpOnly(true)
        .secure(true)               // required for SameSite=None; also correct for prod HTTPS
        .sameSite("None")
        .path("/api/auth")          // scoped so it's never sent to /api/trips/** etc.
        .maxAge(Duration.between(Instant.now(), issued.expiresAt()))
        .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
```
**CORS config change required (`SecurityConfig.java:78`, read this session):**
```java
// backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java:75-78 (current, read this session)
// Stateless bearer-token API — no cookies, no session (SessionCreationPolicy.STATELESS above).
// Nothing needs credentialed CORS; keeping it false also fails closed if allowedOriginPatterns
// (which permits wildcards alongside credentials, unlike setAllowedOrigins) is ever adopted here.
config.setAllowCredentials(false);
```
This must become `config.setAllowCredentials(true)` — the comment is now stale and must be rewritten, not just the value flipped. `setAllowedOrigins` (not `setAllowedOriginPatterns`) is already used, which is the correct/required pairing with `allowCredentials(true)` (Spring CORS throws at runtime if you combine `allowCredentials(true)` with a wildcard origin).
**CSRF config change required (`SecurityConfig.java:38-50`, read this session):** the existing `.csrf(AbstractHttpConfigurer::disable)` comment explicitly says "revisit only if cookie-based auth is ever introduced" — that condition is now true. Recommendation (Claude's discretion per CONTEXT.md): **keep CSRF disabled in Spring Security's own terms**, but add the custom-header requirement as a lightweight application-level gate (a small `OncePerRequestFilter` or a check inside `AuthController.refresh()`/`logout()` that 400s if `X-Requested-With` is absent) — full `CookieCsrfTokenRepository` synchronizer-token machinery is unwarranted for two low-blast-radius endpoints in a JSON-only API with strict CORS origin allowlisting.

### Anti-Patterns to Avoid

- **Storing the raw refresh token in the DB:** Only ever store `SHA-256(rawToken)`. A DB read (backup leak, SQL injection, insider access) must not itself hand out a working session token.
- **Reusing `JwtService`/JWT format for the refresh token:** The refresh token doesn't need to carry claims (no `email`, no `userId` visible client-side) — an opaque random secret is simpler to invalidate (DB row deletion/flag, not "wait for JWT expiry") and doesn't need signature verification, just a hash lookup.
- **Cookie `Domain=.onrender.com`:** Never set an explicit `Domain` attribute pointing at the parent suffix — that would leak the cookie to every other `*.onrender.com`-hosted app (a real cross-tenant security bug on a shared PaaS). Leave `Domain` unset (host-only cookie, scoped to the exact backend hostname that set it).
- **`SameSite=Strict` "because it's most secure":** Breaks the proactive refresh entirely in prod for this specific cross-subdomain topology — not a hypothetical, see Pattern 3.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| `Set-Cookie` header string formatting (attribute escaping, ordering) | Manual `"refresh_token=" + value + "; HttpOnly; ..."` string concat | `org.springframework.http.ResponseCookie` (already a transitive dep, zero new install) | String concat is a classic header-injection and malformed-attribute source; `ResponseCookie` validates/encodes for you |
| Refresh-token secret generation | `UUID.randomUUID()` or `Math.random()`-based strings | `java.security.SecureRandom` + Base64URL encoding, 256 bits | `UUID.randomUUID()` uses a CSPRNG in modern JVMs but isn't documented as one — `SecureRandom` is the explicit, auditable choice for a security-sensitive secret |
| Silent-refresh scheduling drift (tab backgrounded, laptop sleep) | Hand-rolled `setInterval` polling loop that assumes wall-clock ticks reliably | A single `setTimeout` computed from `expiresAt - now()`, re-armed on each successful refresh, **plus** a check-on-resume via the `visibilitychange` event (if the tab was backgrounded past expiry, the timer callback fired late or not at all — re-validate on visibility resume rather than trusting a stale timer) | `setInterval` drifts and double-fires across sleep/wake; a single self-rearming `setTimeout` plus a resume-check is the standard fix and needs no new dependency |

**Key insight:** Every piece of this phase has a JDK/Spring/Angular built-in that already does the hard part correctly — the phase-specific work is almost entirely *wiring* (where does the cookie get built, where does the timer live, what does reuse-detection revoke), not algorithm implementation.

## Common Pitfalls

### Pitfall 1: CORS `allowCredentials(true)` + wildcard origin throws at startup
**What goes wrong:** Spring Security throws `IllegalArgumentException` at `CorsConfigurationSource` bean creation if `allowCredentials(true)` is paired with `setAllowedOrigins(List.of("*"))` or any origin pattern containing `*`.
**Why it happens:** The CORS spec forbids `Access-Control-Allow-Origin: *` alongside `Access-Control-Allow-Credentials: true` (a browser-enforced security rule Spring validates eagerly).
**How to avoid:** `SecurityConfig` already uses `setAllowedOrigins(allowedOrigins)` bound from `app.cors.allowed-origins` (`[VERIFIED: SecurityConfig.java:66-72]` — explicit list, no wildcard) — this is already correct, just flip `allowCredentials` to `true` and leave the origin list mechanism untouched.
**Warning signs:** App fails to start (bean creation error), not a runtime CORS rejection — will surface immediately in `mvn spring-boot:run`/CI, not silently in prod.

### Pitfall 2: `withCredentials: true` set globally breaks non-cookie requests
**What goes wrong:** If every `HttpClient` request gets `withCredentials: true` (e.g. via a blanket interceptor with no URL gating), requests to third-party APIs the frontend also calls directly (Mapbox, Cloudinary — confirmed present via `auth.interceptor.spec.ts:62-70`, which tests exactly this exclusion for the *Authorization* header) may get unexpected credential/cookie behavior or trigger unnecessary preflights.
**Why it happens:** Copy-pasting the new interceptor without the same origin-gating the existing `authInterceptor` already does (`isApiRequest = req.url.startsWith(environment.apiBaseUrl)`).
**How to avoid:** Gate the new credentials-interceptor on the identical `isApiRequest` check already proven in `auth.interceptor.ts:10`.
**Warning signs:** Mapbox/Cloudinary requests start failing CORS preflight in the browser console after this phase ships.

### Pitfall 3: Reuse-detection revoke query races with a legitimate concurrent refresh
**What goes wrong:** Two tabs both hold the same (now-rotated-once) refresh token cookie value (e.g. user had two tabs open before the first tab's silent refresh fired) — the second tab's refresh request looks like "reuse" even though it's the same legitimate user, not an attacker.
**Why it happens:** httpOnly cookies are shared across all tabs of the same browser profile for the same origin — rotation in tab A invalidates the cookie tab B is still holding, and tab B's next silent-refresh attempt (up to ~15 min later) will look identical to a stolen-token replay.
**How to avoid:** This is an inherent tradeoff of single-use rotation + multi-tab apps, not a bug to "fix" — D-03 already accepted it (forces re-login on all devices, which includes re-login in the other tab of the *same* device). Document it in the plan's UAT/verification notes so a tester doesn't mistake "second tab got logged out" for a regression; it's the designed behavior. No code changes reduce this — a `BroadcastChannel`/`localStorage` cross-tab token-sync mechanism *would* fix it but is out of this phase's scope (not requested in CONTEXT.md, and D-05/D-06 don't mention multi-tab coordination).
**Warning signs:** QA report says "logging in on tab 1 logged me out of tab 2 a few minutes later" — expected, not a bug, given D-03.

### Pitfall 4: Rate-limiting the refresh endpoint by IP collapses behind Render's proxy the same way login/register almost did
**What goes wrong:** If `/api/auth/refresh` reuses the IP-keyed Bucket4j pattern from `login`/`register` (`AuthController.java:36,44`) naively, it inherits the exact proxy/CDN-IP collapse bug SCRUM-297/SCRUM-312 already fixed for login/register (`docs/auth.md:46-48`, read this session) — but *only if* the new endpoint is added before confirming `CF-Connecting-IP` resolution still applies. It already does (`server.tomcat.remoteip.remote-ip-header=CF-Connecting-IP` is a global Tomcat valve setting, not per-endpoint), so this is a non-issue **as long as** the refresh rate limit reuses `httpRequest.getRemoteAddr()` the same way login/register do — flagging only because a keyed-by-refresh-token-hash alternative (also viable, arguably better since it ties the limit to a specific session rather than an IP shared by many users behind NAT) would need its own reasoning, not a copy-paste assumption.
**How to avoid:** Either reuse the proven IP-keyed pattern (`RateLimiterService.checkLimit("refresh:" + httpRequest.getRemoteAddr(), ...)`, consistent with existing `login`/`register` calls) or explicitly key by the refresh token's hash — both are defensible; pick one and don't mix.
**Warning signs:** N/A — this is a design-consistency note, not an observed bug.

## Code Examples

### `V12__create_refresh_tokens.sql`
```sql
-- Source: pattern verified against V1__create_users.sql and V8__create_stop_photos.sql
-- (both read this session) for the exact PK/timestamp/FK convention this repo uses.
-- Stores only a SHA-256 hash of the raw refresh token — never the raw value (see
-- "Don't Hand-Roll" / Anti-Patterns above).
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  CHAR(64) NOT NULL UNIQUE, -- hex-encoded SHA-256 digest, 64 chars
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ, -- set once redeemed via rotation (single-use, D-03)
    revoked_at  TIMESTAMPTZ, -- set on logout (this token only, D-04) or on
                              -- reuse-detected mass revoke (all rows for user, D-03)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```
`token_hash` already gets a unique index for free from the `UNIQUE` constraint (Postgres convention already relied on elsewhere in this schema — no separate `CREATE INDEX` needed for it, matching how `users.email`/`users.username` don't get a redundant explicit index either, `[VERIFIED: V1__create_users.sql:1-8]`).

### Raw token generation + hashing (backend)
```java
// Illustrative — java.security stdlib, no new dependency.
private static final SecureRandom RNG = new SecureRandom();

private String generateRawToken() {
    byte[] bytes = new byte[32]; // 256 bits
    RNG.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
}

private String hash(String rawToken) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 must be available on every JVM", e);
    }
}
```

### Frontend: proactive timer (SessionStateService, new file)
```typescript
// Illustrative — matches this codebase's existing signal-based service pattern
// (AuthService.isAuthenticated in auth.service.ts:19, read this session).
@Injectable({ providedIn: 'root' })
export class SessionStateService {
  readonly status = signal<'active' | 'refreshing' | 'expired'>('active');
  private refreshTimer?: ReturnType<typeof setTimeout>;
  private http = inject(HttpClient);

  scheduleRefresh(expiresAt: string): void {
    clearTimeout(this.refreshTimer);
    const msUntilRefresh = new Date(expiresAt).getTime() - Date.now() - 60_000; // 1 min buffer
    this.refreshTimer = setTimeout(() => this.doRefresh(), Math.max(msUntilRefresh, 0));
  }

  private doRefresh(): void {
    this.status.set('refreshing');
    this.http
      .post<{ token: string; expiresAt: string }>(
        `${environment.apiBaseUrl}/auth/refresh`,
        {},
        { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } },
      )
      .subscribe({
        next: (res) => {
          this.status.set('active');
          // ... persist new access token via AuthService, re-arm timer with res.expiresAt
        },
        error: () => this.status.set('expired'), // D-06: stay put, banner only
      });
  }
}
```
**Note:** the `X-Requested-With` header is exactly what makes this a non-simple CORS request (forces preflight) — see Pattern 3. It must be added to `SecurityConfig.corsConfigurationSource()`'s `setAllowedHeaders(...)` list (`SecurityConfig.java:74`, currently `List.of("Authorization", "Content-Type")` — needs `X-Requested-With` appended) or the preflight itself will fail for legitimate calls.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| Single long-lived JWT (1h, `JWT_EXPIRY_MS=3600000` today) with no refresh mechanism | 15-min access token + rotating refresh token in httpOnly cookie | This phase (D-02/D-03) | `app.jwt.expiration-ms` default changes from `3600000` to `900000`; `JWT_EXPIRY_MS` env var in Render dashboard must be updated for prod, not just the code default (`docs/deployment.md:30` lists it as a required env var — a code-only change won't take effect in prod without also updating the dashboard value, since the property is `${JWT_EXPIRY_MS:3600000}` and prod always has the env var set) |
| CORS `allowCredentials(false)`, CSRF unconditionally disabled | CORS `allowCredentials(true)`, CSRF disabled-but-gated by custom header on 2 endpoints | This phase (D-01 + Pattern 3 finding) | `SecurityConfig`'s stale "no cookies anywhere" comment must be rewritten, not just the boolean flipped |

**Deprecated/outdated:** None yet — this is a net-new capability, not a migration off something.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `onrender.com` behaves as a public-suffix-like boundary, making the frontend/backend subdomains cross-*site* (not just cross-origin) | Pattern 3 / Summary | If actually same-site, `SameSite=Lax` would have worked and `SameSite=None` is a strictly-safer superset choice anyway — **low risk even if wrong**, since `None` works in both cases. Kept as the recommendation regardless. |
| A2 | Refresh-token lifetime (not specified in CONTEXT.md D-01–D-06 or REQUIREMENTS.md) — no verified value | Code Examples / Standard Stack | No number is given anywhere in canonical docs; the planner must pick one (common ranges: 7–30 days) and treat it as a new locked decision, not infer one from this research |
| A3 | Custom-header (`X-Requested-With`) CSRF gate is sufficient given `SameSite=None`, in place of full CSRF-token machinery | Pattern 3 | If a reviewer wants defense-in-depth beyond CORS-preflight gating (e.g. for ASVS V4/V12 compliance stricter than what this app currently practices anywhere else), a synchronizer-token (`CookieCsrfTokenRepository`) approach would need to replace this — flagged in Security Domain below |
| A4 | Rate limiting `/api/auth/refresh` by IP (reusing the login/register pattern) rather than by refresh-token identity | Pitfall 4 | Low risk either way — both are defensible; planner should pick one explicitly rather than leave it unaddressed, since an unrated refresh endpoint that also triggers reuse-detection-driven mass-revocation is itself a denial-of-service vector (repeatedly replaying a stale token to force-logout a target user) if left completely unlimited |

## Open Questions (RESOLVED)

1. **Refresh token lifetime** — RESOLVED: 30 days, fixed from issuance. See `01-02-PLAN.md` `<new_decision>` D-07 (`app.refresh-token.expiration-days` / `REFRESH_TOKEN_EXPIRY_DAYS`).
   - What we know: Access token is locked at 15 min (D-02). No refresh-token lifetime is specified anywhere in CONTEXT.md, REQUIREMENTS.md, or `docs/`.
   - What's unclear: 7 days? 30 days? Sliding vs. fixed expiry from issuance?
   - Recommendation: Planner should surface this as an explicit new decision (30 days, fixed from issuance, is a defensible capstone-project default) rather than silently picking a number — it's a security-relevant parameter CONTEXT.md's "Claude's Discretion" section doesn't cover.

2. **Whether the reuse-detection mass-revoke should also force-expire already-issued access tokens** — RESOLVED: accepted as residual risk, not mitigated further. See `01-03-PLAN.md` threat T-01-19.
   - What we know: D-03 revokes all *refresh* tokens on reuse detection. Access tokens are stateless JWTs valid until their own (15-min) expiry regardless of refresh-token state.
   - What's unclear: Is a ≤15-min window where a compromised-but-still-valid access token keeps working after reuse-detection fires acceptable, or does this need an access-token denylist?
   - Recommendation: Given the 15-min TTL is already short and D-03/D-06 don't mention an access-token denylist, treat the existing behavior (access token simply expires naturally within 15 min) as sufficient — flagging only so the planner makes this an explicit, documented tradeoff rather than an unnoticed gap.

## Environment Availability

No new external tool/service dependency — this phase is entirely code + one Flyway migration against the existing local PostgreSQL 16 instance already required for backend dev (`backend/.env.example` setup, unchanged).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Spring Boot Test + MockMvc (`[VERIFIED: backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java:1-37]`) |
| Backend config | `backend/pom.xml` Surefire (`*Test.java`, no Docker) / Failsafe under `-Pci` (`*IT.java`, Testcontainers Postgres) |
| Frontend framework | Karma + Jasmine (`frontend/package.json` `test`/`test:ci` scripts) |
| Quick run (backend) | `.\mvnw.cmd test -Dtest=RefreshTokenServiceTest` |
| Quick run (frontend) | `npm test` (watch) or `npm run test:ci` |
| Full suite | `.\mvnw.cmd verify -Pci` (backend, Docker/CI-only per CLAUDE.md) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-04 | Login sets `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=None` | unit + IT (cookie attributes are only visible via a real filter-chain response, similar to how `AuthControllerIT` tests JWT end-to-end) | `.\mvnw.cmd verify -Pci -Dit.test=AuthControllerIT` | ❌ Wave 0 — extend existing `AuthControllerIT` |
| AUTH-04 | Refresh with a valid, unused cookie rotates the token and returns a new access token | unit (`RefreshTokenServiceTest`, mocked repository) + IT (full round trip via MockMvc with `.cookie(...)`) | `.\mvnw.cmd test -Dtest=RefreshTokenServiceTest` | ❌ Wave 0 — new file, follow `AuthServiceTest.java` pattern |
| AUTH-04 | Reuse of an already-rotated (used) refresh token revokes all of that user's tokens and returns 401 | unit + IT | `.\mvnw.cmd test -Dtest=RefreshTokenServiceTest` | ❌ Wave 0 |
| AUTH-04 | Logout revokes only the presented token, not the user's other sessions | unit + IT | same as above | ❌ Wave 0 |
| AUTH-04 | Frontend: silent-refresh timer fires ~1 min before `expiresAt` and re-arms on success | unit (Jasmine, `fakeAsync`/`tick()`) | `npm test` | ❌ Wave 0 — new `session-state.service.spec.ts` |
| AUTH-04 | Frontend: on refresh failure, banner shows and next interaction shows the intercept dialog (D-06) | unit (component/service spec) — full E2E UX flow is `manual-only` since it needs real user-interaction timing | `npm test` for the unit-testable state transitions; manual UAT for the visual banner/dialog | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** targeted `mvnw test -Dtest=...` / `npm test` for touched files
- **Per wave merge:** `.\mvnw.cmd verify -Pci` (backend — requires Docker, CI-only per CLAUDE.md, so this step realistically only runs in GitHub Actions, not locally) + `npm run test:ci`
- **Phase gate:** Full CI suite green (`.github/workflows/backend-ci.yml`, `frontend-ci.yml`) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `RefreshTokenServiceTest.java` — unit tests for issue/rotate/revoke/reuse-detection (mocked `RefreshTokenRepository`), covers AUTH-04 core logic
- [ ] Extend `AuthControllerIT.java` — cookie-attribute assertions on login, plus new `refresh`/`logout` end-to-end scenarios (reuse existing `PostgresTestcontainersConfiguration`, `persistUser()` helper)
- [ ] `session-state.service.spec.ts` — new file, Jasmine `fakeAsync`/`tick()` for timer behavior
- [ ] Extend or add an interceptor spec for the new credentials-interceptor, following `auth.interceptor.spec.ts`'s exact `provideHttpClient(withInterceptors([...]))` + `HttpTestingController` pattern

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT bearer (unchanged) + new opaque refresh-token secret, hashed at rest |
| V3 Session Management | yes | This is the core of the phase — httpOnly cookie, rotation, reuse-detection, explicit revocation on logout |
| V4 Access Control | no change | `TripOwnershipService` app-level 403s unaffected |
| V5 Input Validation | yes (minor) | `@CookieValue` extraction — malformed/missing cookie must map to a clean 401 via `InvalidRefreshTokenException`, never a stack-trace 500 |
| V6 Cryptography | yes | `SecureRandom` for token generation, `SHA-256` for at-rest hashing — never store the raw token (see Anti-Patterns) |
| V13 (implicit, CSRF) | yes | Custom-header preflight gate in place of `SameSite` (Pattern 3) — flagged in Assumptions Log A3 as needing sign-off if stricter ASVS conformance is wanted than "CORS preflight as CSRF defense" |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Refresh-token theft via XSS | Information Disclosure | `HttpOnly` cookie — JS (and thus any XSS payload) cannot read it, per D-01's own stated rationale |
| Refresh-token replay after theft | Spoofing / Repudiation | Single-use rotation + reuse-detection mass-revoke (D-03) |
| CSRF-triggered refresh/logout | Tampering / Denial of Service | Non-simple-request CORS preflight gate (custom header), strict origin allowlist (already in place, `app.cors.allowed-origins`) |
| Cookie leak to unrelated `*.onrender.com` tenants | Information Disclosure | No explicit `Domain` attribute on the cookie (host-only scoping) — see Anti-Patterns |
| Raw refresh token recovered from a DB dump/backup | Information Disclosure | Only `SHA-256(rawToken)` is ever persisted |

## Sources

### Primary (HIGH confidence)
- `backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java` — read this session, CORS/CSRF current state
- `backend/src/main/java/com/tripflow/backend/security/JwtService.java`, `JwtAuthFilter.java`, `JwtProperties.java`, `UserPrincipal.java` — read this session
- `backend/src/test/java/com/tripflow/backend/ArchitectureTest.java` — read this session, the `services_must_not_have_http_concerns` rule
- `backend/src/main/resources/db/migration/V1__create_users.sql`, `V8__create_stop_photos.sql`, `V9__create_trip_likes.sql` — read this session, migration numbering (highest existing V11) and schema conventions
- `backend/src/main/java/com/tripflow/backend/repository/TripLikeRepository.java` — read this session, `@Modifying` bulk-update pattern
- `frontend/src/main.ts` — read this session, interceptor registration point/order
- `frontend/src/app/core/services/auth.service.ts`, `interceptors/auth.interceptor.ts`, `interceptors/session-expiry.interceptor.ts` — read this session
- `frontend/src/environments/environment.ts`, `environment.prod.ts` — read this session, confirms `apiBaseUrl` is a build-time-injected absolute URL, not same-origin-relative
- `docs/deployment.md`, `docs/deployment/frontend-setup.md` — read this session, confirms the two-subdomain deployment topology
- `docs/auth.md`, `docs/api-contracts.md`, `.planning/phases/01-auth-seam-hardening/01-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/config.json` — read this session

### Secondary (MEDIUM confidence)
- Baeldung, "Spring Boot 4 & Spring Framework 7 – What's New" and spring.io's "Spring Boot 4.0.0 available now" blog post — Spring Boot 4.1 built on Spring Framework 7.0.8 / Spring Security 7.x line

### Tertiary (LOW confidence)
- Render community discourse thread on cross-subdomain cookie behavior on `onrender.com` — informs the Pattern 3 recommendation but the underlying PSL-entry claim was not independently confirmed against the raw Public Suffix List file this session (a WebFetch attempt did not conclusively resolve it either way). The recommendation (`SameSite=None`) is deliberately chosen to be correct regardless of how this resolves.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries, all patterns verified against files read this session
- Architecture (cookie delivery, ArchUnit constraint, schema): HIGH — cross-checked against actual source files, not training-data assumption
- Cross-site cookie topology finding: MEDIUM — the *consequence* (use `SameSite=None`) is safe regardless; the *cause* (onrender.com PSL status) is CITED, not VERIFIED
- Pitfalls: HIGH — derived directly from reading the actual CORS/rate-limit/ArchUnit code, not generic JWT-refresh-token folklore
- Refresh token lifetime, exact CSRF-gate strictness: MEDIUM/LOW — genuinely unresolved decisions, flagged in Assumptions Log and Open Questions for the planner/discuss-phase to close

**Research date:** 2026-08-14
**Valid until:** 30 days (stable backend stack) — but re-verify the `onrender.com` cross-site cookie behavior empirically (e.g. `curl -v` the real deployed `/api/auth/login` and inspect `Set-Cookie` + a follow-up cross-origin `fetch` from the deployed frontend) before shipping to prod, since that's the one CITED-not-VERIFIED claim this whole design leans on.
