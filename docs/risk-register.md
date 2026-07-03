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

## Sprint 1 Update Log
- Jul 7: [add new risks discovered]
- Jul 12: [close out resolved risks, note carryover]