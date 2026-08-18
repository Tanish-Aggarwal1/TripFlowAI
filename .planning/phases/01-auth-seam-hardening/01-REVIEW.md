---
phase: 01-auth-seam-hardening
reviewed: 2026-08-17T00:00:00Z
depth: standard
files_reviewed: 36
files_reviewed_list:
  - backend/README.md
  - backend/src/main/java/com/tripflow/backend/config/RefreshTokenConfig.java
  - backend/src/main/java/com/tripflow/backend/config/RefreshTokenProperties.java
  - backend/src/main/java/com/tripflow/backend/controller/AuthController.java
  - backend/src/main/java/com/tripflow/backend/domain/RefreshToken.java
  - backend/src/main/java/com/tripflow/backend/dto/RefreshResponse.java
  - backend/src/main/java/com/tripflow/backend/exception/GlobalExceptionHandler.java
  - backend/src/main/java/com/tripflow/backend/exception/InvalidRefreshTokenException.java
  - backend/src/main/java/com/tripflow/backend/ratelimit/RateLimitProperties.java
  - backend/src/main/java/com/tripflow/backend/repository/RefreshTokenRepository.java
  - backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java
  - backend/src/main/java/com/tripflow/backend/security/SecurityErrorWriter.java
  - backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java
  - backend/src/main/resources/application-prod.properties
  - backend/src/main/resources/application.properties
  - backend/src/main/resources/db/migration/V12__create_refresh_tokens.sql
  - backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java
  - backend/src/test/java/com/tripflow/backend/controller/AuthControllerTest.java
  - backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java
  - backend/src/test/java/com/tripflow/backend/security/JsonAccessDeniedHandlerTest.java
  - backend/src/test/java/com/tripflow/backend/security/JsonAuthenticationEntryPointTest.java
  - backend/src/test/java/com/tripflow/backend/service/RefreshTokenServiceTest.java
  - backend/src/test/resources/application-test.properties
  - docs/api-contracts.md
  - docs/auth.md
  - docs/deployment.md
  - frontend/src/app/app.component.html
  - frontend/src/app/app.component.spec.ts
  - frontend/src/app/app.component.ts
  - frontend/src/app/core/interceptors/session-expiry.interceptor.spec.ts
  - frontend/src/app/core/interceptors/session-expiry.interceptor.ts
  - frontend/src/app/core/models/auth.model.ts
  - frontend/src/app/core/services/auth.service.spec.ts
  - frontend/src/app/core/services/auth.service.ts
  - frontend/src/app/core/services/session-state.service.spec.ts
  - frontend/src/app/core/services/session-state.service.ts
findings:
  critical: 2
  warning: 8
  info: 9
  total: 19
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-08-17T00:00:00Z
**Depth:** standard
**Files Reviewed:** 36
**Status:** issues_found

## Summary

The server-side refresh-token implementation is the strongest part of this phase. Hash-only persistence, the ordering of the revoked/expired checks ahead of reuse detection, the host-only cookie, the `X-Requested-With` CSRF gate paired with credentialed CORS on an explicit origin list, and the deliberate `noRollbackFor` on the mass revoke are all correct and well argued in comments. `RefreshTokenServiceTest` and `AuthControllerIT` cover the revocation semantics thoroughly.

The failures are at the seams, and they are severe. **The feature does not work in a real browser as written.** Two independent defects each break it on their own:

1. `login()` and `register()` omit `withCredentials`, so the browser discards the `Set-Cookie` the backend just issued — the refresh cookie is never stored in the first place.
2. `authGuard` calls the now server-revoking `AuthService.logout()` the moment the access token lapses. With the access-token lifetime cut from 60 to 15 minutes in this same phase, this fires on the ordinary "user comes back after a coffee break" reload, and it destroys the 30-day refresh session instead of redeeming it.

Neither is caught by the test suite because `HttpTestingController` does not enforce browser cookie rules and no test exercises the guard against an expired token. The remaining findings cluster around a rotation race with no concurrency guard, unbounded row growth in `refresh_tokens`, a test that cannot fail if the load-bearing `noRollbackFor` regresses, and a session-expired dialog that loops on the login page.

## Critical Issues

### CR-01: Login and register never persist the refresh cookie — `withCredentials` missing

**File:** `frontend/src/app/core/services/auth.service.ts:26` and `:33`
**Issue:** `AuthController.register` and `AuthController.login` (`AuthController.java:60`, `:70`) both attach a `Set-Cookie: refresh_token=...` header. The frontend issues those two requests without `withCredentials: true`:

```ts
return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request).pipe(...)
```

A browser ignores `Set-Cookie` on the response to a **non-credentialed** cross-origin XHR/fetch. The frontend and backend are cross-origin in every environment this project ships: `localhost:8100` → `localhost:8080` in dev (`application-dev.properties` sets `app.cors.allowed-origins=http://localhost:8100`), and different PaaS subdomains in production — the exact fact the `SameSite=None` reasoning in `SecurityConfig.java:49-52` is built on.

Consequence: the cookie is silently dropped at login. `POST /api/auth/refresh` then always answers 401 (no cookie), `SessionStateService.renew()` marks the session `expired` roughly 14 minutes after every login, and the entire phase deliverable is inert. `refresh()` and `logout()` set the flag correctly (`:46`, `:62`), which makes the omission on the two endpoints that *issue* the cookie easy to miss.

The tests do not catch this: `auth.service.spec.ts:169` and `:240` assert `withCredentials` on refresh and logout only, and `HttpTestingController` never applies browser cookie-storage rules, so an integration-looking test would pass regardless.

**Fix:**
```ts
login(request: LoginRequest): Observable<AuthResponse> {
  return this.http
    .post<AuthResponse>(`${this.baseUrl}/login`, request, { withCredentials: true })
    .pipe(
      tap((res) => this.handleAuthSuccess(res)),
      catchError((err: HttpErrorResponse) => this.handleAuthError(err))
    );
}

register(request: RegisterRequest): Observable<AuthResponse> {
  return this.http
    .post<AuthResponse>(`${this.baseUrl}/register`, request, { withCredentials: true })
    .pipe(
      tap((res) => this.handleAuthSuccess(res)),
      catchError((err: HttpErrorResponse) => this.handleAuthError(err))
    );
}
```
Add `expect(req.request.withCredentials).toBeTrue()` to the existing login and register specs so the four cookie-carrying calls are pinned uniformly.

---

### CR-02: `authGuard` server-revokes the refresh session the moment the access token expires

**File:** `frontend/src/app/core/guards/auth.guard.ts:14` (in combination with `frontend/src/app/core/services/auth.service.ts:57-79` and `backend/src/main/resources/application.properties:31`)
**Issue:** `AuthService.logout()` changed in this phase from a local-storage clear into a call that also hits `POST /api/auth/logout`, which **revokes the refresh token server-side**. `authGuard` was not revisited:

```ts
if (authService.hasValidToken()) { return true; }
authService.logout();
return false;
```

`hasValidToken()` (`auth.service.ts:102`) is true only while the *access* token is unexpired. This phase shortened that from 1 hour to 15 minutes. So the normal flow is now:

1. User is idle 15+ minutes, or reloads the tab after being away.
2. `AuthService` is reconstructed; `storedTokenExpiry()` returns `null` for the lapsed JWT, so `expiresAt` seeds to `null`.
3. `SessionStateService.schedule(null)` returns at `session-state.service.ts:46` before arming any timer — no silent refresh is attempted.
4. First navigation hits `authGuard` → `hasValidToken()` false → `logout()` → the 30-day refresh token is revoked and the user is sent to `/login`.

This is precisely the scenario refresh tokens exist to handle, and the guard converts it into a permanent logout. Before this phase the same code was harmless (`logout()` was local-only and the window was 4x longer); the change to `logout()` made the pre-existing guard destructive.

**Fix:** the guard must attempt a refresh before tearing anything down, and must never call the revoking `logout()` for a merely-expired access token:

```ts
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.hasValidToken()) return true;

  return authService.refresh().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login'])))
  );
};
```
Pair this with a local-only teardown on the failure path (see WR-05) rather than `logout()`. Add a guard spec covering "expired access token, valid refresh cookie" — the case with no coverage today.

## Warnings

### WR-01: `rotate()` is a read-check-write with no concurrency guard

**File:** `backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java:79-101`
**Issue:** The `usedAt` check at `:91` and the stamp at `:100` are not atomic, and `RefreshToken` carries no `@Version`. Two concurrent refreshes presenting the same cookie both read `usedAt == null`, both stamp it, both call `issue()`. Two outcomes, both bad:

- The single-use invariant that the whole D-03 design rests on is broken — one presented token yields two live replacements.
- Under the reverse interleaving, the loser's legitimate retry lands after the winner's commit and trips `REFRESH_TOKEN_REUSE_DETECTED`, force-logging-out every device.

`docs/auth.md` acknowledges the multi-tab race ("two tabs share one cookie, so the slower tab's timer presents a value the faster tab already spent") but nothing mitigates it, and `SessionStateService` makes it likelier: the `visibilitychange` handler at `session-state.service.ts:21-27` can fire `renew()` in several tabs at the same instant when a user restores a window.

**Fix:** make the redemption a conditional update and treat a zero rowcount as reuse:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE RefreshToken rt SET rt.usedAt = :now WHERE rt.id = :id AND rt.usedAt IS NULL")
int markUsed(@Param("id") Long id, @Param("now") Instant now);
```
```java
if (refreshTokenRepository.markUsed(stored.getId(), Instant.now()) == 0) {
    // lost the race — another request already redeemed this exact token
    int revoked = refreshTokenRepository.revokeAllForUser(stored.getUserId(), Instant.now());
    log.warn("REFRESH_TOKEN_REUSE_DETECTED all sessions revoked userId={} revokedTokenCount={}",
            stored.getUserId(), revoked);
    throw new InvalidRefreshTokenException();
}
```
A `@Version` column on `RefreshToken` is the alternative, but requires a new migration and turns the race into an `OptimisticLockException` the caller must translate.

---

### WR-02: `refresh_tokens` rows are never pruned

**File:** `backend/src/main/resources/db/migration/V12__create_refresh_tokens.sql:12-21`
**Issue:** Nothing deletes from this table. Every login inserts a row, and rotation inserts another **without removing the redeemed one** (`RefreshTokenService.rotate:100-105` stamps `usedAt` and then `issue()`s a new row). With a 15-minute access token refreshed at the 14-minute mark, one continuously-open tab writes ~100 rows per day, retained for the 30-day token lifetime and then forever after that. `AuthControllerIT.refresh_replayOfAlreadyRotatedCookie...:445` asserts exactly this growth (2 rows after one rotation) without flagging it.

This is unbounded growth on a table whose only pruning signal — `expires_at` — is already stored. It also slowly degrades the `token_hash` unique-index lookup that sits on the hot path of every refresh.

**Fix:** add a scheduled cleanup, e.g. a `@Scheduled` daily delete plus a supporting index:

```java
@Modifying
@Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
```
Consider also deleting rows whose `usedAt` is older than a short grace window — a redeemed token only needs to be retained long enough for reuse detection to be meaningful, not for the full 30 days.

---

### WR-03: the D-03 integration test cannot fail if `noRollbackFor` regresses

**File:** `backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java:43` and `:422-446`
**Issue:** `RefreshTokenService.rotate:77` documents `noRollbackFor = InvalidRefreshTokenException.class` as "load-bearing, not tidiness" — without it the mass revoke is undone by the throw that follows. The test meant to pin that cannot detect its removal.

`AuthControllerIT` is annotated `@Transactional` at the class level, so the service's `@Transactional` joins the test-owned transaction. If `noRollbackFor` were deleted:

- The participating transaction would be marked rollback-only, not rolled back immediately.
- `unrevokedTokenCount()` (`:416`) reads through `JdbcTemplate` on the same bound connection, so it still observes the uncommitted revocations and the `isZero()` assertion at `:443` still passes.
- The Spring TestContext framework rolls back at test end rather than committing, so no `UnexpectedRollbackException` ever surfaces.

The test passes either way. The same caveat applies to `refresh_afterMassRevoke_evenTheRotatedCookieIsRejected:448`.

**Fix:** run the reuse-detection case outside the test-managed transaction so the revocation must survive a real commit:

```java
@Test
@org.springframework.transaction.annotation.Transactional(propagation = Propagation.NOT_SUPPORTED)
void refresh_replayOfAlreadyRotatedCookie_returns401AndRevokesAllUserTokens() throws Exception { ... }
```
with explicit `@AfterEach` cleanup of the rows it creates, or `@Commit` plus teardown. `RefreshTokenServiceTest` cannot substitute here — it mocks the repository, so transaction semantics are invisible to it.

---

### WR-04: cookie name is read from a hardcoded literal but written from a bound property

**File:** `backend/src/main/java/com/tripflow/backend/controller/AuthController.java:79` and `:99` vs `:132`
**Issue:** Reads use a literal:

```java
@CookieValue(name = "refresh_token", required = false) String rawRefreshToken
```

Writes use the property:

```java
ResponseCookie.from(refreshTokenProperties.cookieName(), value)
```

`app.refresh-token.cookie-name` is a real bound `@ConfigurationProperties` value (`application.properties:43`) and is therefore overridable by `APP_REFRESH_TOKEN_COOKIE_NAME` in any environment. Overriding it makes the server issue one cookie and look for another: `/api/auth/refresh` returns 401 for every request, `/api/auth/logout` silently revokes nothing while still answering 204, and no error is logged anywhere. `RefreshTokenProperties`'s compact constructor validates `expirationDays` and the `None`/`Secure` pairing precisely to avoid this class of silent-misconfiguration failure, so the gap is inconsistent with the file's own standard.

**Fix:** the property buys nothing here — drop `cookieName` from `RefreshTokenProperties` and use one `private static final String REFRESH_COOKIE = "refresh_token"` in `AuthController` for both directions. If the property must stay configurable, read the cookie manually:

```java
private String refreshCookieValue(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    return Arrays.stream(cookies)
            .filter(c -> refreshTokenProperties.cookieName().equals(c.getName()))
            .map(Cookie::getValue).findFirst().orElse(null);
}
```

---

### WR-05: a failed refresh leaves `isAuthenticated` true and a dead JWT in localStorage

**File:** `frontend/src/app/core/services/auth.service.ts:41-55`
**Issue:** `refresh()` has a success `tap` and no error path. When it fails, `SessionStateService.renew()` (`session-state.service.ts:60`) sets the status signal to `expired` and nothing else happens: `TOKEN_KEY` still holds the expired JWT, `isAuthenticated` is still `true`, and `expiresAt` still holds the stale value. `auth.service.spec.ts` pins this as intended behaviour ("propagates a 401 to the caller without clearing storage or navigating").

Consequences: `authInterceptor` keeps attaching the dead bearer token to every API call, producing a stream of 401s that `sessionExpiryInterceptor` re-marks as expired; and any component reading `auth.isAuthenticated()` renders signed-in UI for a session the app already knows is over. `authGuard` blocks navigation because it re-derives from `hasValidToken()`, which is what hides this today — but that is coincidence, not design.

**Fix:** extract the local-only teardown from `clearSession()` and run it on refresh failure, keeping the server call out of it (the refresh token is already known-dead, and this path must not become another revoke):

```ts
refresh(): Observable<RefreshResponse> {
  return this.http.post<RefreshResponse>(...).pipe(
    tap((res) => { ... }),
    catchError((err) => {
      this.clearLocalSession();   // storage + signals, no navigate, no server call
      return throwError(() => err);
    })
  );
}
```

---

### WR-06: the session-expired dialog re-opens on every click on the login page

**File:** `frontend/src/app/app.component.ts:32-50` with `frontend/src/app/core/services/auth.service.ts:73-79` and `session-state.service.ts:44-51`
**Issue:** `SessionStateService` never returns to `active` after a logout. `clearSession()` sets `expiresAt` to `null`; the effect calls `schedule(null)`, which returns at `:46` **before** the `sessionStatus.set('active')` on `:48`. The status stays `expired`.

So after the user clicks "Log in" in the expiry dialog:

1. `logout()` runs, `clearSession()` navigates to `/login`.
2. The alert dismisses; `onDidDismiss()` resets `expiryHandled = false` (`:49`).
3. The warning banner (`app.component.html:2`) is still rendered on the login page, because `sessionStatus()` is still `expired`.
4. The user's first click on the email field bubbles to `document`, `onDocumentClick` sees `expired` and `expiryHandled === false`, and presents another `backdropDismiss: false` alert whose only button calls `logout()` again.

The user is trapped in a modal loop over the login form and cannot type credentials — the only escape is that step 4 repeats until they happen to complete a login, which they cannot reach. This also makes `logIn()` (`:53`) ineffective: it sets `expiryHandled = true`, but `onDidDismiss` in the other handler clears it.

**Fix:** clearing the session must clear the status. Either move the `set('active')` above the early return and add an explicit reset, or expose one on the service:

```ts
// session-state.service.ts
reset(): void {
  this.clearTimer();
  this.sessionStatus.set('active');
}
```
called from `AuthService.clearSession()`. Add an `app.component.spec.ts` case asserting the banner disappears after logout.

---

### WR-07: the 8s availability timeout applies to silent refresh, and races the logout navigation

**File:** `frontend/src/app/core/interceptors/backend-availability.interceptor.ts:12` and `:31`
**Issue:** `EXEMPT_URL_PATTERNS` covers only `/ai-generate` and `/ai-suggest`, so `/api/auth/refresh` and `/api/auth/logout` both carry the 8-second `timeout()`. On Render's free tier — the documented cold-start problem this interceptor exists for (SCRUM-273) — a background silent refresh times out well inside 8s, and `SessionStateService.renew()`'s error handler marks the session `expired` even though the refresh token is entirely valid. The user is then bounced to `/starting-up` *and* shown the expiry banner, and per WR-06 that banner never clears.

Separately, `logout()`'s error path calls `clearSession()` → `router.navigate(['/login'])` while the interceptor's `catchError` fires `router.navigate(['/starting-up'], ...)` for the same failure. Two competing navigations on one failed request; which wins is timing-dependent.

**Fix:** treat transport failure as "unknown", not "expired":

```ts
const EXEMPT_URL_PATTERNS = ['/ai-generate', '/ai-suggest', '/api/auth/refresh'];
```
and in `SessionStateService.renew()`, only mark `expired` on an `HttpErrorResponse` with status 401, leaving the timer to re-arm (or retry) on a timeout / status 0.

---

### WR-08: the rate-limiter bucket map grows without bound, and this phase adds a third unauthenticated key prefix

**File:** `backend/src/main/java/com/tripflow/backend/ratelimit/RateLimiterService.java:22`
**Issue:** `private final ConcurrentHashMap<String, Bucket> buckets` is never evicted — the class javadoc says buckets are "held for the lifetime of the JVM". The keys for the authenticated endpoints are bounded by the user table, but `AuthController` keys on `HttpServletRequest.getRemoteAddr()`, and this phase adds `"refresh:" + ip` (`AuthController.java:82`) alongside the existing `login:`/`register:` prefixes.

`/api/auth/refresh` is reachable with no credentials at all — just the `X-Requested-With` header — so anything that can vary its source address (a botnet, a proxy pool, or simply IPv6 address rotation, where a single /64 allocation yields effectively unlimited distinct addresses) allocates a permanent `Bucket` per address in a single-instance JVM with `spring.datasource.hikari.maximum-pool-size=5`. The mechanism predates this phase, but refresh materially widens the surface it is reachable through.

**Fix:** back the map with an expiring cache rather than a plain map. Caffeine is the smallest change:

```java
private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(2))   // > the longest configured window
        .maximumSize(100_000)
        .build();
```
`expireAfterAccess` longer than the widest window keeps the limiter's semantics intact — an evicted bucket was necessarily idle for longer than its own refill period.

## Info

### IN-01: `@Value` field injection in `SecurityConfig`, inconsistent with the project's `@ConfigurationProperties` pattern

**File:** `backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java:73-74`
**Issue:** `@Value("${app.cors.allowed-origins}") private List<String> allowedOrigins;` is declared between two `@Bean` methods, mixing field injection into an otherwise constructor-injected class (`@RequiredArgsConstructor` at `:22`) and bypassing the `@ConfigurationProperties` record convention used by `RefreshTokenProperties`, `RateLimitProperties`, `OrsProperties` and `GeminiProperties`. There is also stray trailing whitespace on `:29`, `:72` and `:75`.
**Fix:** a `CorsProperties` record bound from `app.cors`, injected through the constructor, and the declaration moved out from between the bean methods.

### IN-02: `/api/auth/logout` is the only unauthenticated auth endpoint with no rate limit

**File:** `backend/src/main/java/com/tripflow/backend/controller/AuthController.java:97-110`
**Issue:** `register`, `login` and `refresh` all call `rateLimiterService.checkLimit(...)`; `logout` does not, despite being unauthenticated, reachable with only the CSRF header, and performing a SHA-256 plus an indexed lookup and a write per call.
**Fix:** either add an `app.ratelimit.logout.*` limit for symmetry, or record the deliberate omission in `docs/auth.md` next to the refresh-limit rationale.

### IN-03: logout with an already-redeemed token leaves the live successor alive

**File:** `backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java:119-128`
**Issue:** `revoke()` stamps whatever row the presented hash matches. If a client presents a token that was already rotated (a tab that lost the WR-01 race, or a stale cached request), the *replacement* token is untouched and the session survives — while the caller receives 204 and the cookie is cleared. The javadoc's "Ends exactly the session whose token was presented" is accurate but narrower than what a caller reasonably expects from logout.
**Fix:** if the matched row has a non-null `usedAt`, revoke its successor chain too — or document the limitation in `docs/auth.md`'s D-04 paragraph.

### IN-04: `docs/auth.md` names a test class that does not exist

**File:** `docs/auth.md` (Testing section, final lines)
**Issue:** Lists `AuthControllerIntegrationIT`; the class is `AuthControllerIT`. The section was edited in this phase to add `RefreshTokenServiceTest` but the stale name was carried through, and the new refresh/logout IT coverage and the frontend specs (`session-state.service.spec.ts`, `session-expiry.interceptor.spec.ts`) are not listed. The file also has no trailing newline.
**Fix:** correct the class name and add the new suites.

### IN-05: an architecture assertion is parked in a Testcontainers integration test

**File:** `backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java:412-420`
**Issue:** `controllers_resolveCurrentUserViaTypedPrincipal_notNameString` is pure reflection over `TripController.class` — no Spring context, no database, no HTTP. Living in an `*IT` means it runs only under `-Pci` and costs a Postgres container start for zero I/O, so a violation is invisible to `mvn verify` locally. It also inlines fully-qualified names (`java.util.Arrays`, `java.lang.reflect.Modifier`, `java.security.Principal`) rather than importing them, unlike the rest of the file, and the file ends without a newline.
**Fix:** move it to the ArchUnit suite alongside `services_must_not_have_http_concerns`, or to a plain `*Test` class.

### IN-06: `AuthControllerTest` mocks `RateLimitProperties`, so the new rate-limit wiring is asserted by nothing

**File:** `backend/src/test/java/com/tripflow/backend/controller/AuthControllerTest.java:83-84`
**Issue:** `@MockitoBean RateLimitProperties` returns `null` from `.login()`, `.register()` and `.refresh()`, and `RateLimiterService` is likewise mocked, so `checkLimit("refresh:" + ip, null)` is a silent no-op. Deleting the `checkLimit` call from `refresh()` would not fail any slice test. The class also has no logout cases at all, despite the comment at `:164` claiming these tests exist so "a regression fails a local build" — the CSRF gate on logout is covered only by `AuthControllerIT`, which needs Docker.
**Fix:** use the real `RefreshTokenProperties`-style `@TestConfiguration` approach already present at `:47` to supply a real `RateLimitProperties`, verify `checkLimit` is called with the expected key prefix, and add the two logout cases.

### IN-07: substring URL matching in the session-expiry interceptor

**File:** `frontend/src/app/core/interceptors/session-expiry.interceptor.ts:18`
**Issue:** `SELF_HANDLED_PATHS.some((path) => req.url.includes(path))` matches anywhere in the URL, including the query string, so a request such as `/api/trips?search=/api/auth/login` would be wrongly treated as self-handled and its 401 swallowed. Low reachability, but `startsWith` on the resolved path costs nothing.
**Fix:** compare against `new URL(req.url, location.origin).pathname` with `===`, or at minimum use `startsWith` against the full endpoint URL.

### IN-08: `RefreshTokenProperties` validates two fields and skips the two that fail silently

**File:** `backend/src/main/java/com/tripflow/backend/config/RefreshTokenProperties.java:21-33`
**Issue:** The compact constructor exists specifically to convert silent runtime cookie failures into startup failures, and correctly handles `expirationDays` and the `None`/`Secure` pairing. `cookieName` and `cookiePath` get no validation, yet a blank or misspelled value there produces exactly the symptom the class was written to prevent — a refresh cookie that never comes back, with no error anywhere. See WR-04 for the related read/write asymmetry.
**Fix:** reject blank `cookieName`, and reject a `cookiePath` that does not start with `/`.

### IN-09: missing trailing newlines on edited files

**File:** `backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java:96`, `backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java:422`, `docs/auth.md`
**Issue:** All three end without a trailing newline, which produces the `\ No newline at end of file` marker in every future diff that touches the last line.
**Fix:** add trailing newlines.

---

_Reviewed: 2026-08-17T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
