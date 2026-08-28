# Risk Register

Reviewed at sprint boundaries (see Update Log for the last review date) — not literally weekly; this line previously claimed a weekly cadence the log never matched. Format: ID, Description, Category, Likelihood, Impact, Mitigation, Status.

| ID | Risk | Category | Likelihood | Impact | Mitigation | Status |
|---|---|---|---|---|---|---|
| R1 | Flyway/Hibernate config conflict on schema setup | Technical | Medium | High | Use ddl-auto=validate; test on fresh DB | Open |
| R2 | JWT filter misconfiguration locks out all endpoints incl. /auth | Technical | Medium | High | Postman regression after every SecurityConfig change | Open |
| R3 | User entity defined twice (Story 1 + Story 2 collision) | Integration | Medium | Medium | Tuesday sync locks entity ownership to Pratham | Mitigated |
| R4 | Story 2 (auth) slips past Friday, cascades to Story 3/4 | Schedule | Medium | Medium | Daily status check Thu; spillover absorbed into Sprint 2 start | Open |
| R5 | Joann + Pratham both at weekly capacity ceiling | Team | Medium | Medium | Tanish covers PPP compilation if needed; cut schema scope to entities-only | Open |
| R6 | UI built against contract that changes before backend ships | Integration | Low | Medium | Contract locked in api-contracts.md before UI work starts | Mitigated |
| R7 | Ontario Civic Holiday (Aug 3) reduces capacity in Sprint 5 presentation week | Schedule | High | Medium | Deploy completed by Aug 4, rehearsal Aug 5 | Closed (event passed; deploy/rehearsal completed on schedule) |
| R8 | No Docker on any team machine — `*IT` tests can't run or be debugged locally, only in CI | Technical | High | Medium | `-Pci` profile scopes IT tests to CI only; documented in `docs/ci.md` | Mitigated |
| R9 | GitHub Actions platform outages block PR merges with no team-side fix | Schedule | Low | Medium | Continue local development on stacked branches during outages; check githubstatus.com before assuming a repo-side config issue | Open |
| R10 | Neel at ~12h sprint capacity ceiling — SCRUM-67 (AI preferences UI) flagged as first ticket to slip to Sprint 5 | Schedule | High | Medium | Flagged during Sprint 3 planning; SCRUM-67 has explicit slip-to-Sprint-5 note in Jira description | Open |
| R11 | SCRUM-173 (Bucket4j rate limiting) blocked on SCRUM-149 (AI-suggest endpoint) — cannot start until dependency merges | Integration | Medium | Low | Tracked in Sprint 4 plan; will unblock once Gemini PR lands | Open |
| R12 | Smart Commit email mismatch silently left merged PRs stuck at "To Do" in Jira — multiple sprints of stale status data | Process | High | Medium | Root-caused to transposed-letter typo in git config email; fixed in SCRUM-191, verified fix works on subsequent merges | Mitigated |
| R13 | GitHub push protection blocked a token commit (Mapbox scoped token classified as secret) — caused a branch deletion/recreation cycle | Technical | Low | Low | Resolved by moving Mapbox token to GitHub Actions secrets with build-time injection; added verification step to CI | Mitigated |
| R14 | `/actuator/metrics` and `/actuator/metrics/**` exposed unauthenticated by deliberate decision (SCRUM-174) — permits reading `http.server.requests` (every routed URI plus per-endpoint call counts/latency), `jvm.memory.*`, and `hikaricp.connections.*` without auth | Security | Low | Low | No PII or secrets in these metrics; accepted so `/actuator/health` and future dashboards can stay unauthenticated. Narrower option (separate non-public `management.server.port`) documented in `docs/auth.md` if exposure ever needs tightening | Accepted (residual risk) |


## Sprint 1 Update Log
- Jul 7: Initial risks logged during planning.
- Jul 12: R3 (User entity collision) and R6 (contract drift) mitigated per plan.

## Sprint 3 Update Log
- Jul 19: R8 and R9 added — surfaced during a live GitHub Actions outage (runs stuck "queued") mid-sprint. R2 remains relevant — SecurityConfig changed twice this sprint (SCRUM-197 move, SCRUM-100 wiring); Postman regression discipline held.

## Sprint 4 Update Log
- Jul 23: R10–R13 added; the "R14" this entry originally claimed was the actuator-metrics decision below (SCRUM-174) but its row was never written — added retroactively in the Aug 28 entry. R12 (Smart Commit email) mitigated via SCRUM-191. R2 (JWT filter lockout) remains relevant — SecurityConfig modified again for actuator metrics exposure (SCRUM-174). R13 mitigated via CI-injection pattern.

## 2026-08-28 Review
- R14 added: the actuator-metrics exposure (SCRUM-174) referenced by the Jul 23 entry above and by `docs/auth.md`'s public-paths table, which had never had a corresponding row.
- R7 closed — its event (Aug 3) is long past and its mitigation held.
- Corrected the "Updated weekly" line above; the log shows sprint-boundary reviews, not weekly ones.
- Not attempted here: a full backfill of every security-relevant change merged since the Jul 23 entry (multiple sprints' worth — e.g. SCRUM-410 rate limiting, SCRUM-437 photo-upload URL leak, SCRUM-450 Mapbox-token CI-artifact leak, SCRUM-493/494/495/496 hardening). That's a larger content refresh than this pass's scope (fixing the register's internal inconsistencies); tracking it as follow-up work rather than folding it into this change.