# Architecture

Layered backend: `controller/ → service/ → repository/ → domain/`, plus `dto/`, `mapper/`, `security/`, `config/`, `client/`, `exception/`, `ai/`, `ratelimit/`, `schedule/`. Chosen over feature-slicing for a small team on Spring Boot conventions — see `README.md` "Architecture rationale" for the full reasoning.

## Enforced layer-boundary rules (SCRUM-219 / AUDIT-10)

Until SCRUM-219, these boundaries were maintained entirely by convention and code review — nothing mechanical prevented drift, and this project has already run three deliberate refactoring cycles to correct it after the fact (SCRUM-107, SCRUM-197, SCRUM-202). `backend/src/test/java/com/tripflow/backend/ArchitectureTest.java` now makes the following rules self-enforcing, checked on every `mvn verify` (no Docker required — it runs under Surefire like any other unit test):

| Rule | Why |
| --- | --- |
| Controllers must not depend on repositories | A controller reaching into a repository directly is the structural tell that a service is missing or being bypassed — this is exactly the `StopController → TripService` coupling AUDIT-06 found, generalized to also cover the repository layer. |
| `domain` must not depend on `dto`, `controller`, or `service` | Entities are the innermost layer; nothing about the wire contract or request handling should leak inward. |
| `dto` must not depend on `domain` entities | DTOs are the wire contract. `domain.enums` (a separate package) is a deliberate, allowed exception — enums are shared vocabulary, not JPA entities. |
| Classes in `client/**` must not be accessed from `controller/**` | External API clients (`client/ors`, `client/gemini`) are a service-layer concern. |
| No class outside `exception/**` may be annotated `@RestControllerAdvice` | `GlobalExceptionHandler` is the single source of HTTP error mapping. |
| Services must not import `jakarta.servlet` or `org.springframework.http` types | Keeps HTTP-specific concerns out of the service layer. |

**Known deliberate exception:** `TripOwnershipService` lives in `service/` as a cross-cutting access-control concern shared across `TripService`, `StopService`, `RouteOptimizationService`, and `AiItineraryService`. It's an ordinary service class — none of the rules above need to special-case it.

Every rule above passes against current `main` with no production-code changes. If a future rule would fail against existing code, fix the violation in a separate ticket or leave the rule out — never ship a red build to force a rule in.
