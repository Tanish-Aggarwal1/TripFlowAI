# Sprint 1 Retrospective

**Sprint dates:** 2026-07-06 – 2026-07-12
**Attendees:** Tanish, Neel, Pratham, Joann
**Filled out by:** Tanish

---

## Follow-up from last sprint's change
*(Skipped — this is the first sprint; there's no prior retro to follow up on.)*

## What went well

- Shipped a genuinely solid foundation in the first sprint: 18 story points across auth, the core data model, and CI/test groundwork — the team's first real sprint didn't slip.
- The Users/Trips/Stops schema (SCRUM-47) landed with improvements beyond the original scope — a dedicated `Place` entity to dedupe geographic data, enum-backed status/visibility fields, and shared `BaseEntity` auditing, rather than the bare-minimum schema.
- Auth backend (SCRUM-48) and the signup/login UI built against it (SCRUM-49) landed in the same sprint with a finalized `AuthResponse` contract — meaning Neel wasn't blocked waiting on a contract that kept shifting.
- Proactively ran an architecture review before problems compounded, opening a refactor epic (REF-01 through REF-05) covering migration alignment, config hardening, integration-test infrastructure, and CI — this is what most of Sprint 3's cleanup work built on.

## What didn't go well

- Hit a real environment blocker mid-sprint: a Windows Controlled Folder Access issue blocked file writes while spiking Testcontainers configuration, stalling that branch until it was root-caused and handed off.
- Docker being unavailable on any team member's machine meant Testcontainers integration tests couldn't be verified locally at all from day one — a structural constraint the team had to design around (CI-only `*IT` tests) rather than something fixable in-sprint.
- Some early schema drift crept in from `ddl-auto=update` silently masking mismatches between entities and migrations — caught and fixed, but the kind of thing that should be structurally prevented (which is why `ddl-auto=validate` became a hard rule going forward).

## One change for next sprint

- Treat local-environment blockers (Docker absence, Windows file-access issues) as known constraints to design around from the start of a sprint, not surprises to react to mid-sprint — bake the CI-only Testcontainers pattern and `ddl-auto=validate` rule into the SDP immediately rather than after being burned by them.

---

*Filed under `docs/retros/sprint-1.md`. See `docs/retros/README.md` for the convention.*