# Sprint 2 Retrospective

**Sprint dates:** 2026-07-13 – 2026-07-20
**Attendees:** Tanish, Neel, Pratham, Joann
**Filled out by:** Tanish

---

## Follow-up from last sprint's change
*(Skipped — this is the first retro filed under the new template.)*

## What went well

- Caught a real bug before it shipped: a hidden tab character in `SecurityConfig`'s `permitAll` matcher for `/actuator/health`, which would have caused a silent 401 on the deploy health check (SCRUM-133).
- Testcontainers + CI wiring confirmed genuinely working, not just configured — verified via an actual CI log showing a real `postgres:16` container, Flyway migrations applying, 13/13 tests passing.
- Dev/prod profile split (SCRUM-104) landed cleanly, including externalizing CORS out of `SecurityConfig` and writing `docs/deployment.md` in one pass — env vars, JWT secret generation, rollback procedure, troubleshooting table.

## What didn't go well

- The repo and Jira board drifted significantly — several tickets (SCRUM-54, 103, 105, 108, and subtasks 125–127) were already merged on `main` but still showing "To Do." This was the first clear sign of the Smart Commit email-mismatch issue that kept recurring for weeks after.
- A JaCoCo coverage gap was found reactively rather than caught by process — Failsafe's `*IT` execution data wasn't being merged into the final report, so integration-test coverage was invisibly reporting as 0%. Filed as SCRUM-138, but only after someone happened to notice the numbers looked wrong.
- No written retro happened this sprint — which is the entire reason this template exists now.

## One change for next sprint

- Fix the Jira/repo drift at the source (Smart Commit email match) instead of doing a manual sweep every sprint to catch it after the fact.

---

*Filed under `docs/retros/sprint-2.md`. See `docs/retros/README.md` for the convention.*