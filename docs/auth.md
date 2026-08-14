# Authentication

## Overview

Stateless JWT-based authentication via Spring Security. No sessions, no server-side login state — every request carries its own bearer token, validated per-request by `JwtAuthFilter`.

## Token Flow

1. User registers (`POST /api/auth/register`) or logs in (`POST /api/auth/login`).
2. `AuthService` validates credentials (BCrypt password check on login), issues a JWT signed with HMAC-SHA256 via `JwtService`.
3. Client stores the token and sends it as `Authorization: Bearer <token>` on every subsequent request.
4. `JwtAuthFilter` (a `OncePerRequestFilter`) intercepts each request, validates the token, and — if valid — sets a `UserPrincipal` on the `SecurityContext`.
5. Token expires after `JWT_EXPIRY_MS` milliseconds (default 15 minutes — short because it is backed by silent refresh, below).

## Refresh Tokens

Register and login also set an httpOnly `refresh_token` cookie scoped to `Path=/api/auth`. The client never sees the value in JavaScript, and the server never stores it — only a SHA-256 hex digest lands in `refresh_tokens`.

`POST /api/auth/refresh` redeems that cookie once, returning a new access token and a rotated cookie; the presented value is marked used and is rejected on any later presentation. Full-reuse response (revoke every session for that user) and logout revocation are not implemented yet — a replayed token is currently rejected as invalid, which is fail-closed but does not yet raise the compromise signal.

The cookie is delivered `SameSite=None; Secure` rather than `Lax`/`Strict`, because the deployed frontend and backend are different subdomains of a shared PaaS suffix and are therefore cross-*site* — `Lax` would silently fail to attach the cookie in production while working fine on localhost. CSRF protection is instead carried by requiring a non-simple `X-Requested-With` header on `/api/auth/refresh`, checked before any token lookup: a cross-site form or image cannot set it, so the browser must preflight, and only an origin in `app.cors.allowed-origins` passes. This is why `corsConfigurationSource()` sets `setAllowCredentials(true)` and must keep using `setAllowedOrigins` (an explicit list) rather than `setAllowedOriginPatterns`.

Tunables: `app.refresh-token.expiration-days` (`REFRESH_TOKEN_EXPIRY_DAYS`, default 30, fixed from issuance not sliding), plus `REFRESH_COOKIE_SECURE` / `REFRESH_COOKIE_SAME_SITE` for local HTTP development only — production must not override those two.

## Unauthenticated / Unauthorized Responses

- **Missing, malformed, or expired token** on a protected endpoint → `401 Unauthorized`, JSON `ApiError` body, via `JsonAuthenticationEntryPoint` (added SCRUM-100/REF-11).
- **Valid token, but the request is forbidden by application logic** (e.g. not the trip owner) → `403 Forbidden`, JSON `ApiError` body, via `GlobalExceptionHandler`'s `ForbiddenException` handler — this is app-level business logic, not Spring Security's own authorization layer.
- **Valid token, but Spring Security's own authorization layer rejects it** (e.g. a future role/authority check) → `403 Forbidden`, JSON `ApiError` body, via `JsonAccessDeniedHandler` (added SCRUM-100/REF-11). Not currently reachable — no `.hasRole(...)`/`@PreAuthorize` rules exist yet — but wired and unit-tested for when they do.

Both JSON error paths return the same canonical `ApiError` shape documented in `docs/api-contracts.md`.

## Key Classes

- `SecurityConfig` (`security/`) — filter chain, permitted paths, `PasswordEncoder` bean, registers `JsonAuthenticationEntryPoint` + `JsonAccessDeniedHandler`
- `JwtService` (`security/`) — generate/parse/validate tokens; `extractUserId`, `extractEmail`
- `JwtAuthFilter` (`security/`) — per-request token extraction, constructs `UserPrincipal`, sets `SecurityContext`
- `UserPrincipal` (`security/`) — typed `UserDetails` implementation (`userId`, `email`), resolved in controllers via `@AuthenticationPrincipal UserPrincipal principal`
- `JsonAuthenticationEntryPoint` / `JsonAccessDeniedHandler` (`security/`) — JSON 401/403 responses, replacing Spring Security's default HTML responses
- `AuthService` / `AuthController` — register/login business logic

## How to Add a New Protected Endpoint

Nothing extra needed. `SecurityConfig` denies all requests by default. Any new controller method automatically requires a valid JWT.

The full `permitAll` set is:

| Path | Why it's public |
|---|---|
| `/api/auth/**` | Login, registration, and refresh — pre-authentication by definition (refresh authenticates on the cookie, not the access token, so it must be reachable once the access token has expired). Refresh is not rate-limited yet. Rate-limited via `app.ratelimit.login.*`/`app.ratelimit.register.*`, keyed on `HttpServletRequest.getRemoteAddr()` — per-client-IP in prod via `CF-Connecting-IP` (SCRUM-312), see the mechanism below. |
| `/api/discovery/**` | Public trip feed and search (`PUBLIC` visibility only). `TripSummaryResponse` carries no owner or user field, so nothing personal is exposed. |
| `/actuator/health` | Liveness probe. `management.endpoint.health.show-details=never`. |
| `/actuator/metrics`, `/actuator/metrics/**` | Exposed deliberately under SCRUM-174 (see `docs/risk-register.md`). **Note this is an unauthenticated read of `http.server.requests` — which enumerates every routed URI plus per-endpoint call counts and latency — as well as `jvm.memory.*` and `hikaricp.connections.*`.** If public metrics are no longer needed, the narrower option is a separate `management.server.port` that isn't publicly routed. |
| `/swagger-ui.html`, `/swagger-ui/**`, `/api-docs`, `/api-docs/**` | Dev/demo aid only — both are disabled in prod via `springdoc.swagger-ui.enabled=false` and `springdoc.api-docs.enabled=false` (`application-prod.properties`). |

**Auth rate limiting trust chain — what's configured (as of SCRUM-312, 2026-08-11).** `AuthController` keys the login/register bucket on `HttpServletRequest.getRemoteAddr()`. Render (where this app deploys) terminates TLS at a reverse proxy in front of the app, so without help `getRemoteAddr()` would see the proxy's address for every request, collapsing all clients into one bucket. `application-prod.properties` sets `server.forward-headers-strategy=native`, which enables Tomcat's `RemoteIpValve`. Render's platform edge is confirmed live to be Cloudflare (`Server: cloudflare` on every response) — SCRUM-297's original assumption (walk `X-Forwarded-For` right-to-left, stopping at the first entry outside `server.tomcat.remoteip.internal-proxies`' private-range default) broke against that hop, since Cloudflare's edge IP is public and the walk stopped there instead of reaching the real client. SCRUM-312 fixed this by pointing the valve at `server.tomcat.remoteip.remote-ip-header=CF-Connecting-IP` — Cloudflare's own single-value real-client-IP header, set by the edge itself — sidestepping the `X-Forwarded-For` chain-walk (and the `internal-proxies` tuning it depended on) entirely.

This was chosen deliberately over the more commonly recommended `server.forward-headers-strategy=framework` (Spring's `ForwardedHeaderFilter`): `framework` has no trusted-proxy allowlist and takes the *leftmost* `X-Forwarded-For` entry unconditionally, letting a client mint a fresh rate-limit bucket per request (a full bypass, fails open). `native` reading `CF-Connecting-IP` fails closed instead: the header is only ever set by Cloudflare's edge, and since Render's platform edge *is* Cloudflare, there's no direct-to-origin path a client could use to inject it — never switch to `framework` to make it "work faster," that's the insecure direction.

**One thing this doc does not assert as fact:**
- *Whether HSTS now engages in prod* — `RemoteIpValve` also derives `isSecure()` from `X-Forwarded-Proto`, and Spring Security's default `HstsHeaderWriter` (never overridden in `SecurityConfig`) is gated on that, so it *should* send `Strict-Transport-Security`. Not confirmed offline — depends on Boot's `native` strategy also populating `server.tomcat.remoteip.protocol-header`. If the header is missing after deploy, that property is the fix to check first.

Everything else falls through to `.anyRequest().authenticated()`. To read the current user, add `@AuthenticationPrincipal UserPrincipal principal` as a method parameter and call `principal.userId()` / `principal.email()`.

## Environment Variables Required

- `JWT_SECRET` — signing key, set in `backend/.env`, never committed
- `JWT_EXPIRY_MS` — token lifetime in milliseconds

## Testing

- Unit: `JwtServiceTest`, `JwtAuthFilterTest`, `JsonAuthenticationEntryPointTest`, `JsonAccessDeniedHandlerTest`, `AuthServiceTest`
- Integration: `AuthControllerIntegrationIT` (register/login end-to-end), `TripControllerIT`'s `createTrip_withRealJwt_authenticatesThroughFilterAndPersists` (full filter-chain round trip with a real token)
- Slice: `AuthControllerTest` (`@WebMvcTest`, mocked `AuthService`, no Testcontainers needed)