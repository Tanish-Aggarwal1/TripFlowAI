# TripFlowAI

## What This Is

TripFlowAI is an AI-powered multi-stop trip planning PWA: Ionic + Angular frontend, Spring Boot backend, PostgreSQL, Mapbox for maps, OpenRouteService (VROOM) for route optimization, and Google Gemini for AI itinerary suggestions. It's a capstone (SDP) project — the semester-5 presentation happened 2026-08-06; work now moves into a fall-break window (Aug 17 2026 – early Jan 2027) and then a winter term before final grading/presentation.

## Core Value

AI-assisted, route-optimized multi-stop itineraries that feel like a real, usable travel product — not just a CRUD demo. If the AI-suggestion and route-optimization flows don't work well, nothing else compensates.

## Current Milestone: v1.0 Semester 5 → Semester 6 Fall Break Implementation

**Goal:** Take the app from a working capstone MVP (presented 2026-08-06) through fall break and winter term to a polished, socially-featured, production-hardened product ready for final presentation/grading.

**Target features:**
- Auth hardening (typed 401/403, `UserPrincipal`, refresh tokens)
- Exports, completion tracking, search/filter
- AI itinerary quality pass (day/time/meal-aware scheduling)
- Frontend/map polish (route rendering, dark mode, place search)
- Notifications, observability, PWA offline viewing, native build spike
- Community/social layer (discovery feed, like, clone, save, ratings)
- Foreground trip tracking
- Winter production hardening and final sign-off

See `.planning/ROADMAP.md` for the 8-phase breakdown and `.planning/RISKS.md` for the full risk register, including live Jira reconciliation findings.

## Requirements

### Validated

<!-- Inferred from codebase map (.planning/codebase/), existing and working as of 2026-08-06 -->

- ✓ Trip CRUD (create/list/get/update/delete) with owned-resource access control — existing
- ✓ Multi-stop route optimization via OpenRouteService/VROOM, with a heuristic day/time scheduler — existing
- ✓ AI itinerary suggestions and full AI trip generation via Gemini — existing
- ✓ Stateless JWT auth (login/register), default-deny `SecurityConfig` — existing
- ✓ Stop photo upload via Cloudinary — existing
- ✓ Calendar (.ics) export — existing
- ✓ Manual stop-visited tracking (`Stop.status`) — existing
- ✓ Rate limiting (Bucket4j) on optimize/AI endpoints — existing
- ✓ CI (backend + frontend), Flyway-managed schema, Testcontainers IT suite (CI-only, no local Docker) — existing

### Active

<!-- Fall-break scope (docs/TripFlow_fall_Break_Plan.md, FB-01..FB-26) and winter scope (docs/TripFlow_Winter_Plan.md, WP-01..WP-08) -->

- [ ] Auth hardening: typed 401 vs 403 entry point, `UserPrincipal` typed seam, refresh token flow (FB-01, FB-02, FB-03, FB-16)
- [ ] Export features: PDF itinerary export, calendar export polish, trip completion percentage (FB-04, FB-05, FB-06)
- [ ] Search and filter on trip list (FB-07)
- [ ] AI quality: Gemini prompt engineering pass, day/time/reasoning + meal-aware itinerary scheduling, alternative-suggestion popups (FB-08, FB-17, FB-18)
- [ ] Notifications: signup confirmation + trip reminder emails (FB-09)
- [ ] Frontend polish: advanced Mapbox route rendering/clustering, dark mode, Mapbox place search for stop input, editable stop coordinates (FB-10, FB-11, FB-15)
- [ ] Observability: Sentry error tracking integration (FB-12)
- [ ] PWA: offline caching for saved trips (read-only), native Capacitor build spike (FB-13, FB-14)
- [ ] Community/social: TikTok-style full-screen "For You" feed of PUBLIC trips (swipe through stops, like/save/clone from an on-card action rail), trip ratings, a minimal user profile page, and lightweight interest-based feed ranking (FB-19, FB-20, FB-21, FB-24, plus SOCIAL-05/06 added 2026-08-06)
- [ ] Trip tracking: foreground stop-arrival detection MVP; push notification stretch blocked on FB-14 (FB-25, FB-26)
- [ ] Winter hardening: production hardening pass, deployment automation/smoke tests, load/perf test on rate-limited endpoints, retro backlog catch-up, documentation freshness audit, final e2e regression + sign-off, demo/seed data script, grading/portfolio README pass (WP-01 through WP-08)

### Out of Scope

- Public unauthenticated trip sharing — would require reworking `SecurityConfig`'s default-deny posture; the social-features audit explicitly recommends against it for capstone scope (see `.planning/codebase/CONCERNS.md` security section)
- True background/push arrival detection (FB-26) — blocked on a native Capacitor shell that doesn't exist yet; explicitly deferred until FB-14 (native build spike) lands successfully
- Distributed/Redis-backed rate limiting — current single-instance in-memory Bucket4j is sufficient at current scale; revisit only if multi-instance deployment becomes a real requirement
- Offline edits/sync for the PWA — FB-13 scope is explicitly read-only offline viewing, not offline mutation

## Context

- Academic capstone project (SDP + AJF module logs — those logs are due within the current sprint, before the fall-break window starts, and are tracked separately, not as fall/winter scope).
- Team: Tanish (backend/AI), Neel (frontend, required reviewer on any DTO/API contract change), Pratham (auth, standing reviewer on auth-adjacent PRs), Joann (QA/regression).
- Serialize-point rule in force all break/winter: async Slack ping before touching `pom.xml`, `application.properties`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `BaseEntity.java`, or any new migration-adjacent file.
- Jira project `SCRUM` at `atanish6.atlassian.net`; epic mapping is documented in `docs/TripFlow_fall_Break_Plan.md` Section 1.
- `docs/social-features-traceability-audit.md` found real gaps between what Jira's social-feature tickets claimed and what's actually shipped (discovery feed, like, clone, ratings) — FB-19/20/21/24 exist specifically to close those gaps.
- `docs/risk-register.md` tracks 13 risks (R1-R13); most mitigated. R2 (JWT filter misconfiguration locking out all endpoints) and R9 (GitHub Actions outages) remain open and recurring concerns.
- No Docker on any team machine — `*IT` integration tests only run in CI under the `-Pci` Maven profile; local dev is `mvn verify` (unit tests only).
- `.planning/` (this GSD workspace) is deliberately gitignored — the rest of the team doesn't use GSD, so planning artifacts stay local to this session/machine, not in the shared repo.
- **Jira backlog reviewed live (2026-08-06, 233 issues in project `SCRUM`)** for this milestone: confirmed `SCRUM-244` (day/time scheduling) and `SCRUM-110`/REF-21 (pagination) are already Done, unblocking FB-17/FB-04a and FB-07 respectively. Found the fall plan's epic-mapping table is stale for `SCRUM-83` (not AUTH — it's "REFACTOR: Database & Schema Integrity") and `SCRUM-87` (Done/closed, not usable for new config tickets) — must be corrected before any ticket creation. Also found two real, unreferenced Jira tickets: `SCRUM-248` (Dockerize + Neon Postgres deploy) and `SCRUM-274` (404 existence-hiding standardization, directly relevant to Phase 6). Full detail in `.planning/RISKS.md`.

## Constraints

- **Tech stack**: Java 21 / Spring Boot 4.1 backend, Angular 20 / Ionic 8 frontend, PostgreSQL 16, Flyway-managed schema — locked in; no dependency bumps or framework upgrades without team review (ground rule carried from fall into winter plan).
- **Testing**: No Docker locally — `*IT` Testcontainers tests are CI-only; anything requiring live-container verification can only be diagnosed from CI logs.
- **Timeline**: Fall-break window is 2026-08-17 through early January 2027. Winter term follows with no fixed sprint boundaries yet — deliberately a groomable backlog, not a schedule, until the team knows what fall break actually finished.
- **Team capacity**: Small team (3 devs) — sequencing must avoid collisions on serialize-point files; Neel and Pratham's review requirements gate DTO/API and auth changes respectively.
- **Grading/demo**: Work must read well in a capstone demo/portfolio context — visible polish (map rendering, dark mode, AI itinerary quality) carries real weight alongside raw feature completeness.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Layered backend (`controller/`→`service/`→`repository/`→`domain/`) over feature-slicing | Deliberate choice for a small team on Spring Boot conventions — see `README.md` "Architecture rationale" | ✓ Good |
| `.planning/` (GSD workspace) kept local-only, gitignored | Rest of team doesn't use GSD; keep it out of the shared repo | ✓ Good |
| FB-19/20/21 (discovery feed, like, clone) moved from winter into the fall-break plan | SCRUM-74c's prod regression pass found those endpoints didn't exist yet; de-risks winter by building the social foundation earlier | ✓ Good |
| Public unauthenticated trip sharing explicitly not pursued | Would require reworking `SecurityConfig`'s default-deny posture; not justified for capstone scope per the social-features audit | — Pending |
| SDP/AJF module log finalization tracked as current-sprint work, not fall/winter scope | Due within 2 weeks of 2026-08-06, before the fall-break window even starts | ✓ Good |
| Milestone named "Semester 5 → Semester 6 Fall Break Implementation" (v1.0), spans both fall-break and winter phases in one continuous roadmap | Winter is explicitly de-risked *by* fall break per the plan docs' own framing — treating them as one milestone with 8 continuously-numbered phases keeps traceability intact across the break/term boundary | ✓ Good |
| Front-load Phases 1-7 (all feature work) into fall break; Phase 8 (winter) is hardening/regression only | User explicit call (2026-08-06): 4.5-month fall break vs. 3-month winter term — most functionality should land while there's more runway, leaving winter for polish/sign-off rather than net-new features | — Pending |
| Phase 6 "For You" feed restyled as TikTok-style full-screen swipe (not Instagram-post-list); user profile page + interest-based ranking folded into Phase 6 rather than deferred | User explicit design call during Phase 6 discussion — profile page is small and directly needed as the data source for personalized ranking and for the feed's owner-username display | — Pending |
| Created 8 new `v2` epics (`SCRUM-275`-`SCRUM-282`: AUTH, TRIP, ROUTE, AI, SOCIAL, DEVOPS, DOCS, TESTING) rather than reopening/reusing any semester-5 epic | All semester-5 epics close 2026-08-17 alongside the presentation wrap-up, before official fall-break work starts (per user); `SCRUM-83` was also actively a different, mislabeled epic. Reopening closed epics blurs the "shipped" signal for an academic project that gets reviewed. Resolves RISK-J1/RISK-J2 | ✓ Good |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-06 after brownfield onboarding initialization*
