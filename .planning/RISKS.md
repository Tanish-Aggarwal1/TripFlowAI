# Risks: Milestone v1.0 — Semester 5 → Semester 6 Fall Break Implementation

**Defined:** 2026-08-06
**Sources:** `docs/risk-register.md` (R1-R13), `.planning/codebase/CONCERNS.md`, live Jira review (project `SCRUM`, 233 issues, `atanish6.atlassian.net`), `docs/TripFlow_fall_Break_Plan.md`, `docs/TripFlow_Winter_Plan.md`

## Jira Backlog Reconciliation Findings (new — found during this review)

**Update 2026-08-06 (post Phase 6 discussion):** User confirmed all remaining open epics used by this milestone (`SCRUM-7` ROUTE, `SCRUM-8` AI, `SCRUM-9` SOCIAL, `SCRUM-10` DEVOPS, `SCRUM-11` DOCS, `SCRUM-84` REFACTOR: Test Infra) will also close 2026-08-17 alongside `SCRUM-83`/`SCRUM-6`. Created six more `v2` epics to match: `SCRUM-277` (ROUTE v2), `SCRUM-278` (AI v2), `SCRUM-279` (SOCIAL v2), `SCRUM-280` (DEVOPS v2), `SCRUM-281` (DOCS v2), `SCRUM-282` (TESTING v2). Every epic this milestone's phases reference is now a fresh `v2` epic (`SCRUM-275` through `SCRUM-282`) — none of the original semester-5 epics are used going forward. All FB-##/WP-## `Epic:` lines in `docs/TripFlow_fall_Break_Plan.md` and `docs/TripFlow_Winter_Plan.md`, and `.planning/ROADMAP.md`'s per-phase Jira epic notes, updated accordingly.

**RISK-J1 — Fall plan's epic-mapping table (Section 1) is stale for 2 of 9 epics** — High priority, blocks ticket creation
- `docs/TripFlow_fall_Break_Plan.md` Section 1 maps `SCRUM-83 = AUTH` and `SCRUM-87 = Config & Deploy`. Live Jira shows:
  - **SCRUM-83** is actually `REFACTOR: Database & Schema Integrity` (Epic, To Do) — not AUTH. There is currently **no open AUTH epic**: `SCRUM-5` (AUTH) and `SCRUM-85` (REFACTOR: Security & Authentication Hardening) are both **Done**/closed.
  - **SCRUM-87** is `REFACTOR: Configuration & Deployment Readiness` (Epic) and is **Done**/closed — new config/profile/secret tickets (e.g. FB-09's email provider config) should not be filed under a closed epic.
- Impact: FB-01/02/03/16 (auth work) and any config-flavored fall ticket have no correct home epic as currently mapped. Following the fall plan's own Section 5 instruction ("verify epic keys... first") would have caught this — do it before creating any tickets.
- Mitigation: create a new open epic for fall auth work rather than reopening `SCRUM-5`/`SCRUM-85` — see decision below.
- Status: **Resolved 2026-08-06** — created `SCRUM-275` ("AUTH v2") epic for Phase 1 (FB-01/02/03/16). `SCRUM-87` (Config & Deploy) mapping is stale too but no FB item actually cites it as its epic — no new epic needed there; existing `SCRUM-10` (DEVOPS) covers config-adjacent fall work.

**RISK-J2 — SCRUM-6 (TRIP) epic is Done/closed but 6 fall stories map to it** — Medium priority
- Correction (2026-08-06): FB-04, FB-05, FB-06, FB-07, FB-15, FB-25 map to `SCRUM-6 (TRIP)` per the fall plan's Section 1 table — Phases 2, 4, and 7. (Earlier draft of this risk incorrectly included FB-19/20/21/24; those are actually epic `SCRUM-9 (SOCIAL)`, which is **To Do**/open — no epic issue there, corrected below.) Live Jira shows `SCRUM-6` status **Done**.
- Impact: Adding new stories to a closed epic is legal in Jira but is a board-hygiene problem (mirrors the exact class of issue `SCRUM-191` — "Jira board hygiene" — already had to fix once this project, per Sprint 4 risk log).
- Mitigation: create a new `TRIP v2` epic rather than reopening `SCRUM-6` — see decision below.
- Status: **Resolved 2026-08-06** — created `SCRUM-276` ("TRIP v2") epic for Phases 2/4/7 (FB-04/05/06/07/15/25).

**RISK-J3 — Two real, in-flight Jira tickets aren't referenced anywhere in the fall/winter plan docs** — Medium priority
- `SCRUM-248` (Task, To Do): *"Dockerize backend and deploy to Render with a Neon Postgres database"* — a real, planned infra migration with zero mention in either plan doc. If this lands independently mid-fall-break, it could collide with `WP-01`/`WP-02` (production hardening / deploy automation) or invalidate assumptions baked into those winter tickets (e.g. connection pool sizing, `application-prod.properties` review).
- `SCRUM-274` (Task, To Do): *"Standardize 404 existence-hiding across owner-gated trip mutations (PUT/DELETE/PATCH)"* — directly overlaps with the 404-vs-403 existence-hiding convention question the fall plan already flags as unresolved for FB-19/FB-20/FB-21 (`docs/social-features-traceability-audit.md`'s cross-reference notes). This ticket should very likely be resolved *before or alongside* FB-19/20/21, not independently.
- Mitigation: Add `SCRUM-248` as an explicit dependency/context note on Phase 9 (winter hardening). Resolve `SCRUM-274`'s existence-hiding convention decision as part of Phase 6 (community/social) rather than letting it drift as an orphaned ticket.
- Status: Open, unmitigated — first surfaced here.

**RISK-J4 (informational, not a risk — reduces two existing soft-dependencies)** — resolved good news
- `SCRUM-244` (+ 244a/244b, day/time scheduling foundation) is **Done** in Jira. FB-17 (Gemini day/time/meal scheduling) and FB-04a (.ics export using `dayNumber`/`plannedTime`) are no longer blocked on it — their "soft dependency" notes in the fall plan can be dropped.
- `SCRUM-110` (REF-21, pagination/sorting convention) is **Done** in Jira. FB-07 (search/filter on trip list) is no longer blocked — its "Do NOT start until REF-21 is merged" note in the fall plan is now satisfied.
- `SCRUM-71` (parent, In Progress) and subtasks `SCRUM-159..163` (71a-71e, all To Do) confirm the fall plan's own cross-reference notes are accurate: FB-19/20/21 should extend these existing tickets, not create duplicates, when Section 5's ticket-creation pass runs.

## Carried-Forward Risks (from `docs/risk-register.md`, still open)

**RISK-R2 — JWT filter misconfiguration locks out all endpoints including `/auth`** — High impact, Medium likelihood
- Phase 1 (Auth Seam Hardening) directly modifies `SecurityConfig.java`, `JwtAuthFilter`, and introduces a new `UserPrincipal` seam plus refresh-token endpoints — exactly the surface this risk targets.
- Mitigation (carried forward): Postman regression after every `SecurityConfig` change; this discipline held through Sprints 3-4 per the risk register's update log and must continue through Phase 1.

**RISK-R9 — GitHub Actions outages block PR merges with no team-side fix** — Medium impact, Low likelihood
- Mitigation (carried forward): continue local development on stacked branches during outages; check githubstatus.com before assuming a repo-side config issue.

## Milestone-Specific Risks (derived from CONCERNS.md + fall/winter plan content)

**RISK-M1 — FB-26 (push notification) is hard-blocked on FB-14 (native Capacitor build spike)** — Phase 7 sequencing risk
- FB-14 (Phase 5) may not produce a working native build (its own acceptance criteria allow a "documented blockers" outcome as success). If so, FB-26 cannot start at all this milestone — it isn't a schedule slip, it's a scope removal.
- Mitigation: Treat FB-26 as conditional scope from the start; do not commit to it in any external-facing plan (e.g. grading rubric) until FB-14 actually reports a working build.

**RISK-M2 — SEARCH-01 (Phase 2) soft-depended on REF-21 landing — now resolved (see RISK-J4)** — downgraded from risk to informational
- No longer blocking; noted here only so the dependency isn't re-flagged mid-phase-2-planning.

**RISK-M3 — Public trip sharing is explicitly out of scope, but SecurityConfig's default-deny posture means "share a trip" only works between logged-in users** — Product-expectation risk
- If grading or demo expectations assume link-based public sharing (common in travel apps), this will read as a gap. Mitigation: confirm expectations early and, if needed, explicitly scope "share with logged-in users only" into demo talking points rather than discovering the gap during rehearsal.

**RISK-M4 — Winter term (Phase 9) has no fixed sprint boundaries yet** — Planning risk, not a defect
- `docs/TripFlow_Winter_Plan.md` deliberately leaves winter hardening ungroomed until the team knows what fall break actually finished. `/gsd-plan-phase 9` should not run until Phase 8 is substantially complete — running it earlier risks planning against assumptions that fall-break slippage will invalidate. (Renumbered 2026-08-12: Phase 8 "Trip Collaboration" was inserted ahead of winter hardening, which shifted from Phase 8 to Phase 9.)

**RISK-M5 — Rate limiting is single-instance/in-memory (Bucket4j, no Redis backend)** — Scaling risk, currently accepted
- Not a blocker for capstone scale, but if `SCRUM-248` (Dockerize + Render + Neon, see RISK-J3) leads to any horizontally-scaled deployment topology, this silently stops working (each instance gets its own token buckets). Cross-check `SCRUM-248`'s deployment topology against this assumption before Phase 9's production hardening pass.

## Status Summary

| ID | Risk | Priority | Status |
|----|------|----------|--------|
| RISK-J1 | Fall plan epic mapping stale (SCRUM-83, SCRUM-87) | High | Resolved — `SCRUM-275` "AUTH v2" created |
| RISK-J2 | New stories filed under closed SCRUM-6 epic | Medium | Resolved — `SCRUM-276` "TRIP v2" created |
| RISK-J3 | SCRUM-248 / SCRUM-274 unreferenced in plan docs | Medium | Open |
| RISK-J4 | SCRUM-244 / SCRUM-110 done — dependencies resolved | — | Resolved (informational) |
| RISK-R2 | JWT filter lockout risk (SecurityConfig changes) | High | Open, carried forward |
| RISK-R9 | GitHub Actions outages | Medium | Open, carried forward |
| RISK-M1 | FB-26 hard-blocked on FB-14 | Medium | Open, conditional scope |
| RISK-M2 | SEARCH-01 / REF-21 dependency | — | Resolved (informational) |
| RISK-M3 | Public sharing expectation gap | Low-Medium | Open |
| RISK-M4 | Winter phase ungroomed | Low | Open, by design |
| RISK-M5 | Single-instance rate limiting vs. SCRUM-248 deployment | Low | Open, monitor |

---
*Last updated: 2026-08-06 after milestone v1.0 initialization and live Jira backlog review*
