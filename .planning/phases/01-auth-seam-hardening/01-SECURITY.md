---
phase: 01
slug: auth-seam-hardening
status: verified
threats_open: 0
asvs_level: 1
created: 2026-08-18
---

# Phase 01 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| unauthenticated client -> Spring Security filter chain | Requests with no/expired/malformed bearer token reach `JsonAuthenticationEntryPoint` before the DispatcherServlet | none (rejection path) |
| authenticated client -> application authorization | A valid token belonging to a non-owner reaches `TripOwnershipService`, which raises `ForbiddenException` | trip ownership metadata |
| browser -> `POST /api/auth/refresh` / `/api/auth/logout` (cross-site) | Frontend and API are different origins; an httpOnly cookie crosses this boundary automatically on any credentialed request the browser is willing to make | refresh-token cookie (opaque to JS) |
| any web page on the internet -> `/api/auth/refresh` / `/api/auth/logout` | Both endpoints are `permitAll` and cookie-authenticated, reachable by any origin unless a control forces a preflight | refresh-token cookie, CSRF header |
| application -> `refresh_tokens` table | Long-lived session material at rest | SHA-256 hash of refresh token, user id, timestamps |
| browser JavaScript -> refresh cookie | httpOnly; app code can only influence whether a request is credentialed, never read the value | none (structurally blocked) |
| SPA -> cross-site API origin | Every credentialed call crosses a site boundary, subject to the backend's origin allowlist and preflight | access token (bearer), refresh cookie |
| expired-session UI -> user | Window between "refresh failed" and "user notices" | session status only |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-01-01 | Information Disclosure | `SecurityErrorWriter.write` error body | low | mitigate | `fieldErrors` passes `null`; body stays status/error/message/path/timestamp only | closed |
| T-01-02 | Spoofing | `TripController` current-user resolution | medium | mitigate | Reflection gate (`TripControllerIT`) fails the build if a controller method reverts to `Authentication`/`Principal` resolution — CI-only (Failsafe), not local `mvnw test` (UF-02, non-blocking) | closed |
| T-01-03 | Repudiation | 403 vs 404 disclosure on non-owned trips | low | accept | Deliberate, documented `docs/auth.md`; standardization tracked SCRUM-274 (Phase 6) | closed |
| T-01-04 | Information Disclosure | refresh token readable by injected script | high | mitigate | `HttpOnly` cookie; raw value never in a JSON body; `withCredentials` on login/register (CR-01 fix) makes the cookie actually persist | closed |
| T-01-05 | Information Disclosure | `refresh_tokens` rows in a DB dump | high | mitigate | Only SHA-256 hex digest persisted, no raw-token column in V12 | closed |
| T-01-06 | Tampering (CSRF) | cross-site forced call to `/api/auth/refresh` | high | mitigate | `X-Requested-With` required as first statement; forces CORS preflight, only allow-listed origin survives | closed |
| T-01-07 | Information Disclosure | cookie leaking to unrelated tenants on shared PaaS suffix | high | mitigate | No `Domain` attribute set — host-only cookie | closed |
| T-01-08 | Spoofing | replay of a stolen refresh token | high | mitigate | Superseded/strengthened by T-01-13's conditional-update redemption | closed |
| T-01-09 | Elevation of Privilege | credentialed CORS opening whole API to any origin | high | mitigate | Explicit env-bound `allowedOrigins` list, not patterns; wildcard+credentials fails bean creation | closed |
| T-01-10 | Denial of Service | unauthenticated flooding of `/api/auth/refresh` | medium | mitigate | Closed by T-01-14 (rate limit shipped in 01-03) | closed |
| T-01-11 | Information Disclosure | token material reaching application logs | medium | mitigate | All log sites parameterized on user id / row count only, never raw token or hash | closed |
| T-01-12 | Tampering | supply chain via new packages (01-02) | low | accept | No new dependency (verified via diff of pom.xml/package.json) | closed |
| T-01-13 | Spoofing | replay of a redeemed refresh token | high | mitigate | Conditional `markUsed` UPDATE, rowcount 0 -> mass revoke -> 401; `noRollbackFor` pinned by an out-of-transaction IT (WR-01, WR-03 fixes) | closed |
| T-01-14 | Denial of Service | attacker repeatedly replays a stale token to force-log-out a victim | high | mitigate | `/api/auth/refresh` rate limited 60/hour per client IP | closed |
| T-01-15 | Tampering (CSRF) | cross-site forced logout | medium | mitigate | Same custom-header gate as refresh | closed |
| T-01-16 | Repudiation | no trace of a compromise-signal revocation | medium | mitigate | WARN log, greppable `REFRESH_TOKEN_REUSE_DETECTED` marker, user id + row count, no token material | closed |
| T-01-17 | Information Disclosure | logout acting as a token-validity oracle | low | mitigate | Logout returns 204 uniformly regardless of token validity | closed |
| T-01-18 | Elevation of Privilege | logout widened to revoke sessions the caller does not hold | medium | mitigate | `revoke()` scoped strictly to presented token's hash; the guard's pre-fix widening (calling `logout()` on ordinary expiry) closed by CR-02 | closed |
| T-01-19 | Spoofing | access token still valid in 15-min window after mass revoke | medium | accept | Stateless JWT, accepted per RESEARCH.md, documented `docs/auth.md` as known residual | closed |
| T-01-20 | Tampering | supply chain via new packages (01-03) | low | accept | No new dependency | closed |
| T-01-21 | Information Disclosure | injected script exfiltrating the refresh token | high | mitigate | No code path reads the cookie; `HttpOnly` enforced structurally | closed |
| T-01-22 | Information Disclosure | credentials attached to third-party requests (Mapbox, Cloudinary) | high | mitigate | `withCredentials` set at 4 `AuthService` call sites only (was 2 at plan time, CR-01 added 2 more — deviation accepted, all 4 target the app's own `/api/auth` base URL), never a blanket interceptor | closed |
| T-01-23 | Spoofing | stale session appearing usable after refresh failure | medium | mitigate | Status flips on refresh failure/401; first interaction intercepted with blocking dialog; local session cleared on failed refresh (WR-05 fix) | closed |
| T-01-24 | Repudiation | logout that never reaches the server | medium | mitigate | `logout()` posts server-side revocation before clearing local state; local teardown still runs on failure | closed |
| T-01-25 | Denial of Service | refresh loop hammering endpoint after failure | medium | mitigate | Failed refresh does not re-arm timer; transport failures no longer read as expiry (WR-07 fix); backed by server-side rate limit | closed |
| T-01-26 | Tampering | CSRF header omitted, silently breaking flow | low | mitigate | Header set at both call sites, source-gated; backend 400s without it | closed |
| T-01-27 | Tampering | supply chain via new packages (01-04) | low | accept | No new dependency | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on (high) count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Unregistered Flags (non-blocking, surfaced during audit)

| ID | Finding | Why non-blocking |
|----|---------|-------------------|
| UF-01 | `RateLimiterService`'s bucket map (`ConcurrentHashMap`) is never evicted. This phase adds `"refresh:" + ip` as a third IP-keyed prefix on an endpoint reachable with no credentials at all — source-address rotation can allocate unbounded permanent buckets. | Memory-exhaustion surface, not a threat this register modeled; same finding as code-review WR-08, deliberately deferred (needs a new Caffeine dependency, judged too large for an auto-fix pass) |
| UF-02 | T-01-02's reflection gate lives in `TripControllerIT`, so it only runs under `-Pci` (Failsafe), not local `mvnw test` — costs a Testcontainers Postgres start for a test that does no I/O | The gate holds in CI, which is a required status check on `main` — mitigation is real, just weaker than "fails build" implies. Same finding as code-review IN-05 |

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-01-03 | 403-on-delete-by-non-owner vs 404-on-private-trip-view existence-hiding inconsistency is pre-existing, deliberate, documented in `docs/auth.md`; standardization tracked as SCRUM-274 under Phase 6 | Plan 01-01 author, confirmed at audit | 2026-08-18 |
| AR-02 | T-01-12, T-01-20, T-01-27 | No new dependency introduced across any of the 4 plans (verified via diff of pom.xml/package.json/package-lock.json) | Plan authors, confirmed at audit | 2026-08-18 |
| AR-03 | T-01-19 | Access tokens are stateless JWTs with no denylist; a mass-revoked session's already-issued access token remains valid for up to its 15-minute lifetime. Accepted per RESEARCH.md Open Question 2 — an access-token denylist is materially larger work, not requested anywhere in this phase | Plan 01-03 author, confirmed at audit | 2026-08-18 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-18 | 27 | 27 | 0 | gsd-security-auditor (secaudit-01), ASVS level 1, block_on: high |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-18
