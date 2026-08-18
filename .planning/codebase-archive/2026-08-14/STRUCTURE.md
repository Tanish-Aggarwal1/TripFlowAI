# Codebase Structure

**Analysis Date:** 2026-08-06

## Directory Layout

```
TripFlowAI/
├── backend/                          # Spring Boot 4.1, Java 21
│   ├── src/main/
│   │   ├── java/com/tripflow/backend/
│   │   │   ├── BackendApplication.java
│   │   │   ├── controller/           # HTTP → DTO mapping, @RestController
│   │   │   ├── service/              # Business logic, repository orchestration
│   │   │   ├── repository/           # JPA data access, Spring Data
│   │   │   ├── domain/               # JPA entities, enums, BaseEntity
│   │   │   ├── dto/                  # Request/response DTOs, validation annotations
│   │   │   ├── mapper/               # Entity ↔ DTO translation
│   │   │   ├── security/             # JWT token handling, auth filter, UserPrincipal
│   │   │   ├── client/               # External API wrappers (ors, gemini, cloudinary)
│   │   │   ├── exception/            # Custom exceptions, GlobalExceptionHandler, ApiError
│   │   │   ├── config/               # Spring bean configuration
│   │   │   ├── ratelimit/            # Token bucket rate limiting (Bucket4j)
│   │   │   ├── ai/                   # Gemini prompt templates, response parsing
│   │   │   └── schedule/             # Stop scheduling heuristic (ItineraryScheduler)
│   │   └── resources/
│   │       ├── application.properties # Config (DB, JWT, API keys from env vars)
│   │       ├── db/migration/          # Flyway SQL migrations (V1, V2, ...)
│   │       └── templates/             # Email templates (if any)
│   ├── src/test/
│   │   ├── java/com/tripflow/backend/ # Unit tests (*Test.java), slice tests (@WebMvcTest)
│   │   └── resources/                 # Test fixtures, test properties
│   ├── pom.xml                        # Maven dependencies, plugins, JaCoCo config
│   └── .env.example                   # Template for local .env (never commit actual .env)
│
├── frontend/                          # Angular 20 + Ionic 8, TypeScript strict
│   ├── src/
│   │   ├── app/
│   │   │   ├── app.routes.ts          # Top-level routing config (standalone)
│   │   │   ├── app.config.ts          # App providers (HttpClient, etc.)
│   │   │   ├── core/
│   │   │   │   ├── guards/            # Route guards (authGuard function)
│   │   │   │   ├── services/          # Singleton services (TripService, AuthService, etc.)
│   │   │   │   ├── models/            # TypeScript interfaces (TripResponse, etc.)
│   │   │   │   ├── http/              # HttpClient interceptors, error mapper
│   │   │   │   └── interceptors/      # Auth interceptor (adds Bearer token)
│   │   │   └── pages/
│   │   │       ├── auth/
│   │   │       │   ├── login/         # LoginPage component (login.page.ts/.html/.scss)
│   │   │       │   └── signup/        # SignupPage component
│   │   │       ├── starting-up/       # StartingUpPage (loading screen for backend startup)
│   │   │       └── trips/
│   │   │           ├── dashboard/     # DashboardPage (trip list, create modal)
│   │   │           ├── trip-view/     # TripViewPage (read-only, map, export)
│   │   │           ├── trip-edit/     # TripEditPage (create/edit trip + stops)
│   │   │           └── components/    # Reusable components
│   │   │               ├── trip-map/
│   │   │               ├── stop-list/
│   │   │               ├── stop-photo-gallery/
│   │   │               ├── stop-photo-upload/
│   │   │               ├── ai-preferences-form/
│   │   │               ├── ai-suggestion-cards/
│   │   │               ├── ai-trip-prompt/
│   │   │               └── edit-stop-form/
│   │   ├── assets/
│   │   │   └── icon/                  # App icons for PWA
│   │   ├── environments/
│   │   │   ├── environment.ts         # Dev config (apiBaseUrl with placeholders)
│   │   │   └── environment.prod.ts    # Prod config (CI build replaces placeholders)
│   │   ├── theme/                     # Ionic global styles (SCSS variables)
│   │   ├── global.scss                # Global styles (cross-view-encapsulation escapes)
│   │   ├── index.html                 # HTML entry point
│   │   └── main.ts                    # Angular bootstrap (bootstrapApplication)
│   ├── angular.json                   # Angular CLI config
│   ├── tsconfig.json                  # TypeScript strict mode
│   ├── karma.conf.js                  # Test runner config
│   ├── package.json                   # npm dependencies, scripts
│   └── .eslintrc.json                 # ESLint rules (enforced in CI)
│
├── docs/                              # Living documentation
│   ├── architecture.md                # Layer boundaries, layer-boundary test rules
│   ├── api-contracts.md               # All endpoints, request/response shapes, error codes
│   ├── auth.md                        # JWT flow, 401 vs 403, env var requirements
│   ├── LOGGING_STANDARD.md            # Logging levels, what to/not to log
│   ├── ci.md                          # CI pipeline, coverage requirements
│   └── [others]
│
├── .github/
│   ├── workflows/
│   │   ├── backend-ci.yml             # Runs mvn verify -Pci on PR/push to main
│   │   └── frontend-ci.yml            # Runs ng lint, ng test, ng build on PR/push to main
│   └── pull_request_template.md       # PR template (enforced sections)
│
├── .planning/
│   └── codebase/                      # Codebase analysis documents (this directory)
│       ├── ARCHITECTURE.md
│       ├── STRUCTURE.md
│       ├── CONVENTIONS.md             # (optional) coding style, naming
│       ├── TESTING.md                 # (optional) test patterns
│       ├── STACK.md                   # (optional) tech stack summary
│       ├── INTEGRATIONS.md            # (optional) external services
│       └── CONCERNS.md                # (optional) tech debt, issues
│
├── .claude/                           # Claude Code project config
│   ├── CLAUDE.md                      # Project instructions for Claude Code
│   └── skills/                        # Custom skills (if any)
│
├── .gitignore                         # Standard: target/, node_modules/, .env, .DS_Store
├── README.md                          # Project overview (how to run locally)
└── pom.xml (root)                     # (optional root pom if multi-module needed)
```

## Directory Purposes

### Backend

**`controller/`**
- Purpose: HTTP request routing and DTO mapping
- Contains: `TripController`, `StopController`, `AiController`, `AuthController`, `TripExportController`, `StopPhotoController`
- Key files: Each controller is one `@RestController` class with `@RequestMapping` at class level and `@GetMapping/@PostMapping/` etc on methods
- Add new file: One controller per major resource (not one per operation); e.g., if adding a community-shares feature, create `CommunityController` in this directory

**`service/`**
- Purpose: Business logic, orchestration of repositories and external APIs
- Contains: `TripService`, `StopService`, `AuthService`, `RouteOptimizationService`, `AiItineraryService`, `AiTripGenerationService`, `TripOwnershipService`, `StopPhotoService`, `PlaceResolutionService`, `IcsExportService`, `OrphanPlaceCleanupJob`
- Key files: Each service handles one domain concern (Trip CRUD, Stop CRUD, auth, route optimization, etc.); cross-cutting concerns (access control, place dedup) are separate services
- Add new file: For a new feature, create a service in this directory if it has business logic or repository access; name it descriptively (e.g., `TripExportService` for calendar export)

**`repository/`**
- Purpose: JPA data access abstraction
- Contains: `TripRepository`, `StopRepository`, `UserRepository`, `PlaceRepository`, `StopPhotoRepository` (all `extends JpaRepository<T, Long>`)
- Key files: One interface per entity; add `@Query` or custom finder methods as needed
- Add new file: One repository interface per new JPA entity; rely on Spring Data derived queries (e.g., `findByEmail`) rather than `@Query` where possible

**`domain/`**
- Purpose: JPA entities and core enums (innermost layer, no outbound dependencies except `domain/enums`)
- Contains: `BaseEntity` (abstract), `User`, `Trip`, `Stop`, `Place`, `StopPhoto`, enums in `domain/enums/`
- Key files: Each entity is one JPA `@Entity` class with Lombok `@Getter @Setter`; enums in separate files in `domain/enums/`
- Add new file: For a new persistent concept, create an `@Entity` in this directory with all JPA annotations; add an enum in `domain/enums/` if new status/type enum needed

**`dto/`**
- Purpose: Request/response wire contracts (never JPA-annotated)
- Contains: `*Request` (e.g., `CreateTripRequest`, `UpdateTripRequest`), `*Response` (e.g., `TripResponse`, `TripSummaryResponse`), `ApiError`, `AuthResponse`, etc.
- Key files: Use records where possible (immutable, auto-equals/hashCode); always include Bean Validation annotations
- Add new file: One DTO file per request/response shape; name clearly (e.g., `CreateCommunityPostRequest`, `CommunityPostResponse`)

**`mapper/`**
- Purpose: Entity ↔ DTO translation (pure functions)
- Contains: `TripMapper`, `StopMapper`, `AiItineraryMapper`
- Key files: One mapper class per entity; add methods for toEntity, toResponse, collections, etc.
- Add new file: For a new entity with its own DTO, create a mapper (e.g., `CommunityPostMapper`) using the pattern: entity → DTO (add `@Mapper` from MapStruct if complexity warrants, or implement by hand for simple cases)

**`security/`**
- Purpose: JWT authentication, Spring Security configuration, principal resolution
- Contains: `JwtService`, `JwtAuthFilter`, `UserPrincipal`, `SecurityConfig`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`, `JwtConfig`, `JwtProperties`
- Key files: Read-only unless adding new auth schemes (e.g., OAuth2); `SecurityConfig` is the central configuration bean

**`client/{ors,gemini,cloudinary}/`**
- Purpose: External API wrappers, exception translation
- Contains per-client:
  - `*Client` (HTTP wrapper, calls external API)
  - `*Properties` (`@ConfigurationProperties`)
  - `*Config` (RestTemplate bean, timeouts)
  - `*Request`, `*Response` (wire-format DTOs)
  - `*Exception` (domain exception, caught/translated by service)
- Add new file: For a new external API, create a subdirectory `client/{service-name}/` with the above pattern

**`exception/`**
- Purpose: Custom exceptions and global error handler
- Contains: `GlobalExceptionHandler` (`@RestControllerAdvice`), `ResourceNotFoundException`, `ForbiddenException`, domain exceptions, `ApiError`, `SecurityErrorWriter`
- Key files: `GlobalExceptionHandler` is the single source of HTTP error mapping; add `@ExceptionHandler` methods here for new exception types
- Add new file: For a new domain exception, create a class here (extends `RuntimeException` or custom base) and add a handler method in `GlobalExceptionHandler`

**`config/`**
- Purpose: Spring bean configuration, initialization
- Contains: `JacksonConfig`, `JpaConfig`, `OpenApiConfig`, `SchedulingConfig`
- Key files: Beans for Jackson (JSON serialization), JPA (Hibernate config), OpenAPI/Swagger, async scheduling
- Add new file: For a new third-party library or custom bean setup, create a config class here (e.g., `CacheConfig` if adding Redis)

**`ratelimit/`**
- Purpose: Token bucket rate limiting (Bucket4j)
- Contains: `RateLimiterService`, `RateLimitConfig`, `RateLimitProperties`, `RateLimitExceededException`
- Key files: Single service; inject into controllers that need rate limiting

**`ai/`**
- Purpose: Gemini prompt templates, response parsing
- Contains: `ItineraryPromptTemplate`, `TripGenerationPromptTemplate`, `GeminiResponseParser`, `SuggestedItinerary`, `GeneratedTripPlan`, `ItineraryPromptInput`, `TripGenerationPromptInput`
- Key files: Templates are not Spring components; they build prompt strings; parser extracts JSON from Gemini responses

**`schedule/`**
- Purpose: Stop scheduling heuristic
- Contains: `ItineraryScheduler`, `RouteScheduleConfig`, `RouteScheduleProperties`
- Key files: Scheduler is stateless; called by `RouteOptimizationService` after ORS returns leg times

**`src/test/java/com/tripflow/backend/`**
- Purpose: Unit and slice tests (no Docker)
- Naming: `*Test.java` for unit tests (Surefire), `*IT.java` for integration tests with Testcontainers (Failsafe, CI only)
- Key files: `ArchitectureTest.java` enforces layer boundaries; one test class per source class
- Add new file: For each new class, create a corresponding `*Test.java` in the same package structure; include at minimum a happy-path test

**`src/main/resources/db/migration/`**
- Purpose: Flyway SQL migrations (schema single source of truth)
- Naming: `V{n}__description.sql` (e.g., `V7__stop_scheduling.sql`)
- Key files: Each migration is immutable once applied; never edit an applied migration
- Add new file: For schema changes, create `V{next}__description.sql` with CREATE TABLE, ALTER TABLE, CREATE INDEX, etc.; Flyway runs on boot in validate mode

**`src/main/resources/application.properties`**
- Purpose: Configuration (externalizes env vars, feature flags)
- Key variables:
  - `spring.datasource.*` — database URL, username (password from env)
  - `spring.jpa.hibernate.ddl-auto=validate` — Flyway is the schema source
  - `jwt.secret` / `jwt.expiryMs` — read from env vars `JWT_SECRET`, `JWT_EXPIRY_MS`
  - `app.ors.api-key`, `app.ors.connect-timeout`, `app.ors.read-timeout` — external APIs
  - `app.ratelimit.optimize.capacity`, `.window` — rate limits (tunable)
  - `app.schedule.default-visit-duration`, `.day-start-time`, `.day-end-time` — scheduling

### Frontend

**`app.routes.ts`**
- Purpose: Top-level routing configuration (standalone Angular)
- Routes: `login`, `signup`, `starting-up`, `dashboard`, `trips/new`, `trips/:id`, `trips/:id/edit`
- Add new route: Add an object to the `routes` array with `path`, `loadComponent` (lazy), `canActivate` (guards)

**`core/services/`**
- Purpose: Singleton services (dependency injection, state management)
- Contains: `TripService`, `AuthService`, `StopPhotoService`, `ToastService`
- Key files:
  - `TripService` — all trip backend calls, maintains `trips` signal for reactive list
  - `AuthService` — login/logout, token storage, `isAuthenticated` signal
  - `StopPhotoService` — photo upload (Cloudinary signing), metadata persistence
  - `ToastService` — toast notifications (wrapper around Ionic ToastController)
- Add new file: For a new domain feature with backend integration, create a service here (e.g., `CommunityService` for community features)

**`core/guards/`**
- Purpose: Route guards (function-based, not class-based)
- Contains: `authGuard` (checks JWT validity before navigation)
- Add new file: For a new guard (e.g., `ownerGuard` if role-based access needed), create a guard function here

**`core/models/`**
- Purpose: TypeScript interfaces matching backend DTOs
- Contains: `TripResponse`, `TripSummaryResponse`, `StopResponse`, `CreateTripRequest`, `PagedResponse`, etc.
- Key files: These are NOT classes; use `interface` and/or `type` for structural typing
- Add new file: When backend DTO changes or a new endpoint is added, add corresponding TypeScript types here

**`core/http/`**
- Purpose: HttpClient setup, error mapping
- Contains: `api-error.mapper.ts` (HTTP error → domain error), `api.interceptor.ts` (adds Bearer token)
- Key files: Interceptor should add token to every request; error mapper provides typed error objects for services to use

**`pages/auth/login/`, `pages/auth/signup/`**
- Purpose: Authentication pages
- Files: `login.page.ts`, `login.page.html`, `login.page.scss`; `signup.page.ts`, `signup.page.html`, `signup.page.scss`
- Pattern: Component with `standalone: true`, uses `inject()` DI, calls `AuthService.login()` / `AuthService.register()`
- Add new file: For additional auth flows (e.g., password reset), create a new page directory here

**`pages/trips/dashboard/`**
- Purpose: Trip list page (load, filter, create, delete)
- Files: `dashboard.page.ts`, `dashboard.page.html`, `dashboard.page.scss`
- Key behavior:
  - `ionViewWillEnter` loads trips via `TripService.listTrips()`
  - Modal for "Create with AI" (free-text prompt → new trip)
  - Delete button with confirmation alert
  - Edit / View navigation to trip-edit / trip-view

**`pages/trips/trip-view/`**
- Purpose: Trip detail page (read-only or owner edit)
- Files: `trip-view.page.ts`, `trip-view.page.html`, `trip-view.page.scss`
- Key behavior:
  - `TripService.getTrip(id)` fetches trip
  - Map display (trip-map component)
  - Stop list (stop-list component)
  - Export to calendar (ICS), AI suggestions modal
  - Edit button (navigate to trip-edit) for owner only

**`pages/trips/trip-edit/`**
- Purpose: Create or edit trip + stops (shared component for both paths)
- Files: `trip-edit.page.ts`, `trip-edit.page.html`, `trip-edit.page.scss`
- Key behavior:
  - Detects URL: if `id` present (route param) → fetch existing trip; if not → new trip
  - Form for trip metadata (title, description, tags, visibility, startDate)
  - Stop list editor (add, reorder, remove stops)
  - Save button calls `TripService.createTrip()` or `TripService.updateTrip()`
  - Optimize button calls `TripService.optimizeTrip()` (reorder, add scheduling)

**`pages/trips/components/`**
- Purpose: Reusable sub-components for trip pages
- Contains:
  - `trip-map/` — Mapbox map display, stop markers
  - `stop-list/` — Stops table/list, edit/delete actions
  - `stop-photo-gallery/` — Photos for a stop, delete actions
  - `stop-photo-upload/` — Cloudinary signed upload form
  - `edit-stop-form/` — Form to add/edit a stop (modal or inline)
  - `ai-preferences-form/` — Form for AI itinerary suggestions (interests, budget, pace)
  - `ai-suggestion-cards/` — Display suggested stops from Gemini
  - `ai-trip-prompt/` — Free-text prompt input for generating a whole new trip
- Pattern: Each is a `standalone: true` component; takes `@Input` and `@Output` for communication
- Add new file: For a new trip feature, break it into small reusable components here

**`pages/starting-up/`**
- Purpose: Loading screen shown while backend is starting up (SCRUM-273)
- Files: `starting-up.page.ts`, `starting-up.page.html`, `starting-up.page.scss`
- Behavior: Polls `/actuator/health` until backend responds; then navigates to dashboard

**`environments/`**
- Purpose: Environment-specific config (dev vs prod)
- Files: `environment.ts` (dev), `environment.prod.ts` (prod)
- Key variable: `apiBaseUrl` (defaults to `http://localhost:8080` in dev; CI build replaces with prod URL via `sed`)
- Add new config: If frontend needs API keys or feature flags, add to both environment files; use `environment.apiBaseUrl` as the base

**`theme/`**
- Purpose: Ionic global SCSS variables (colors, fonts, spacing)
- Files: Usually one `variables.scss` file with `--ion-color-primary`, etc.
- Used by: Ionic components and global styles

**`global.scss`**
- Purpose: Global CSS that escapes view encapsulation (e.g., Mapbox popup/marker styling)
- Usage: Only for styles that must cross component boundaries; most styles should be in component `.scss` files

**`src/main.ts`**
- Purpose: Angular bootstrap
- Pattern: Calls `bootstrapApplication(AppComponent, appConfig)` where `appConfig` provides HTTP, routing, Ionic, etc.

**`angular.json`, `tsconfig.json`, `karma.conf.js`, `package.json`, `.eslintrc.json`**
- Purpose: Build, test, lint configuration
- Read-only unless changing build/test behavior

## Key File Locations

**Backend Entry Points:**
- `BackendApplication.java` — Spring Boot main class
- `SecurityConfig.java` — Security bean, filter chain
- `application.properties` — Externalized config

**Frontend Entry Points:**
- `main.ts` — Angular bootstrap
- `app.routes.ts` — Routing
- `index.html` — HTML shell

**Database Schema:**
- `src/main/resources/db/migration/V*.sql` — Flyway migrations (apply in order)

**API Contracts:**
- `docs/api-contracts.md` — Canonical endpoint documentation
- `docs/auth.md` — JWT flow, auth header format
- `docs/LOGGING_STANDARD.md` — Logging rules

## Naming Conventions

### Backend (Java)

**Files:**
- Controllers: `*Controller.java` (e.g., `TripController`)
- Services: `*Service.java` (e.g., `TripService`)
- Repositories: `*Repository.java` extending `JpaRepository` (e.g., `TripRepository`)
- Entities: PascalCase entity name (e.g., `Trip.java`, `User.java`)
- DTOs: `*Request.java` for inputs, `*Response.java` for outputs (e.g., `CreateTripRequest`, `TripResponse`)
- Exceptions: `*Exception.java` (e.g., `ResourceNotFoundException`)
- Tests: `*Test.java` for unit, `*IT.java` for integration (e.g., `TripServiceTest`, `TripControllerIT`)

**Classes/Variables:**
- PascalCase for classes: `TripService`, `UserPrincipal`
- camelCase for variables/methods: `tripId`, `listTrips()`, `userId()`
- UPPERCASE for constants: `TRIP_STATUS_DRAFT`

### Frontend (TypeScript)

**Files:**
- Pages: `feature-name.page.ts`, `feature-name.page.html`, `feature-name.page.scss` (kebab-case)
- Components: `component-name.component.ts`, `component-name.component.html`, `component-name.component.scss` (kebab-case)
- Services: `feature.service.ts` (kebab-case)
- Models: `feature.model.ts` (kebab-case; or `.types.ts` for interfaces)
- Tests: `*.spec.ts` (co-located with source)

**Exports:**
- Class name PascalCase: `export class DashboardPage {}`, `export class TripMapComponent {}`
- Function name camelCase: `export function authGuard() {}`
- Type/interface PascalCase: `export interface TripResponse {}`, `export type PagedResponse<T> = ...`

**Selectors:**
- Component selector kebab-case: `@Component({ selector: 'app-trip-map', ... })`

## Where to Add New Code

### New Backend Feature (CRUD Endpoint)

**Example: Add a "Community Posts" feature**

1. Create domain entity: `backend/src/main/java/com/tripflow/backend/domain/CommunityPost.java` (`@Entity`, relationships)
2. Create DTOs:
   - `backend/src/main/java/com/tripflow/backend/dto/CreateCommunityPostRequest.java`
   - `backend/src/main/java/com/tripflow/backend/dto/CommunityPostResponse.java`
3. Create repository: `backend/src/main/java/com/tripflow/backend/repository/CommunityPostRepository.java` (extends `JpaRepository`)
4. Create service: `backend/src/main/java/com/tripflow/backend/service/CommunityPostService.java` (CRUD logic)
5. Create controller: `backend/src/main/java/com/tripflow/backend/controller/CommunityPostController.java` (HTTP routes)
6. Create mapper: `backend/src/main/java/com/tripflow/backend/mapper/CommunityPostMapper.java` (entity ↔ DTO)
7. Add database migration: `backend/src/main/resources/db/migration/V9__create_community_posts.sql` (next version number)
8. Add tests: `backend/src/test/java/com/tripflow/backend/service/CommunityPostServiceTest.java` (unit), and `CommunityPostControllerIT.java` (integration, if backend startup needed)
9. Update API contracts: add endpoint section to `docs/api-contracts.md`
10. Add route to frontend: update `app.routes.ts` if needed
11. Create frontend service: `frontend/src/app/core/services/community.service.ts` (backend calls)
12. Create frontend pages/components: `frontend/src/app/pages/community/` (UI)

### New External API Integration

**Example: Add Slack notifications**

1. Create client directory: `backend/src/main/java/com/tripflow/backend/client/slack/`
2. Add files:
   - `SlackClient.java` (HTTP wrapper, calls Slack API)
   - `SlackProperties.java` (`@ConfigurationProperties`, reads `app.slack.api-key` from env)
   - `SlackClientConfig.java` (RestTemplate bean)
   - `SlackMessage.java` / `SlackResponse.java` (wire-format DTOs)
   - `SlackClientException.java` (exception type)
3. Add to `application.properties`: `app.slack.api-key=${SLACK_API_KEY}` (read from env)
4. Inject `SlackClient` into a service (e.g., `TripService` to notify on trip creation)
5. Catch `SlackClientException`, let it propagate (GlobalExceptionHandler maps to 502)
6. Update `.env.example`: add `SLACK_API_KEY=...`
7. Test: `SlackClientTest.java` (mock HTTP), `TripServiceIT.java` (with Testcontainers + MockRestServiceServer)

### New Frontend Feature

**Example: Add trip sharing**

1. Create models: `frontend/src/app/core/models/trip.model.ts` (add `ShareTripRequest`, `SharedTripResponse` interfaces)
2. Create service methods: `frontend/src/app/core/services/trip.service.ts` (add `shareTrip()` method)
3. Create page or component:
   - If standalone flow: create `frontend/src/app/pages/trips/share-trip/share-trip.page.ts` (form for email, permissions)
   - If modal: create component in `frontend/src/app/pages/trips/components/share-trip-modal/`
4. Add route: `app.routes.ts` if standalone page
5. Add button to trip-view: edit `trip-view.page.html`, call `shareTrip()` on submit
6. Tests: `*.spec.ts` co-located with source, use `provideHttpClientTesting()`, mock service responses

### New Test

**Backend Unit Test:**
- Location: `backend/src/test/java/com/tripflow/backend/service/MyServiceTest.java`
- Pattern: Arrange-Act-Assert, Mockito mocks for dependencies
- Example: Test `TripService.getTrip()` with mocked repository, assert 403 on private trip access

**Frontend Unit Test:**
- Location: `frontend/src/app/core/services/my.service.spec.ts`
- Pattern: Arrange-Act-Assert, `HttpClientTestingModule` or `provideHttpClientTesting()`
- Example: Test `TripService.listTrips()` with mocked HTTP response

**Backend Integration Test (Testcontainers, CI-only):**
- Location: `backend/src/test/java/com/tripflow/backend/controller/MyControllerIT.java`
- Pattern: Full Spring context, Postgres container, real database calls
- Run: `mvn verify -Pci` (CI profile only; no team machine runs Docker)
- Example: Test trip creation end-to-end with real repository + database

### Bug Fix

**Apply the Ladder:**

1. **Does this need to exist at all?** — Is the bug in a feature being removed? Skip it.
2. **Already in this codebase?** — Is there a helper/util that could be reused instead of the buggy code? Use it.
3. **Stdlib does it?** — Java/TypeScript stdlib, Angular/Spring built-ins? Prefer them.
4. **Native platform feature covers it?** — Ionic component, HTML5, PostgreSQL constraint? Use it.
5. **Already-installed dependency solves it?** — Don't add new dependencies for what's already available.
6. **Can it be one line?** — One-line fix beats complex refactor if it solves the root cause.
7. **Only then:** the minimum code that works.

**Example: GET /api/trips returns null for a new trip's stopCount**

- Root cause: mapper doesn't handle empty stops array
- One-line fix: in `TripMapper.toResponse()`, use `trip.getStops().size()` instead of relying on collection initialization
- Don't: refactor the entire entity layer to add a field

## Special Directories

**`.planning/codebase/`**
- Purpose: Codebase analysis documents (ARCHITECTURE.md, STRUCTURE.md, CONVENTIONS.md, TESTING.md, STACK.md, INTEGRATIONS.md, CONCERNS.md)
- Generated: Yes (via `/gsd-map-codebase` agent command)
- Committed: Yes (tracked in git, used by other GSD agents for context)

**`.claude/`**
- Purpose: Claude Code project config (CLAUDE.md, skills directory)
- Generated: No (committed manually, project instructions)
- Committed: Yes

**`.github/workflows/`**
- Purpose: GitHub Actions CI pipeline (backend-ci.yml, frontend-ci.yml, pr-title-check.yml)
- Generated: No (committed, runnable)
- Committed: Yes

**`backend/src/main/resources/db/migration/`**
- Purpose: Flyway SQL migrations (version-controlled schema)
- Generated: No (written by hand for schema changes)
- Committed: Yes (never edited once applied; new version numbers only)
- Note: On local boot, Flyway validates existing migrations; migrations are idempotent (or should be)

**`backend/target/`, `frontend/node_modules/`, `frontend/dist/`**
- Purpose: Build artifacts, dependencies
- Generated: Yes (build outputs)
- Committed: No (.gitignore excludes these)

---

*Structure analysis: 2026-08-06*
