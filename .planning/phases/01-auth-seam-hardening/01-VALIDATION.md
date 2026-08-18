---
phase: 01
slug: auth-seam-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-14
---

# Phase 01 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Backend: JUnit 5 + Spring Boot Test + MockMvc. Frontend: Karma + Jasmine |
| **Config file** | `backend/pom.xml` (Surefire/Failsafe), `frontend/package.json` (`test`/`test:ci` scripts) |
| **Quick run command** | `.\mvnw.cmd test -Dtest=<ClassName>` (backend) / `npm test` (frontend, watch) |
| **Full suite command** | `.\mvnw.cmd verify -Pci` (backend, Docker/CI-only per CLAUDE.md) / `npm run test:ci` (frontend) |
| **Estimated runtime** | ~30s quick (unit), full CI suite runs in GitHub Actions only (no local Docker) |

---

## Sampling Rate

- **After every task commit:** Run targeted `.\mvnw.cmd test -Dtest=<touched class>` / `npm test` for touched files
- **After every plan wave:** `.\mvnw.cmd verify -Pci` (backend, CI-only — no team machine runs Docker locally) + `npm run test:ci`
- **Before `/gsd-verify-work`:** Full CI suite green (`.github/workflows/backend-ci.yml`, `frontend-ci.yml`)
- **Max feedback latency:** ~30s (unit tier); IT tier only runs in CI

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01-01 | 1 | AUTH-01 | T-01-* | `SecurityErrorWriter` returns `fieldErrors: null` (not `[]`) on 401/403, matching `docs/api-contracts.md` | unit | `.\mvnw.cmd test -Dtest=JsonAuthenticationEntryPointTest,JsonAccessDeniedHandlerTest` | ✅ (extend existing) | ⬜ pending |
| 01-01-02 | 01-01 | 1 | AUTH-03 | — | `deleteTrip_nonOwner_returns403`/`getTrip_nonExistentId_returns404` assert `$.status` body, not status-code-only | unit/IT | `.\mvnw.cmd test -Dtest=TripControllerIT` | ✅ (extend existing) | ⬜ pending |
| 01-02-01 | 01-02 | 1 | AUTH-04 | T-01-01..T-01-09 | Login sets `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=None`, hashed at rest (SHA-256, never raw) | unit + IT (tracer) | `.\mvnw.cmd verify -Pci -Dit.test=AuthControllerIT` | ❌ Wave 0 — extend `AuthControllerIT` | ⬜ pending |
| 01-02-02 | 01-02 | 1 | AUTH-04 | T-01-10..T-01-15 | `V12` migration creates `refresh_tokens` (token_hash, used_at, revoked_at, expiry); `RefreshTokenService` has no servlet/HTTP imports (ArchUnit `services_must_not_have_http_concerns`) | unit | `.\mvnw.cmd test -Dtest=RefreshTokenServiceTest,ArchitectureTest` | ❌ Wave 0 — new file | ⬜ pending |
| 01-03-01 | 01-03 | 2 | AUTH-04 | T-01-16..T-01-19 | Refresh with valid unused cookie rotates token, returns new access token | unit + IT | `.\mvnw.cmd test -Dtest=RefreshTokenServiceTest` | ❌ Wave 0 | ⬜ pending |
| 01-03-checkpoint | 01-03 | 2 | AUTH-04 | — | D-03 one-way reuse-detection policy — human confirms mass-revoke tradeoff before implementation | manual (`checkpoint:decision`) | N/A | N/A | ⬜ pending |
| 01-03-02 | 01-03 | 2 | AUTH-04 | T-01-20..T-01-24 | Reuse of an already-rotated token revokes ALL of that user's tokens, returns 401 | unit + IT | `.\mvnw.cmd test -Dtest=RefreshTokenServiceTest` | ❌ Wave 0 | ⬜ pending |
| 01-03-03 | 01-03 | 2 | AUTH-04 | T-01-25..T-01-27 | Logout revokes only the presented token, not other sessions | unit + IT | `.\mvnw.cmd test -Dtest=RefreshTokenServiceTest,AuthControllerIT` | ❌ Wave 0 | ⬜ pending |
| 01-04-01 | 01-04 | 3 | AUTH-04 | — | Frontend silent-refresh timer fires ~1min before `expiresAt`, re-arms on success, resumes correctly after `visibilitychange` | unit (Jasmine `fakeAsync`/`tick()`) | `npm test` | ❌ Wave 0 — new `session-state.service.spec.ts` | ⬜ pending |
| 01-04-02 | 01-04 | 3 | AUTH-04 | — | Two credentialed call sites (`AuthService`) set `withCredentials` explicitly — no blanket interceptor (avoids Mapbox/Cloudinary preflight breakage) | unit | `npm test` | ❌ Wave 0 — `auth.service.spec.ts` extension | ⬜ pending |
| 01-04-03 | 01-04 | 3 | AUTH-04 | — | On refresh failure: inline banner shows, next user interaction intercepts with session-expired dialog (D-06) | unit (state transitions) + manual (visual UAT) | `npm test` | ❌ Wave 0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `RefreshTokenServiceTest.java` — unit tests for issue/rotate/revoke/reuse-detection (mocked `RefreshTokenRepository`), covers AUTH-04 core logic
- [ ] Extend `AuthControllerIT.java` — cookie-attribute assertions on login, plus new `refresh`/`logout` end-to-end scenarios (reuse `PostgresTestcontainersConfiguration`, `persistUser()` helper)
- [ ] `session-state.service.spec.ts` — new file, Jasmine `fakeAsync`/`tick()` for timer behavior
- [ ] Extend `auth.service.spec.ts` for the two explicit `withCredentials` call sites (no new interceptor spec needed — planner deliberately dropped the interceptor approach, see 01-02-PLAN.md)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Session-expired banner renders inline (not a forced navigation) and the intercept dialog appears on next interaction | AUTH-04 (D-06) | Full E2E UX/visual flow needs real user-interaction timing; unit tests only cover the state transitions, not the rendered UX | Let access token expire with backend refresh disabled/mocked-failing; confirm banner appears without navigation; click any UI element; confirm session-expired dialog appears and routes to login |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s (unit tier)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
