# TripFlowAI — Winter Term Plan

**Purpose:** Backlog of work for the semester-6 (winter) term — the final term before presentation/grading. Unlike `docs/TripFlow_fall_Break_Plan.md`, this file deliberately does **not** assign sprints: sprint boundaries and capacity will be decided once winter term actually starts and the team knows what fall break actually finished. This is a groomable backlog + a checklist of necessary steps, not a schedule.

**Relationship to the fall plan:** Fall break work (`docs/TripFlow_fall_Break_Plan.md`) was meant to de-risk winter, not replace it. In particular, FB-19/20/21 (community discovery feed, like, clone) moved from a winter item into the fall plan after SCRUM-74c's prod regression pass found those endpoints didn't exist yet — see `docs/qa/prod-regression-photos-social.md`. If any fall-break item doesn't land before winter starts, carry it into this backlog rather than dropping it silently.

**Ground rules (carried forward from fall):**
- Serialize-point rule stays in force: async Slack ping before touching `pom.xml`, `application.properties`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `BaseEntity.java` — and any new migration-adjacent file fall break introduces (e.g. if FB-20's `trip_likes` migration lands, its follow-on changes count too).
- Neel remains required reviewer on any DTO / API contract change.
- Pratham remains standing reviewer on any auth-adjacent PR.
- Nobody merges their own PR without at least one approval.
- No dependency bumps or framework upgrades without team review.

**Instructions for future ticket creation:** When Tanish provides this file back once winter term starts, create Jira issues in project `SCRUM` the same way the fall plan's items were created (see that file's Section 5) — except **do** assign a sprint this time, since by then the team will have picked actual sprint boundaries. Verify epic keys are still accurate first.

---

## SECTION 1 — Already tracked in Jira, don't recreate

- **SCRUM-74** (+ subtasks SCRUM-169/170/171/172) — end-to-end prod regression pass, created before fall break, currently "To Do," assigned to Joann. Checklists for 74a/b/c already drafted (`docs/qa/prod-regression-*.md`).
  - **Needs a description update once FB-19/20/21 land:** SCRUM-74c's current description still lists discovery feed / like / clone / search as scenarios to test. Once those ship in fall break, 74c should be corrected to actually test them (they'll exist by then) instead of the current checklist's "not implemented — descope" rows. Re-run that section instead of skipping it.
  - **Re-run in winter regardless of fall-break outcome:** even if FB-19/20/21 land in fall, a full regression pass belongs in the winter plan too (WP-07 below) — features built months before a presentation need re-verification against whatever's actually deployed at that point, not just once when first built.

---

## SECTION 2 — Winter work items

### WP-01 · Story · Production hardening pass
- **Epic:** SCRUM-10 (DEVOPS)
- **Priority:** High
- **Story Points:** 5
- **Labels:** security, reliability, production
- **Components:** backend, devops
- **Description:**
  ```
  A checklist pass over the deployed environment, not new features:
  - Confirm no dev-only endpoints, debug logging, or permissive CORS survive in prod config.
  - Re-verify error responses never leak stack traces, SQL, or internal identifiers (spot-check against docs/api-contracts.md's "Additional status codes" table).
  - Dependency vulnerability scan: `mvn dependency-check` (or OWASP plugin) on backend, `npm audit` on frontend — fix or explicitly accept-and-document any High/Critical findings.
  - Confirm rate limiting (SCRUM-173) behaves correctly under actual concurrent load, not just unit tests — see WP-03.
  - Review docs/deployment.md's rollback procedure is still accurate against however deploys actually work by then.
  ```
- **Business Value:** This is the non-functional-requirements story the grading rubric and any real deployment both expect — distinct from feature work, easy to skip under demo-prep time pressure if not tracked explicitly.
- **Dependencies:** none

---

### WP-02 · Task · Deployment automation / post-deploy smoke test script
- **Epic:** SCRUM-10 (DEVOPS)
- **Priority:** Medium
- **Story Points:** 3
- **Labels:** devops, automation
- **Components:** devops
- **Description:**
  ```
  docs/deployment.md's "Verify deployment" step is currently manual: hit /actuator/health, then manually smoke-test register→login. Turn it into a small script (bash or a GitHub Actions post-deploy job) that does both automatically and fails loudly if either breaks, so a bad deploy is caught within minutes instead of at the next manual check-in.
  ```
- **Business Value:** Reduces the chance a broken deploy sits undetected in the run-up to the presentation, when nobody's specifically watching for it.
- **Dependencies:** none

---

### WP-03 · Task · Load/performance test on rate-limited endpoints
- **Epic:** SCRUM-10 (DEVOPS)
- **Priority:** Medium
- **Story Points:** 3
- **Labels:** performance, testing
- **Components:** backend
- **Description:**
  ```
  POST /api/trips/{id}/optimize and /ai-suggest are the two endpoints backed by paid/quota-limited external APIs and protected by Bucket4j rate limiting (SCRUM-173). Verify under a simple concurrent-load test (k6, Gatling, or even a scripted parallel-curl loop) that: the limiter actually blocks at the configured capacity, Retry-After is accurate, and legitimate traffic under the limit isn't accidentally throttled by a race in the token bucket.
  ```
- **Business Value:** The rate limiter has unit tests but has never been exercised under real concurrency — this is exactly the kind of bug that only shows up under load, and ORS/Gemini quota exhaustion during a live demo would be a bad failure mode to discover live.
- **Dependencies:** none

---

### WP-04 · Task · SDP finalization
- **Epic:** SCRUM-11 (DOCS)
- **Priority:** High
- **Story Points:** 3
- **Labels:** docs, sdp
- **Components:** docs
- **Description:**
  ```
  docs/SDP/SDP.md is a living document. Before final submission: fill in Section 10 (Change Log) for the full project lifecycle, not just early sprints; re-verify Sections 3/4/6/8 (tech stack, package structure, testing strategy, coding standards) still match what actually shipped, since fall-break work will have touched several of these; confirm docs/SDP/coding-standards.md wasn't silently drifted from during break-speed development.
  ```
- **Business Value:** Direct grading deliverable — a stale SDP is a worse artifact than a shorter but accurate one.
- **Dependencies:** none

---

### WP-05 · Task · Retro backlog catch-up
- **Epic:** SCRUM-11 (DOCS)
- **Priority:** Medium
- **Story Points:** 2
- **Labels:** docs, process
- **Components:** docs
- **Description:**
  ```
  docs/retros/ currently only has sprint-1.md and sprint-2.md. Write the missing retros for every sprint since (at minimum Sprint 3, 4, 5, plus a fall-break retro covering FB-01 through FB-21's actual outcomes vs. plan) using docs/retros/TEMPLATE.md, while the details are still fresh enough to be worth writing down rather than reconstructed from memory during winter crunch.
  ```
- **Business Value:** Retros are a stated process artifact (docs/retros/README.md) and a natural source for both the SDP change log and presentation "what we learned" material — cheaper to write incrementally than to backfill five sprints at once in April.
- **Dependencies:** none

---

### WP-06 · Task · Documentation freshness audit
- **Epic:** SCRUM-11 (DOCS)
- **Priority:** Medium
- **Story Points:** 2
- **Labels:** docs, quality
- **Components:** docs
- **Description:**
  ```
  Sweep docs/api-contracts.md, docs/auth.md, docs/architecture.md, docs/deployment.md, docs/ci.md, docs/frontend-standards.md, docs/LOGGING_STANDARD.md against actual current code, looking specifically for sections marked "planned"/"not yet implemented" that shipped since, or endpoint contracts that quietly changed. (Concrete precedent: docs/api-contracts.md's Photo Upload section still said "not yet implemented" months after SCRUM-152/153 shipped it — caught and fixed during SCRUM-74c prep, see PR #170. There's likely at least one more of these somewhere.)
  ```
- **Business Value:** Stale "living documents" actively mislead whoever reads them next — including future Claude sessions and new contributors — and are cheap to catch with a deliberate pass instead of accidentally during unrelated work.
- **Dependencies:** none

---

### WP-07 · Story · Final end-to-end regression + sign-off
- **Epic:** SCRUM-6 (TRIP) / cross-cutting
- **Priority:** High
- **Story Points:** 5
- **Labels:** testing, regression, sign-off
- **Components:** api, frontend
- **Description:**
  ```
  Full re-run of SCRUM-74a/b/c against whatever's actually deployed by presentation time, plus a new pass covering FB-19/20/21 (community feed/like/clone) if they weren't already folded into a corrected SCRUM-74c (see Section 1 above). Consolidate into an updated sign-off checklist, same shape as SCRUM-172's, dated for the winter presentation rather than the fall one.
  ```
- **Business Value:** The whole reason SCRUM-74 exists — verifying the product actually works end-to-end on production before it's graded/demoed, not just that individual PRs passed CI in isolation.
- **Dependencies:** WP-01 (hardening should land first — no point regression-testing against a config that's about to change)

---

### WP-08 · Task · AJF module log finalization + demo prep
- **Epic:** SCRUM-11 (DOCS)
- **Priority:** High
- **Story Points:** 3
- **Labels:** docs, presentation
- **Components:** docs
- **Description:**
  ```
  docs/ajf-module-a.md (and its Module B counterpart) are running per-sprint logs of grading-relevant technical work, last updated through Sprint 4. Add entries for Sprint 5 onward and all fall-break work, then write a final consolidated "Presentation Notes" section: demo flow script, architecture talking points, advanced-concept callouts — updated to include whatever fall break actually added (community feed, refresh tokens, etc.) rather than only what existed at the last update.
  ```
- **Business Value:** Direct grading/presentation deliverable; a demo script written under time pressure the night before is worse than one iterated on incrementally.
- **Dependencies:** Best done after most fall-break + winter feature work has landed, so it reflects the actual final feature set rather than needing a rewrite.

---

### WP-09 · Task · Demo/seed data script
- **Epic:** SCRUM-10 (DEVOPS)
- **Priority:** Medium
- **Story Points:** 2
- **Labels:** devops, presentation
- **Components:** backend
- **Description:**
  ```
  A repeatable script/Flyway-style seed (run against a scratch DB, never prod) that populates a handful of realistic trips, stops, a couple of PUBLIC trips with likes for the discovery feed, and at least one AI-suggested itinerary — so a clean demo environment can be stood up in minutes instead of manually clicking through the UI before every rehearsal.
  ```
- **Business Value:** Removes a source of live-demo risk (manually re-creating demo data, forgetting a step) and makes rehearsal (see SCRUM-172's Aug rehearsal-day precedent) faster to repeat.
- **Dependencies:** FB-19/20 (discovery feed + likes) if the seed data is meant to showcase them

---

### WP-10 · Task · Grading/portfolio README pass
- **Epic:** SCRUM-11 (DOCS)
- **Priority:** Low
- **Story Points:** 2
- **Labels:** docs, presentation
- **Components:** docs
- **Description:**
  ```
  Top-level README.md pass for anyone landing on the repo cold (grader or portfolio viewer): accurate feature list reflecting the final shipped scope, setup instructions verified against a fresh clone, screenshots/GIF of the final UI, link to the deployed instance.
  ```
- **Business Value:** First-impression artifact for both grading and any post-course portfolio use — currently secondary to in-progress feature work, worth a dedicated pass once scope stabilizes.
- **Dependencies:** Best done last, after WP-07/WP-08 stabilize the feature set

---

## SECTION 3 — Summary table

| ID | Summary | Priority | SP | Depends on |
|---|---|---|---|---|
| WP-01 | Production hardening pass | High | 5 | — |
| WP-02 | Deployment automation / smoke test | Medium | 3 | — |
| WP-03 | Load/perf test on rate-limited endpoints | Medium | 3 | — |
| WP-04 | SDP finalization | High | 3 | — |
| WP-05 | Retro backlog catch-up | Medium | 2 | — |
| WP-06 | Documentation freshness audit | Medium | 2 | — |
| WP-07 | Final end-to-end regression + sign-off | High | 5 | WP-01 |
| WP-08 | AJF module log finalization + demo prep | High | 3 | (late) |
| WP-09 | Demo/seed data script | Medium | 2 | FB-19/20 (soft) |
| WP-10 | Grading/portfolio README pass | Low | 2 | (last) |

**Total SP:** ~30 (excludes SCRUM-74's already-tracked regression work, Section 1)

**Note:** This backlog will grow once winter starts and the team knows what actually shipped over break — treat this as the floor, not the ceiling, of winter scope.
