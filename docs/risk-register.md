# Risk Register

Updated weekly. Format: ID, Description, Category, Likelihood, Impact, Mitigation, Status.

| ID | Risk | Category | Likelihood | Impact | Mitigation | Status |
|---|---|---|---|---|---|---|
| R1 | Flyway/Hibernate config conflict on schema setup | Technical | Medium | High | Use ddl-auto=validate; test on fresh DB | Open |
| R2 | JWT filter misconfiguration locks out all endpoints incl. /auth | Technical | Medium | High | Postman regression after every SecurityConfig change | Open |
| R3 | User entity defined twice (Story 1 + Story 2 collision) | Integration | Medium | Medium | Tuesday sync locks entity ownership to Pratham | Mitigated |
| R4 | Story 2 (auth) slips past Friday, cascades to Story 3/4 | Schedule | Medium | Medium | Daily status check Thu; spillover absorbed into Sprint 2 start | Open |
| R5 | Joann + Pratham both at weekly capacity ceiling | Team | Medium | Medium | Tanish covers PPP compilation if needed; cut schema scope to entities-only | Open |
| R6 | UI built against contract that changes before backend ships | Integration | Low | Medium | Contract locked in api-contracts.md before UI work starts | Mitigated |
| R7 | Ontario Civic Holiday (Aug 3) reduces capacity in Sprint 5 presentation week | Schedule | High | Medium | Deploy completed by Aug 4, rehearsal Aug 5 | Open (future sprint) |
| R8 | No Docker on any team machine — `*IT` tests can't run or be debugged locally, only in CI | Technical | High | Medium | `-Pci` profile scopes IT tests to CI only; documented in `docs/ci.md` | Mitigated |
| R9 | GitHub Actions platform outages block PR merges with no team-side fix | Schedule | Low | Medium | Continue local development on stacked branches during outages; check githubstatus.com before assuming a repo-side config issue | Open |

## Sprint 1 Update Log
- Jul 7: Initial risks logged during planning.
- Jul 12: R3 (User entity collision) and R6 (contract drift) mitigated per plan.

## Sprint 3 Update Log
- Jul 19: R8 and R9 added — surfaced during a live GitHub Actions outage (runs stuck "queued") mid-sprint. R2 remains relevant — SecurityConfig changed twice this sprint (SCRUM-197 move, SCRUM-100 wiring); Postman regression discipline held.