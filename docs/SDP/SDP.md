# TripFlowAI — Software Development Plan

Living document. Reflects actual current practice, not aspirational process — update this whenever a real convention changes, not just at sprint boundaries. This is the practical/ technical counterpart to the formal academic SDP deliverable (SYST38634); this one is the one the team actually follows day to day.

**Last updated:** 2026-07-19
**Last updated:** 2026-07-23

---

## 1. Purpose & Scope

TripFlowAI (Jira project "Wanderlust", key `SCRUM`) is a Spring Boot 4.1 / Java 21 backend with an Ionic/Angular frontend, built as a capstone project spanning three courses: Capstone Project, Advanced Java Frameworks, and Software Process Management. This document governs engineering practice for the backend; frontend conventions are Neel's domain and documented separately as they solidify.

## 2. Team & Roles

| Person | Role |
|---|---|
| Tanish | Backend lead, repo/Jira admin, Module A (Intelligent Trip Engine) co-owner |
| Neel | Frontend (Ionic/Angular), Module A co-owner, required reviewer on DTO/API contract changes |
| Pratham | Auth/security domain, Module B co-owner, standing reviewer for auth-adjacent PRs |
| Joann | CI/testing scope (Testcontainers, GitHub Actions pipeline) |

**Supervisor:** Syed Raza Bashir.

## 3. Tech Stack

- **Backend:** Spring Boot 4.1, Java 21, PostgreSQL + Flyway, JPA/Hibernate, Lombok,
  Spring Security + JWT (JJWT), JUnit 5 + Mockito, Testcontainers (CI-only)
- **Frontend:** Ionic/Angular 20, standalone components
- **External integrations:** OpenRouteService/VROOM (routing), Google Gemini (AI itinerary),
  Mapbox (maps + place search), Cloudinary (photo uploads)
- **CI/CD:** GitHub Actions — `backend-ci.yml`, `frontend-ci.yml`

## 4. Package Structure

Layered, not feature-sliced — chosen deliberately (see SCRUM-197) because it matches conventions every grader has seen, and minimizes cross-package import churn for a 4-person team on a 12-week timeline.

com.tripflow.backend

├── BackendApplication.java

├── domain/                 entities 

│   └── enums/              + enums

├── repository/              JPA repositories

├── service/                 business logic

├── controller/               REST endpoints

├── dto/                      request/response records

├── mapper/                   DTO <-> entity

├── exception/                 GlobalExceptionHandler, ApiError, custom exceptions

├── security/                 SecurityConfig, JwtAuthFilter, JwtService, UserPrincipal,

│                              JsonAuthenticationEntryPoint, JsonAccessDeniedHandler

├── config/                   ONLY cross-cutting @Configuration (JacksonConfig, etc.)

└── client/

│   ├── ors/                   OpenRouteService client, config, properties, wire DTOs

│   └── gemini/                GeminiClient, config, properties, wire DTOs

├── ai/                     SuggestedItinerary, GeminiResponseParser, ItineraryPromptTemplate

**Rule for adding a new top-level package:** only when a cross-cutting concern has ≥3 related classes (e.g. `security/` earned its own package; `exception/` stays flat until it crosses ~15 files or gains a genuinely separate domain family).

**Test packages mirror source packages exactly.** A test for `controller.TripController` lives in `controller`, never `controllers` or any renamed variant. `testsupport/` is the one sanctioned exception — shared test infrastructure (`PostgresTestcontainersConfiguration`).

## 5. Git Workflow

### Branching
- `main` — protected, always deployable, PR-only merges
- `feature/<Jira-Key>-<slug>` or `feat/<Jira-Key>-<slug>` — new features
- `fix/<Jira-Key>-<slug>` — bug fixes
- `test/<Jira-Key>-<slug>` — test-only additions
- `refactor/<Jira-Key>-<slug>` or `ref/<Jira-Key>-<slug>` — refactors
- `ci/<Jira-Key>-<slug>` — pipeline/workflow changes
- `chore/<Jira-Key>-<slug>` — housekeeping

One branch per ticket. Stacked branches (a branch built on top of another unmerged branch) are fine when there's a genuine dependency — document the stack order in the PR description.

### Commits — Conventional Commits + Jira Smart Commits
[SCRUM-XXX] type(scope): message #transition #time <duration>
Types: `feat`, `fix`, `docs`, `test`, `ref`/`refactor`, `perf`, `chore`, `ci`, `build`, `style`.
Transitions: `#to-do`, `#in-progress`, `#in-review`, `#done`. Only the final commit on a
branch needs `#in-review` — earlier commits on the same branch can omit the transition tag.

**Smart Commit email must exactly match the Jira account email** — GitHub noreply addresses break the Jira link silently, leaving merged PRs stuck showing "To Do."

One commit per logical checkpoint. Small, self-contained changes are fine as a single commit;
larger tickets should split into justified checkpoints (e.g. "add classes" → "wire them in" → "add tests") rather than one giant commit or many trivial ones.

### Pull Requests

- **PR title must match:** `[SCRUM-XXX] type(scope): message` — enforced by `.github/workflows/pr-title-check.yml`. Automated bot PRs (Dependabot) are exempt.
- Use the repo's PR template (`.github/pull_request_template.md`) — Summary, Jira Story link, Type of Change, Breaking Change flag, Testing Evidence, Screenshots (if UI), Checklist including the serialize-point confirmation and Neel-notification box.
- At least 1 reviewer required.
- Neel is required reviewer on any DTO/API contract change. Pratham is standing reviewer for auth-adjacent PRs.

### Serialize points

Coordinate before touching, in the same PR-open window, more than one of:
`pom.xml`, `application.properties`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `BaseEntity.java`. Check the checklist box on the PR template confirming no other open PR touches the same file.

## 6. Testing Strategy

- `*Test.java` → Surefire, runs locally and in every CI run, no Docker required.
- `*IT.java` → Failsafe, CI-only via the `-Pci` Maven profile (Docker unavailable on all team machines — this is why IT correctness can only be confirmed in CI, not locally).
- Test packages mirror source packages exactly (see §4).
- Prefer slice tests (`@WebMvcTest`, `@DataJpaTest`) over full `@SpringBootTest` where the scope allows — faster, and avoids dragging in unrelated infrastructure (e.g. `AuthControllerTest` uses `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)` rather than booting security).
- Coverage measured via JaCoCo, merging Surefire + Failsafe exec data before reporting (see `docs/ci.md`). Current floor: 80% overall, 80% on changed files (set 2026-07-19, see `docs/ci.md` for the exact provenance of that number).
- Query-count assertions (N+1 guards) use Hibernate `Statistics`, with the measured value locked and dated in a comment — never guessed.

## 7. CI/CD

See `docs/ci.md` for the full pipeline. Summary: PR + push to `main` triggers `backend-ci.yml` (and `frontend-ci.yml` for `frontend/**` changes), running the full test suite plus a JaCoCo coverage gate. Required status check on `main` branch protection.

## 8. Coding Standards

- Logging: see `docs/LOGGING_STANDARD.md` — SLF4J via `@Slf4j`, never `System.out`.
- DTOs are Java records, not classes.
- Entities use Lombok `@Getter @Setter @NoArgsConstructor`.
- Dependency injection: constructor injection via Lombok (`@RequiredArgsConstructor`
  preferred over `@AllArgsConstructor` for classes where field order/count may grow — some existing classes still use `@AllArgsConstructor`, migration tracked separately, not a blocker for new code).
- Never commit secrets — `.env` is gitignored, `spring.config.import=optional:file:.env[.properties]`.

## 9. Onboarding

1. Clone the repo, copy `.env.example` to `backend/.env`, fill in local secrets.
2. Run PostgreSQL locally on 5432 with a `tripflow` database.
3. Eclipse: Run As → Spring Boot App. No `SPRING_PROFILES_ACTIVE` needed (`dev` is default).
4. `.\mvnw.cmd test` for unit tests; full `-Pci` suite requires Docker and mainly runs in CI.
5. Read `docs/auth.md`, `docs/api-contracts.md`, and this document before your first PR.

## 10. Change Log

| Date | Change |
|---|---|
| 2026-07-19 | Initial SDP.md created, consolidating conventions established across Sprint 3 |
| 2026-07-23 | Updated package tree (gemini client landed, ai package added), coverage-floor provenance closed, risk register synced through Sprint 4 pre-work, frontend-standards.md created |