# Authentication

## Overview

Stateless JWT-based authentication via Spring Security. No sessions, no server-side login state — every request carries its own bearer token, validated per-request by `JwtAuthFilter`.

## Token Flow

1. User registers (`POST /api/auth/register`) or logs in (`POST /api/auth/login`).
2. `AuthService` validates credentials (BCrypt password check on login), issues a JWT signed with HMAC-SHA256 via `JwtService`.
3. Client stores the token and sends it as `Authorization: Bearer <token>` on every subsequent request.
4. `JwtAuthFilter` (a `OncePerRequestFilter`) intercepts each request, validates the token, and — if valid — sets a `UserPrincipal` on the `SecurityContext`.
5. Token expires after `JWT_EXPIRY_MS` milliseconds (configured in `.env`, typically 1 hour in dev).

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
| `/api/auth/**` | Login and registration — pre-authentication by definition. Rate-limited via `app.ratelimit.login.*`/`app.ratelimit.register.*`, keyed on `HttpServletRequest.getRemoteAddr()` — **per-client-IP in prod only if the trust assumption below holds, see the caveat.** |
| `/api/discovery/**` | Public trip feed and search (`PUBLIC` visibility only). `TripSummaryResponse` carries no owner or user field, so nothing personal is exposed. |
| `/actuator/health` | Liveness probe. `management.endpoint.health.show-details=never`. |
| `/actuator/metrics`, `/actuator/metrics/**` | Exposed deliberately under SCRUM-174 (see `docs/risk-register.md`). **Note this is an unauthenticated read of `http.server.requests` — which enumerates every routed URI plus per-endpoint call counts and latency — as well as `jvm.memory.*` and `hikaricp.connections.*`.** If public metrics are no longer needed, the narrower option is a separate `management.server.port` that isn't publicly routed. |
| `/swagger-ui.html`, `/swagger-ui/**`, `/api-docs`, `/api-docs/**` | Dev/demo aid only — both are disabled in prod via `springdoc.swagger-ui.enabled=false` and `springdoc.api-docs.enabled=false` (`application-prod.properties`). |

**Auth rate limiting trust chain — what's configured vs. what's assumed (as of 2026-08-10).** `AuthController` keys the login/register bucket on `HttpServletRequest.getRemoteAddr()`. Render (where this app deploys) terminates TLS at a reverse proxy in front of the app, so without help `getRemoteAddr()` would see the proxy's address for every request, collapsing all clients into one bucket. `application-prod.properties` now sets `server.forward-headers-strategy=native`, which enables Tomcat's `RemoteIpValve`: it walks the `X-Forwarded-For` chain right-to-left and stops at the first address *not* matching `server.tomcat.remoteip.internal-proxies` (private ranges by default) — i.e. the real client address the load balancer appended, before any hop a client could have forged.

This was chosen deliberately over the more commonly recommended `server.forward-headers-strategy=framework` (Spring's `ForwardedHeaderFilter`): `framework` has no trusted-proxy allowlist and takes the *leftmost* XFF entry unconditionally — since proxies append rather than replace, a client-supplied leftmost value passes straight through, letting an attacker mint a fresh rate-limit bucket per request (a full bypass, fails open). `native`'s allowlist means a forged prefix is never reached — if the platform's proxy topology doesn't match the default private-range assumption, this degrades back to the single-bucket behavior rather than becoming spoofable (fails closed). If it needs tuning for Render's actual proxy IPs, the knob is `server.tomcat.remoteip.internal-proxies` — never switch to `framework` to make it "work faster," that's the insecure direction.

**Two things this doc does not assert as fact:**
- *Whether Render's edge actually appends to `X-Forwarded-For` (rather than overwriting it) and whether the intervening hops fall inside the default `internal-proxies` ranges* — this is the trust assumption the whole mechanism rests on and has not been verified against the live deployment. If it doesn't hold, the limiter is a single global bucket in prod, not per-client — verify with two real requests from different external IPs before relying on this for anything beyond defense-in-depth.
- *Whether HSTS now engages in prod* — `RemoteIpValve` also derives `isSecure()` from `X-Forwarded-Proto`, and Spring Security's default `HstsHeaderWriter` (never overridden in `SecurityConfig`) is gated on that, so it *should* start sending `Strict-Transport-Security` now. Not confirmed offline — depends on Boot's `native` strategy also populating `server.tomcat.remoteip.protocol-header`. If the header is missing after deploy, that property is the fix to check first.

Everything else falls through to `.anyRequest().authenticated()`. To read the current user, add `@AuthenticationPrincipal UserPrincipal principal` as a method parameter and call `principal.userId()` / `principal.email()`.

## Environment Variables Required

- `JWT_SECRET` — signing key, set in `backend/.env`, never committed
- `JWT_EXPIRY_MS` — token lifetime in milliseconds

## Testing

- Unit: `JwtServiceTest`, `JwtAuthFilterTest`, `JsonAuthenticationEntryPointTest`, `JsonAccessDeniedHandlerTest`, `AuthServiceTest`
- Integration: `AuthControllerIntegrationIT` (register/login end-to-end), `TripControllerIT`'s `createTrip_withRealJwt_authenticatesThroughFilterAndPersists` (full filter-chain round trip with a real token)
- Slice: `AuthControllerTest` (`@WebMvcTest`, mocked `AuthService`, no Testcontainers needed)