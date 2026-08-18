# Phase 1: Auth Seam Hardening - Context

**Gathered:** 2026-08-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Auth boundary hardening: correct 401 vs 403 JSON `ApiError` responses, typed `UserPrincipal` resolution in controllers (retiring any string-parsed principal path), the four SCRUM-55 gap integration tests, and a full refresh-token flow (issuance, rotation, reuse-detection, revocation) with a frontend silent-refresh mechanism. No new capabilities beyond FB-01/02/03/16 scope.

</domain>

<decisions>
## Implementation Decisions

### Refresh token delivery & lifetime
- **D-01:** Refresh token delivered via httpOnly cookie (Set-Cookie on login/refresh), not JSON body. Mitigates XSS token theft; CSRF risk on `/api/auth/refresh` and `/api/auth/logout` should be covered by `SameSite=Strict` or `Lax` rather than a separate CSRF token scheme, unless research surfaces a reason otherwise.
- **D-02:** Access-token lifetime set to 15 minutes, backed by silent refresh. — **Reversibility:** costly — changing `JWT_EXPIRY_MS` after clients are issued longer-lived tokens requires a rollout window where both old and new lifetimes are honored, or forces re-login for all active sessions.

### Rotation & reuse policy
- **D-03:** Refresh tokens are single-use (rotated on every refresh). On reuse detection (an already-rotated/consumed token replayed), treat it as a compromise signal and revoke **all** of that user's refresh tokens (all devices), forcing re-login everywhere. — **Reversibility:** one-way — this is a security policy baked into the `refresh_tokens` schema/revocation logic (Plan 01-04); loosening it later to per-token revocation only is a behavior change other clients may come to depend on (e.g. "logout on phone doesn't affect desktop" expectations around reuse handling specifically, as opposed to normal logout below).

### Logout scope
- **D-04:** Logout revokes only the current device's refresh token (the one presented), not all of the user's sessions. Distinct from the reuse-detection case above, which intentionally revokes everything.

### Frontend refresh trigger & failure handling
- **D-05:** Frontend schedules a proactive silent-refresh timer that fires shortly before the 15-minute access-token TTL expires, rather than waiting for a 401 to trigger reactive refresh.
- **D-06:** When silent refresh fails (refresh token expired/revoked): stay on the current page and show an inline "session expired" banner — do NOT force-navigate immediately. However, if the user then attempts any further action (any click/interaction) after expiry, intercept it and show a "your session expired" dialog that leads to the login page. This means the auth interceptor/guard needs to distinguish "silent refresh just failed, sitting idle" from "user tried to do something post-expiry."

### Claude's Discretion
- Exact cookie attributes (`Path`, `Domain`, `Secure` flag per environment) — follow existing cookie/session conventions in the codebase and `docs/auth.md` if any exist; otherwise standard secure defaults (`Secure`, `HttpOnly`, `SameSite=Lax`, scoped `Path=/api/auth`).
- Whether CSRF token protection is additionally needed alongside `SameSite` — resolve during research/planning based on actual browser/client support requirements documented in `docs/auth.md`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Auth & API contracts
- `docs/auth.md` — full 401 vs 403 breakdown (`JsonAuthenticationEntryPoint` vs `JsonAccessDeniedHandler` vs app-level `ForbiddenException`), permitAll set and why each entry is public
- `docs/api-contracts.md` — canonical `ApiError` shape (`status`, `error`, `message`, `path`, `timestamp`, `fieldErrors`)
- `docs/TripFlow_fall_Break_Plan.md` — FB-01/FB-02/FB-03/FB-16 source task breakdown this phase implements

### Requirements
- `.planning/REQUIREMENTS.md` — AUTH-01, AUTH-02, AUTH-03, AUTH-04

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/src/main/java/com/tripflow/backend/security/UserPrincipal.java` — typed `UserPrincipal implements UserDetails` already exists; Plan 01-02's "introduce typed UserPrincipal" goal may already be substantially done — verify during research what's left (controllers still using string-parsed principal, if any).
- `backend/src/main/java/com/tripflow/backend/security/JsonAuthenticationEntryPoint.java` and `JsonAccessDeniedHandler.java` — already exist. Plan 01-01's 401/403 JSON `ApiError` distinction may already be implemented — research should verify coverage against the 4 SCRUM-55 gap scenarios rather than assume it needs to be built from scratch.
- `backend/src/main/java/com/tripflow/backend/security/JwtAuthFilter.java` (+ `JwtAuthFilterTest.java`) — existing JWT validation filter; refresh flow (01-04) will extend around this, not replace it.
- `backend/src/main/java/com/tripflow/backend/security/JwtService.java` — likely home for token issuance logic; refresh/rotation logic probably belongs here or a sibling service.

### Established Patterns
- Test helper `.asUser()` used across `*ControllerIT.java` (Trip, Stop, AiController, RouteOptimization, StopPhoto, TripExport) — reuse this pattern for the new SCRUM-55 gap tests (Plan 01-03).
- `SecurityConfig` denies all by default; new endpoints (`/api/auth/refresh`, `/api/auth/logout`) need explicit `permitAll`/auth wiring decisions — `refresh` likely needs to be reachable without a valid access token (only the refresh cookie), `logout` needs the current session.

### Integration Points
- No `AuthUtils` or `CurrentUserService` found in the codebase by those names — the "retire AuthUtils/CurrentUserService" framing in CLAUDE.md/ROADMAP may be stale or already resolved. Research (Plan 01-02) should confirm current state before planning removal work that may not be needed.
- No existing `refresh_tokens` migration found — Plan 01-04 starts from a clean slate for schema (new Flyway `V{n}__` migration, per project convention of never editing applied migrations).

</code_context>

<specifics>
## Specific Ideas

No specific UI/copy requirements given for the "session expired" banner or dialog — planner/researcher should follow existing frontend error/toast conventions.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 1-Auth Seam Hardening*
*Context gathered: 2026-08-11*
