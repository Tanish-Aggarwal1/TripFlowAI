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

Nothing extra needed. `SecurityConfig` denies all requests by default except `/api/auth/**` and `/actuator/health` (`permitAll`). Any new controller method automatically requires a valid JWT. To read the current user, add `@AuthenticationPrincipal UserPrincipal principal` as a method parameter and call `principal.userId()` / `principal.email()`.

## Environment Variables Required

- `JWT_SECRET` — signing key, set in `backend/.env`, never committed
- `JWT_EXPIRY_MS` — token lifetime in milliseconds

## Testing

- Unit: `JwtServiceTest`, `JwtAuthFilterTest`, `JsonAuthenticationEntryPointTest`, `JsonAccessDeniedHandlerTest`, `AuthServiceTest`
- Integration: `AuthControllerIntegrationIT` (register/login end-to-end), `TripControllerIT`'s `createTrip_withRealJwt_authenticatesThroughFilterAndPersists` (full filter-chain round trip with a real token)
- Slice: `AuthControllerTest` (`@WebMvcTest`, mocked `AuthService`, no Testcontainers needed)