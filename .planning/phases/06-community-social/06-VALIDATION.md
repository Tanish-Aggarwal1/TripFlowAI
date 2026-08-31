---
phase: 06
slug: community-social
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-31
---

# Phase 06 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5 + Spring Boot Test — Surefire for `*Test.java` unit tests, Failsafe + Testcontainers for `*IT.java` integration tests under the `ci` Maven profile |
| **Framework (frontend)** | Karma + Jasmine (`ng test`) |
| **Config file** | `backend/pom.xml` (Surefire/Failsafe plugin config, unchanged); Angular CLI default `angular.json` test target |
| **Quick run command** | `mvnw verify` (backend, no Docker) / `npm test -- --watch=false` (frontend) |
| **Full suite command** | `mvnw verify -Pci` (backend, Testcontainers Postgres) / `npm run test:ci` (frontend) |
| **Estimated runtime** | ~90s backend unit / ~4min backend `-Pci` / ~60s frontend |

---

## Sampling Rate

- **After every task commit:** Run `mvnw verify` (backend) / `npm test -- --watch=false` (frontend)
- **After every plan wave:** Run `mvnw verify -Pci` + `npm run test:ci`
- **Before `/gsd-verify-work`:** Full suite must be green, plus a manual Postman/browser regression pass on the `SecurityConfig` auth change (RISK-R2 — a unit/MockMvc test alone does not satisfy this since the whole point is verifying real unauthenticated requests are now rejected)
- **Max feedback latency:** 240 seconds (full backend `-Pci` suite)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01-xx | 01 | — | SOCIAL-01 (frontend consumption) | — | N/A | component spec | `npm test -- --include='**/feed.page.spec.ts'` | ❌ W0 | ⬜ pending |
| 06-02-xx | 02 | — | SOCIAL-01 (swipe UI) | — | N/A | component spec | `npm test -- --include='**/feed-card.component.spec.ts'` | ❌ W0 | ⬜ pending |
| 06-03-xx | 03 | — | SOCIAL-02/03/04 | T-06-01 | Save/rate return 404 (not 403) on a private/foreign trip, matching SCRUM-274's existence-hiding convention | integration + component spec | `mvnw verify -Pci -Dtest=TripSaveServiceIT` / `npm test -- --include='**/feed-action-rail.component.spec.ts'` | ❌ W0 | ⬜ pending |
| 06-04-xx | 04 | — | SOCIAL-07 | — | Rating value bounded 1-5 at both Bean Validation and DB `CHECK` constraint layers | integration | `mvnw verify -Pci -Dtest=TripRatingServiceIT` | ❌ W0 | ⬜ pending |
| 06-05-xx | 05 | — | SOCIAL-05 | T-06-02 | Profile endpoints scope to `principal.userId()` only, never a path-supplied user id | unit + integration | `mvnw verify -Dtest=ProfileServiceTest` / `mvnw verify -Pci -Dtest=ProfileControllerIT` | ❌ W0 | ⬜ pending |
| 06-01/06-xx | 01/06 | — | SOCIAL-01 (auth), SOCIAL-06 (ranking) | T-06-03 | `/api/discovery/**` requires a valid JWT; PUBLIC trips with interest-tag overlap rank before non-matching, fallback to recency | integration | `mvnw verify -Pci -Dtest=DiscoveryFeedControllerIT` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*Exact task IDs are assigned by the planner; this table is seeded from 06-RESEARCH.md's Phase Requirements → Test Map and refined once PLAN.md files exist.*

---

## Wave 0 Requirements

- [ ] `DiscoveryFeedControllerIT.java` (new, or extends `DiscoveryControllerIT.java`) — covers SOCIAL-01 auth requirement + SOCIAL-06 ranking order
- [ ] `TripSaveServiceIT.java` — covers SOCIAL-04
- [ ] `TripRatingServiceIT.java` — covers SOCIAL-07
- [ ] `ProfileServiceTest.java` / `ProfileControllerIT.java` — covers SOCIAL-05
- [ ] `feed.page.spec.ts`, `feed-card.component.spec.ts`, `feed-action-rail.component.spec.ts` — new frontend component specs, no existing equivalent to extend
- [ ] No new test framework/config install needed — existing JUnit/Testcontainers (backend) and Karma/Jasmine (frontend) infrastructure covers this phase.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `/api/discovery/**` genuinely rejects an unauthenticated request end-to-end (not just via `MockMvc`'s security context simulation) | SOCIAL-01 | RISK-R2 — a `SecurityConfig` permitAll removal has previously required a real HTTP-level regression check in this project, not just an updated unit test, to catch config-vs-test divergence | Postman/browser: call `GET /api/discovery/trips` with no `Authorization` header against a running instance; confirm `401`, not `200` |
| Swiper.js nested vertical/horizontal gesture behavior feels correct on a real touch device | SOCIAL-01 (feed UX) | Nested-Swiper gesture-conflict mitigation (`nested: true`) is community-forum-sourced (RESEARCH.md Assumption A2), not confirmed against an official doc page for this exact configuration | Load the feed on a physical phone (or Chrome DevTools touch emulation at minimum) and confirm vertical trip-swipe and horizontal stop-swipe don't fight each other |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 240s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
