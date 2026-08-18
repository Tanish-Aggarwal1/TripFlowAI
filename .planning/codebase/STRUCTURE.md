# Codebase Structure

**Analysis Date:** 2026-08-14

## Directory Layout

```
TripFlowAI/
├── backend/                               # Spring Boot 4.1, Java 21 backend
│   ├── src/main/java/com/tripflow/backend/
│   │   ├── BackendApplication.java        # App entry point
│   │   ├── ai/                            # AI prompt templates, parsers, structured output
│   │   │   ├── ItineraryPromptTemplate.java
│   │   │   ├── TripGenerationPromptTemplate.java
│   │   │   ├── GeminiResponseParser.java
│   │   │   ├── SuggestedItinerary.java
│   │   │   └── GeneratedTripPlan.java
│   │   ├── client/                        # External API integrations (ORS, Gemini, Cloudinary)
│   │   │   ├── ors/                       # OpenRouteService (route optimization, directions)
│   │   │   │   ├── OrsClient.java
│   │   │   │   ├── OrsClientConfig.java
│   │   │   │   ├── OrsDirectionsRequest.java
│   │   │   │   ├── OrsDirectionsResponse.java
│   │   │   │   ├── OrsOptimizationRequest.java
│   │   │   │   └── OrsOptimizationResponse.java
│   │   │   ├── gemini/                    # Google Gemini (AI itinerary generation)
│   │   │   │   ├── GeminiClient.java
│   │   │   │   ├── GeminiClientConfig.java
│   │   │   │   ├── GeminiGenerateContentRequest.java
│   │   │   │   └── GeminiGenerateContentResponse.java
│   │   │   └── cloudinary/                # Cloudinary (photo storage/delivery)
│   │   │       ├── CloudinaryConfig.java
│   │   │       ├── CloudinaryProperties.java
│   │   │       └── SignedUploadRequest.java
│   │   ├── config/                        # Spring configuration beans
│   │   │   ├── SecurityConfig.java        # Spring Security, JWT filter chain
│   │   │   ├── JwtConfig.java             # JWT generation/validation beans
│   │   │   ├── JpaConfig.java             # JPA/Hibernate beans
│   │   │   ├── JacksonConfig.java         # JSON serialization settings
│   │   │   ├── OpenApiConfig.java         # Springdoc OpenAPI/Swagger
│   │   │   └── SchedulingConfig.java      # @EnableScheduling, async task config
│   │   ├── controller/                    # REST endpoints (@RestController)
│   │   │   ├── AuthController.java        # /api/auth/** (login, register)
│   │   │   ├── TripController.java        # /api/trips/** (CRUD, optimize, clone, like)
│   │   │   ├── StopController.java        # /api/trips/{id}/stops/** (stop CRUD)
│   │   │   ├── StopPhotoController.java   # /api/trips/{id}/stops/{id}/photos/** (uploads)
│   │   │   ├── AiController.java          # /api/trips/{id}/ai-** (generation, suggestions)
│   │   │   ├── TripExportController.java  # /api/trips/{id}/calendar.ics (iCalendar)
│   │   │   └── DiscoveryController.java   # /api/discovery/** (public trip browsing)
│   │   ├── domain/                        # JPA entities (@Entity, relationships)
│   │   │   ├── BaseEntity.java            # Abstract base (id, createdAt, updatedAt)
│   │   │   ├── User.java                  # User account entity
│   │   │   ├── Trip.java                  # Multi-stop trip itinerary
│   │   │   ├── Stop.java                  # Single destination within a trip
│   │   │   ├── Place.java                 # Shared geographic location (POI/address)
│   │   │   ├── StopPhoto.java             # User-uploaded photo attached to a stop
│   │   │   ├── TripLike.java              # Composite-key: (trip, user) like record
│   │   │   ├── TripLikeId.java            # Composite key class
│   │   │   └── enums/
│   │   │       ├── TripStatus.java        # DRAFT, IN_PROGRESS, COMPLETED
│   │   │       ├── TripVisibility.java    # PUBLIC, PRIVATE
│   │   │       ├── StopStatus.java        # PLANNED, VISITED, SKIPPED
│   │   │       └── StopType.java          # SIGHTSEEING, MEAL, LODGING, OTHER
│   │   ├── dto/                           # Request/response DTOs (wire format)
│   │   │   ├── AuthResponse.java          # Login/register response (JWT, user info)
│   │   │   ├── LoginRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   ├── CreateTripRequest.java
│   │   │   ├── UpdateTripRequest.java
│   │   │   ├── TripResponse.java
│   │   │   ├── TripSummaryResponse.java   # Paginated list response (no stops)
│   │   │   ├── CreateStopRequest.java
│   │   │   ├── UpdateStopRequest.java
│   │   │   ├── UpsertStopRequest.java     # Merge-by-identity for full trip replace
│   │   │   ├── StopResponse.java
│   │   │   ├── CreateStopPhotoRequest.java
│   │   │   ├── StopPhotoResponse.java
│   │   │   ├── PhotoSignatureResponse.java
│   │   │   ├── GenerateTripRequest.java   # AI trip generation prompt
│   │   │   ├── ItineraryPreferencesRequest.java  # AI suggestion preferences
│   │   │   ├── SuggestedItineraryResponse.java
│   │   │   └── PagedModel.java            # Spring HATEOAS paged response wrapper
│   │   ├── exception/                     # Custom exceptions & global handler
│   │   │   ├── ApiError.java              # Canonical error response shape
│   │   │   ├── GlobalExceptionHandler.java # @RestControllerAdvice, all exception handlers
│   │   │   ├── ResourceNotFoundException.java
│   │   │   ├── ForbiddenException.java
│   │   │   ├── InvalidRequestException.java
│   │   │   ├── InvalidCredentialsException.java
│   │   │   ├── DuplicateEmailException.java
│   │   │   ├── DuplicateUsernameException.java
│   │   │   ├── ConflictException.java
│   │   │   ├── InsufficientStopsException.java  # < 2 stops for optimization
│   │   │   ├── OrsClientException.java
│   │   │   ├── OrsRateLimitException.java
│   │   │   ├── GeminiClientException.java
│   │   │   ├── GeminiParsingException.java
│   │   │   ├── InvalidPhotoUrlException.java
│   │   │   └── PromptTooLargeException.java
│   │   ├── mapper/                        # Entity ↔ DTO conversions (manual)
│   │   │   ├── TripMapper.java
│   │   │   ├── StopMapper.java
│   │   │   └── AiItineraryMapper.java
│   │   ├── ratelimit/                     # Request rate limiting
│   │   │   ├── RateLimiterService.java
│   │   │   ├── RateLimitConfig.java
│   │   │   ├── RateLimitProperties.java
│   │   │   └── RateLimitExceededException.java
│   │   ├── repository/                    # Spring Data JPA repositories
│   │   │   ├── UserRepository.java        # User by email, username
│   │   │   ├── TripRepository.java        # Trip CRUD, search, like counters
│   │   │   ├── TripSearchRepository.java  # Custom full-text search interface
│   │   │   ├── TripSearchRepositoryImpl.java # Custom full-text search implementation
│   │   │   ├── StopRepository.java        # Stop CRUD
│   │   │   └── TripLikeRepository.java    # Like composite-key operations
│   │   ├── schedule/                      # Async scheduled tasks
│   │   │   ├── ItineraryScheduler.java    # Assign dayNumber/plannedTime to stops
│   │   │   ├── RouteScheduleConfig.java
│   │   │   └── RouteScheduleProperties.java
│   │   ├── security/                      # JWT, authentication, authorization
│   │   │   ├── JwtAuthFilter.java         # Per-request JWT validation filter
│   │   │   ├── JwtService.java            # JWT token generation/parsing
│   │   │   ├── UserPrincipal.java         # Spring Security principal (not persisted)
│   │   │   ├── SecurityConfig.java        # SecurityFilterChain bean, permitAll patterns
│   │   │   ├── JsonAuthenticationEntryPoint.java  # 401 response writer
│   │   │   ├── JsonAccessDeniedHandler.java      # 403 response writer
│   │   │   └── SecurityErrorWriter.java   # Shared error response formatter
│   │   └── service/                       # Business logic layer (@Service, @Transactional)
│   │       ├── AuthService.java           # User registration, login, credential validation
│   │       ├── TripService.java           # Trip CRUD (no stop CRUD — see StopService)
│   │       ├── StopService.java           # Stop CRUD (nested under trip)
│   │       ├── TripOwnershipService.java  # Permission checks (owner-only load)
│   │       ├── PlaceResolutionService.java # Place lookup/creation (geocoding cache)
│   │       ├── RouteOptimizationService.java  # ORS VROOM, directions, scheduling
│   │       ├── AiItineraryService.java    # Gemini itinerary suggestions
│   │       ├── AiTripGenerationService.java # Gemini full trip generation
│   │       ├── TripCloneService.java      # Deep-copy public trips
│   │       ├── TripLikeService.java       # Like/unlike trips
│   │       └── IcsExportService.java      # iCalendar format export
│   ├── src/main/resources/
│   │   ├── application.yaml               # Spring Boot config (dev, prod profiles)
│   │   ├── application-dev.properties     # Dev-specific config (no defaults for secrets)
│   │   ├── application-prod.properties    # Prod config (injected at build time)
│   │   ├── db/migration/                  # Flyway SQL migrations (V{n}__description.sql)
│   │   └── logback-spring.xml             # Logging configuration
│   ├── src/test/java/com/tripflow/backend/ # Unit & integration tests (same package structure)
│   │   ├── service/                       # *Test.java (unit), *IT.java (integration)
│   │   ├── controller/                    # @WebMvcTest slice tests
│   │   ├── repository/                    # Repository tests (Testcontainers)
│   │   └── security/                      # JWT, auth filter tests
│   ├── pom.xml                            # Maven build config, dependencies
│   ├── .env.example                       # Template for .env (copy to .env, fill secrets)
│   └── mvnw                               # Maven wrapper (use mvnw not mvn)
│
├── frontend/                              # Ionic 8 + Angular 20 PWA, TypeScript strict mode
│   ├── src/app/
│   │   ├── app.component.ts/.html/.scss   # Root component (@Component selector='app-root')
│   │   ├── app.routes.ts                  # Standalone routing config (no NgModules)
│   │   ├── core/                          # Shared services, guards, interceptors, models
│   │   │   ├── guards/
│   │   │   │   └── auth.guard.ts          # Functional guard (canActivate), redirects to login
│   │   │   ├── http/
│   │   │   │   └── api-error.mapper.ts    # Typed error mapping (status → message)
│   │   │   ├── interceptors/
│   │   │   │   ├── auth.interceptor.ts    # Injects JWT in Authorization header
│   │   │   │   ├── backend-availability.interceptor.ts  # Checks backend health
│   │   │   │   └── session-expiry.interceptor.ts  # Logs out on 403/401
│   │   │   ├── models/
│   │   │   │   ├── auth.model.ts          # AuthResponse, LoginRequest, RegisterRequest
│   │   │   │   └── trip.model.ts          # All Trip/Stop/Place TypeScript interfaces
│   │   │   └── services/
│   │   │       ├── auth.service.ts        # Login, register, JWT storage (localStorage)
│   │   │       ├── trip.service.ts        # All trip/stop/AI API calls (typed HttpClient)
│   │   │       ├── stop-photo.service.ts  # Photo upload (Cloudinary signature requests)
│   │   │       └── toast.service.ts       # User notifications (@ionic/angular Toaster)
│   │   └── pages/                         # Full-page components (route entry points)
│   │       ├── auth/
│   │       │   ├── login/
│   │       │   │   ├── login.page.ts/.html/.scss
│   │       │   │   └── login.page.spec.ts
│   │       │   └── signup/
│   │       │       ├── signup.page.ts/.html/.scss
│   │       │       └── signup.page.spec.ts
│   │       ├── starting-up/               # Splash screen (future use)
│   │       │   ├── starting-up.page.ts/.html/.scss
│   │       │   └── starting-up.page.spec.ts
│   │       └── trips/
│   │           ├── dashboard/             # User's trip list, create/clone
│   │           │   ├── dashboard.page.ts/.html/.scss
│   │           │   └── dashboard.page.spec.ts
│   │           ├── trip-view/             # View trip details, map, AI suggestions
│   │           │   ├── trip-view.page.ts/.html/.scss
│   │           │   └── trip-view.page.spec.ts
│   │           ├── trip-edit/             # Create/edit trip (form with dynamic stops)
│   │           │   ├── trip-edit.page.ts/.html/.scss
│   │           │   └── trip-edit.page.spec.ts
│   │           └── components/            # Reusable, fully standalone components
│   │               ├── trip-map/          # Mapbox map, polyline, markers
│   │               │   ├── trip-map.component.ts
│   │               │   ├── trip-map.component.html
│   │               │   ├── trip-map.component.scss
│   │               │   └── trip-map.component.spec.ts
│   │               ├── stop-list/         # Iterable list of stops with status badges
│   │               │   └── stop-list.component.*
│   │               ├── edit-stop-form/    # Form to add/edit a single stop
│   │               │   └── edit-stop-form.component.*
│   │               ├── ai-trip-prompt/    # Modal: prompt for full AI trip generation
│   │               │   └── ai-trip-prompt.component.*
│   │               ├── ai-preferences-form/ # Modal: preferences for itinerary suggestions
│   │               │   └── ai-preferences-form.component.*
│   │               ├── ai-suggestion-cards/ # Cards displaying AI-suggested stops
│   │               │   └── ai-suggestion-cards.component.*
│   │               ├── stop-photo-gallery/ # Gallery of uploaded photos for a stop
│   │               │   └── stop-photo-gallery.component.*
│   │               └── stop-photo-upload/  # Form to upload photos (Cloudinary)
│   │                   └── stop-photo-upload.component.*
│   ├── src/environments/
│   │   ├── environment.ts                 # Prod default (placeholder tokens)
│   │   ├── environment.prod.ts            # Prod build config (injected at build time)
│   │   ├── environment.local.ts.template  # Template for local dev (gitignored copy)
│   │   └── environment.local.ts           # Git-ignored; filled with real Mapbox token
│   ├── src/
│   │   ├── index.html                     # Root HTML, <app-root></app-root>
│   │   ├── main.ts                        # Angular bootstrap (bootstrapApplication)
│   │   ├── styles.scss                    # Global styles (minimal, avoid duplication)
│   │   └── polyfills.ts                   # Browser compatibility
│   ├── angular.json                       # Angular CLI config, build targets, fileReplacements
│   ├── package.json                       # npm dependencies, scripts (ng, ionic, karma, etc.)
│   ├── karma.conf.js                      # Test runner config (Jasmine)
│   ├── tsconfig.json                      # TypeScript config (strict: true)
│   └── .eslintrc.json                     # ESLint config (TSlint rules, Angular best practices)
│
├── docs/                                  # Living documentation (architecture decisions, API contracts)
│   ├── README.md                          # High-level overview, getting started
│   ├── ARCHITECTURE.md                    # Detailed architecture & rationale
│   ├── STRUCTURE.md                       # Directory structure (this file)
│   ├── api-contracts.md                   # Endpoint specifications, request/response shapes
│   ├── auth.md                            # Auth flow, JWT, 401/403 semantics
│   ├── ci.md                              # CI/CD pipeline, coverage requirements
│   ├── LOGGING_STANDARD.md                # Logging conventions, what/how to log
│   ├── frontend-standards.md              # Frontend conventions (naming, component structure)
│   └── testing.md                         # Testing patterns, test data, mocking
│
├── .github/
│   ├── workflows/
│   │   ├── backend-ci.yml                 # CI: mvn verify -Pci (unit + IT tests, coverage)
│   │   ├── frontend-ci.yml                # CI: ng build, npm test, lint
│   │   └── pr-title-check.yml             # Enforce PR title format [SCRUM-XXX] type(scope):
│   ├── pull_request_template.md           # PR submission template (Summary, Testing, Checklist)
│   └── CONTRIBUTING.md                    # Contribution guidelines
│
├── .gitignore                             # Ignore build artifacts, .env, node_modules, etc.
├── .gitattributes                         # Custom merge drivers (graphify merge driver)
├── CLAUDE.md                              # Project instructions for Claude Code
├── README.md                              # Project overview, setup, deployment
└── .env.example                           # Template for root .env (if any shared config)
```

## Directory Purposes

### Backend (`backend/`)

**Core Packages:**

**`ai/`**
- Prompt template rendering for Gemini (itinerary & trip generation)
- Response parsing (JSON parsing, error handling)
- Structured data classes (`SuggestedItinerary`, `GeneratedTripPlan`)
- NOT auto-generated code; hand-written with careful prompt engineering

**`client/{service}/`** (ors, gemini, cloudinary)
- HTTP client instantiation and configuration
- Wire-format request/response models (records with `@JsonIgnoreProperties(ignoreUnknown = true)`)
- `@ConfigurationProperties` beans (with secrets masked in `toString()`)
- Service-specific exceptions (e.g., `OrsClientException`, `GeminiClientException`)
- Per-client connect/read timeouts configured independently

**`config/`**
- Spring bean definitions (`@Configuration` classes)
- Security filter chain, JWT config, JPA/Hibernate, JSON serialization
- OpenAPI/Swagger configuration
- Scheduling/async task setup

**`controller/`**
- HTTP entry points (`@RestController`, `@RequestMapping`)
- Request validation (`@Valid`), path/query parameter binding
- Response wrapping (`ResponseEntity`)
- Delegates all business logic to services (never direct repository access)

**`domain/`**
- JPA entity classes (`@Entity`, relationships, enums)
- Base entity abstract class (id, createdAt, updatedAt audit fields)
- No business logic; entities are data holders only
- Relationships: User ← Trip → (Stop → Place), (StopPhoto), (TripLike)

**`dto/`**
- Request/response contract POJOs (records or classes)
- `@NotBlank`, `@Size`, `@Valid` annotations for validation
- Separate from domain entities (DTOs are wire format, entities are persistence format)

**`exception/`**
- Custom exception classes (hierarchy: `RuntimeException` subclasses)
- Global exception handler (`@RestControllerAdvice`)
- Canonical `ApiError` response shape (status, error, message, path, timestamp, fieldErrors)

**`mapper/`**
- Manual DTO ↔ Entity conversions (no code generation like MapStruct)
- Single responsibility: `TripMapper.toEntity()`, `TripMapper.toResponse()`

**`ratelimit/`**
- In-memory rate limiting (Guava cache)
- Per-scope tracking (IP address, user ID)
- Throws `RateLimitExceededException` (→ 429 response)

**`repository/`**
- Spring Data `JpaRepository` interfaces
- Custom query methods (`@Query` with JPQL/SQL)
- N+1 prevention: explicit `LEFT JOIN FETCH`, projection queries for pagination
- Never expose mutable entities; always create fresh instances or read-only projections

**`schedule/`**
- Async scheduled tasks (e.g., assign dayNumber/plannedTime to stops after route optimization)
- Cron/fixed-rate scheduling configuration

**`security/`**
- JWT token generation/validation
- `OncePerRequestFilter` for per-request JWT extraction
- Spring Security principal (`UserPrincipal`)
- Authentication/authorization exception handlers (401, 403 responses)

**`service/`**
- Business logic orchestration
- Ownership/authorization checks (not Spring Security annotations)
- Transaction boundaries (`@Transactional`)
- External API coordination (ORS, Gemini, Cloudinary)
- Deliberately NOT transactional across external API calls (SCRUM-210)

### Frontend (`frontend/src/app/`)

**`core/`**
- Shared, app-wide services and utilities
- Guards: route-level authorization (`authGuard`)
- Interceptors: request/response middleware (JWT injection, error handling)
- Models: TypeScript interfaces (mirrors backend DTOs)
- Services: `HttpClient`-based API calls, localStorage management

**`pages/`**
- Full-page components (lazy-loaded via routing)
- `DashboardPage`: user's trip list, create/clone/delete
- `LoginPage`, `SignupPage`: authentication flows
- `TripViewPage`: view trip details, map, AI suggestions
- `TripEditPage`: create/edit trip with dynamic stops
- `StartingUpPage`: splash screen (future)

**`pages/trips/components/`**
- Reusable component modules (fully standalone)
- `TripMapComponent`: Mapbox map, polyline, stop markers, click-to-pan
- `StopListComponent`: iterable stops with status badges
- `EditStopFormComponent`: add/edit single stop
- `AiTripPromptComponent`: modal for generating a whole trip from text
- `AiPreferencesFormComponent`: modal for suggesting stops on existing trip
- `AiSuggestionCardsComponent`: card-based display of AI suggestions
- `StopPhotoGalleryComponent`: view photos for a stop
- `StopPhotoUploadComponent`: upload photos to Cloudinary

**`environments/`**
- Dev: `environment.local.ts` (gitignored, swapped in by Angular fileReplacements)
- Prod: `environment.prod.ts` (committed with placeholder tokens, injected at build time)

### Documentation (`docs/`)

**`api-contracts.md`**
- Canonical endpoint specifications
- Request/response shapes (mirrors DTOs)
- Status code semantics (401 vs 403, 404 vs other 4xx)
- Updated whenever an API contract changes (authoritative source)

**`auth.md`**
- JWT flow (issuance, validation, expiry)
- 401 (no valid token) vs 403 (valid token, insufficient permission) semantics
- permitAll routes and rationale
- Password policy, credential validation

**`ci.md`**
- CI/CD pipeline stages (unit tests, integration tests, coverage)
- Coverage floor (92% overall, 80% changed files)
- How to run CI locally (Docker required for integration tests)
- Testcontainers vs local database setup

**`LOGGING_STANDARD.md`**
- Log levels and when to use each (ERROR, WARN, INFO, DEBUG, TRACE)
- What NOT to log (passwords, JWTs, API keys, PII bodies)
- Parameterized messages (avoid string concatenation)
- Audit trail expectations (one INFO line per business operation)

**`frontend-standards.md`**
- Component naming: `feature-name.component.*`
- Standalone components, no NgModules
- Control flow (`@if`, `@for`, `@switch` only)
- Service injection via `inject()`, not constructor
- Testing patterns (provideHttpClient, provideRouter)

## Key File Locations

**Backend Entry Points:**
- `backend/src/main/java/.../backend/BackendApplication.java` — app boot
- `backend/src/main/resources/application.yaml` — Spring config profiles
- `backend/src/main/resources/db/migration/V{n}__*.sql` — Flyway DDL (single source of truth for schema)

**Backend REST Controllers:**
- `backend/src/main/java/.../backend/controller/AuthController.java` — `/api/auth/**`
- `backend/src/main/java/.../backend/controller/TripController.java` — `/api/trips/**`
- `backend/src/main/java/.../backend/controller/StopController.java` — `/api/trips/{id}/stops/**`
- `backend/src/main/java/.../backend/controller/AiController.java` — `/api/trips/{id}/ai-**`
- `backend/src/main/java/.../backend/controller/TripExportController.java` — `/api/trips/{id}/calendar.ics`
- `backend/src/main/java/.../backend/controller/DiscoveryController.java` — `/api/discovery/**`

**Backend Services (Business Logic):**
- `backend/src/main/java/.../backend/service/TripService.java` — trip CRUD
- `backend/src/main/java/.../backend/service/StopService.java` — stop CRUD (nested)
- `backend/src/main/java/.../backend/service/RouteOptimizationService.java` — ORS integration
- `backend/src/main/java/.../backend/service/AiItineraryService.java` — Gemini suggestions
- `backend/src/main/java/.../backend/service/AiTripGenerationService.java` — Gemini full trip generation
- `backend/src/main/java/.../backend/service/TripOwnershipService.java` — authorization checks

**Backend Repositories (Data Access):**
- `backend/src/main/java/.../backend/repository/TripRepository.java` — trip queries, custom search
- `backend/src/main/java/.../backend/repository/StopRepository.java` — stop CRUD
- `backend/src/main/java/.../backend/repository/UserRepository.java` — user lookups

**Backend Domain Entities:**
- `backend/src/main/java/.../backend/domain/Trip.java` — trip entity
- `backend/src/main/java/.../backend/domain/Stop.java` — stop entity
- `backend/src/main/java/.../backend/domain/Place.java` — shared geographic location
- `backend/src/main/java/.../backend/domain/User.java` — user entity

**Backend Security:**
- `backend/src/main/java/.../backend/security/JwtAuthFilter.java` — per-request JWT validation
- `backend/src/main/java/.../backend/security/JwtService.java` — JWT generation/parsing
- `backend/src/main/java/.../backend/security/SecurityConfig.java` — Spring Security configuration
- `backend/src/main/java/.../backend/security/UserPrincipal.java` — authenticated principal

**Backend Error Handling:**
- `backend/src/main/java/.../backend/exception/GlobalExceptionHandler.java` — all exception handlers
- `backend/src/main/java/.../backend/exception/ApiError.java` — canonical error shape

**Backend External Integrations:**
- `backend/src/main/java/.../backend/client/ors/OrsClient.java` — OpenRouteService VROOM
- `backend/src/main/java/.../backend/client/gemini/GeminiClient.java` — Google Gemini
- `backend/src/main/java/.../backend/client/cloudinary/CloudinaryConfig.java` — Cloudinary setup

**Frontend Entry Points:**
- `frontend/src/main.ts` — Angular bootstrap
- `frontend/src/app/app.component.ts` — root component
- `frontend/src/app/app.routes.ts` — standalone routing config

**Frontend Core Services:**
- `frontend/src/app/core/services/auth.service.ts` — JWT management, login/register
- `frontend/src/app/core/services/trip.service.ts` — all trip/stop/AI API calls
- `frontend/src/app/core/services/stop-photo.service.ts` — photo upload via Cloudinary

**Frontend Authentication:**
- `frontend/src/app/core/guards/auth.guard.ts` — route-level authorization
- `frontend/src/app/core/interceptors/auth.interceptor.ts` — JWT injection
- `frontend/src/app/core/interceptors/session-expiry.interceptor.ts` — logout on 403/401

**Frontend Pages:**
- `frontend/src/app/pages/auth/login/login.page.ts` — login form
- `frontend/src/app/pages/auth/signup/signup.page.ts` — registration form
- `frontend/src/app/pages/trips/dashboard/dashboard.page.ts` — user's trips list
- `frontend/src/app/pages/trips/trip-view/trip-view.page.ts` — view trip details
- `frontend/src/app/pages/trips/trip-edit/trip-edit.page.ts` — create/edit trip

**Frontend Components:**
- `frontend/src/app/pages/trips/components/trip-map/trip-map.component.ts` — Mapbox map
- `frontend/src/app/pages/trips/components/stop-list/stop-list.component.ts` — stops iteration
- `frontend/src/app/pages/trips/components/edit-stop-form/edit-stop-form.component.ts` — stop form
- `frontend/src/app/pages/trips/components/ai-trip-prompt/ai-trip-prompt.component.ts` — AI generation modal
- `frontend/src/app/pages/trips/components/ai-preferences-form/ai-preferences-form.component.ts` — preferences form
- `frontend/src/app/pages/trips/components/ai-suggestion-cards/ai-suggestion-cards.component.ts` — suggestion display

**Frontend Configuration:**
- `frontend/src/environments/environment.ts` — prod defaults
- `frontend/src/environments/environment.local.ts.template` — local dev template
- `frontend/angular.json` — build config, fileReplacements for environment swapping
- `frontend/tsconfig.json` — TypeScript strict mode config

## Naming Conventions

### Backend (Java)

**Files:**
- Classes: `ClassName.java` (PascalCase)
- Interfaces: `InterfaceName.java` (PascalCase)
- Records: `RecordName.java` (PascalCase)
- Tests: `ClassNameTest.java` (unit), `ClassNameIT.java` (integration)

**Packages:**
- Feature/layer structure: `com.tripflow.backend.{layer}.{feature}`
- Layers: `controller`, `service`, `repository`, `domain`, `dto`, `mapper`, `security`, `exception`, `config`, `client`, `ai`, `ratelimit`, `schedule`

**Functions/Methods:**
- camelCase: `createTrip()`, `getTrip()`, `loadOwnedTrip()`
- Verbs for actions: `create*`, `get*`, `update*`, `delete*`, `list*`, `find*`, `search*`

**Variables:**
- camelCase: `tripId`, `ownerId`, `externalPlaceId`
- Predictable names: `trip`, `stop`, `place`, `user`, `response`, `request`

**Constants:**
- UPPER_SNAKE_CASE: `MAX_STOPS`, `DEFAULT_PAGE_SIZE`, `DRIVING_PROFILE`

**Entity Fields:**
- Match database column names (underscored in SQL, camelCase in Java)
- Examples: `id`, `userId`, `createdAt`, `updatedAt`, `likeCount`, `stopOrder`

### Frontend (TypeScript)

**Files:**
- Pages: `feature-name.page.ts`, `feature-name.page.html`, `feature-name.page.scss`
- Components: `component-name.component.ts`, `component-name.component.html`, `component-name.component.scss`
- Services: `feature.service.ts`
- Models: `feature.model.ts`
- Spec: `*.spec.ts`

**Functions/Methods:**
- camelCase: `createTrip()`, `loadTrips()`, `openTrip()`

**Variables:**
- camelCase: `tripId`, `trips`, `loading`, `error`
- Signals: `trips = signal([])`, `loading = signal(true)`
- Observables: `trips$`, `error$` ($ suffix convention)

**Constants:**
- UPPER_SNAKE_CASE: `MAX_STOPS`, `TOKEN_KEY`

**Component Class Members:**
- Inputs: `@Input() tripId: number;`
- Outputs: `@Output() tripDeleted = new EventEmitter<number>();`
- Injected services: `private tripService = inject(TripService);`

### Database (PostgreSQL)

**Tables:**
- snake_case: `users`, `trips`, `stops`, `places`, `stop_photos`, `trip_likes`

**Columns:**
- snake_case: `user_id`, `trip_id`, `stop_order`, `created_at`, `updated_at`, `like_count`, `route_geometry`

**Indexes:**
- Named: `idx_{table}_{column(s)}`
- Example: `idx_trips_user_id`, `idx_stops_trip_id`

**Constraints:**
- Primary keys: `{table}_pkey`
- Foreign keys: `{table}_fk_{referenced_table}`
- Unique: `{table}_uq_{columns}`

## Where to Add New Code

### New Backend Feature Endpoint

**Pattern:**
1. Create DTO class in `backend/src/main/java/.../backend/dto/` (request/response)
2. Create/update entity in `backend/src/main/java/.../backend/domain/` (if new entity type)
3. Create/update repository query in `backend/src/main/java/.../backend/repository/` (if new query needed)
4. Create/update service method in `backend/src/main/java/.../backend/service/` (business logic)
5. Add controller method in `backend/src/main/java/.../backend/controller/` (HTTP entry point)
6. Add test: `backend/src/test/java/.../backend/controller/ControllerNameTest.java` or `*IT.java`
7. Update `docs/api-contracts.md` with endpoint specification

**Example:** Add "bookmark trip" feature
- `BookmarkTripRequest.java` in `dto/`
- `TripBookmark.java` entity in `domain/`
- `TripBookmarkRepository.java` in `repository/`
- `TripBookmarkService.java` in `service/`
- `@PostMapping("/{id}/bookmark")` in `TripController`

### New Backend Service Integration (External API)

**Pattern:**
1. Create client module: `backend/src/main/java/.../backend/client/{servicename}/`
2. Create `@ConfigurationProperties` bean: `{ServiceName}Properties.java` (with secrets masked in `toString()`)
3. Create client class: `{ServiceName}Client.java` (HTTP calls, error handling)
4. Create DTOs: `{ServiceName}Request.java`, `{ServiceName}Response.java`
5. Create exception: `{ServiceName}ClientException.java`
6. Wire into `backend/src/main/java/.../backend/config/{ServiceName}Config.java`
7. Use in service: `service/MyService.java` calls `{serviceName}Client.someMethod()`
8. Handle exception in `GlobalExceptionHandler` (map to appropriate HTTP status)

**Example:** Add Stripe payment integration
- `backend/src/main/java/.../backend/client/stripe/`
- `StripeProperties.java` (API key, webhook secret)
- `StripeClient.java` (charge, webhook verification)
- `StripeException.java` (mapped to 502)

### New Frontend Page

**Pattern:**
1. Create page directory: `frontend/src/app/pages/{feature}/{feature-name}/`
2. Generate files: `{feature-name}.page.ts`, `.html`, `.scss`, `.spec.ts`
3. Make component standalone: `standalone: true` in `@Component`
4. Add route in `frontend/src/app/app.routes.ts`:
   ```typescript
   {
     path: 'feature/page',
     loadComponent: () => import('./pages/feature/page/page.page').then(m => m.PageComponent),
     canActivate: [authGuard]  // if protected
   }
   ```
5. Inject services via `inject()`: `private tripService = inject(TripService);`
6. Use core services for API calls (never direct `HttpClient`)

**Example:** Add "trip sharing" page
- `frontend/src/app/pages/trips/trip-share/trip-share.page.*`
- Route: `/trips/:id/share`
- Inject: `TripService`, `ToastService`

### New Frontend Component

**Pattern:**
1. Create component directory: `frontend/src/app/pages/{feature}/components/{component-name}/`
2. Generate files: `{component-name}.component.ts`, `.html`, `.scss`, `.spec.ts`
3. Make standalone: `standalone: true`, import shared modules (`IonButton`, `CommonModule`, etc.)
4. Define inputs: `@Input() trip!: TripResponse;`
5. Define outputs: `@Output() tripUpdated = new EventEmitter<TripResponse>();`
6. Use `inject()` for services
7. No NgModules; use tree-shakable standalone dependencies

**Example:** Add "cost calculator" component
- `frontend/src/app/pages/trips/components/cost-calculator/cost-calculator.component.*`
- `@Input() stops: StopResponse[];`
- `@Output() costCalculated = new EventEmitter<number>();`

### New Test (Unit or Integration)

**Backend (Unit):**
- File: `backend/src/test/java/.../backend/{layer}/{ClassName}Test.java`
- Framework: JUnit 5 + Mockito
- No Docker required; mocked dependencies
- Run: `mvn test` or `mvn test -Dtest=ClassNameTest`

**Backend (Integration):**
- File: `backend/src/test/java/.../backend/{layer}/{ClassName}IT.java`
- Framework: JUnit 5 + Testcontainers (PostgreSQL)
- Run: `mvn verify -Pci` (CI profile only; requires Docker)
- Local: usually skipped (see `docs/ci.md`)

**Frontend:**
- File: `frontend/src/app/{path}/{filename}.spec.ts`
- Framework: Jasmine + Karma
- Use `provideHttpClientTesting()`, `provideRouter()`, etc.
- Run: `npm test` or `npm run test:ci`

## Special Directories

**`backend/src/main/resources/db/migration/`**
- Flyway SQL migration files: `V{n}__description.sql`
- Schema is the single source of truth; never edit applied migrations
- Create new migration for every schema change
- Examples: `V1__initial_schema.sql`, `V2__add_trip_likes.sql`

**`backend/src/main/resources/`**
- `application.yaml` — shared Spring config
- `application-{profile}.properties` — profile-specific (dev, prod)
- `logback-spring.xml` — logging format and levels

**`frontend/src/environments/`**
- `environment.ts` — prod default (placeholder tokens: `__MAPBOX_TOKEN__`)
- `environment.prod.ts` — prod build (uses prod tokens, injected at CI)
- `environment.local.ts` — local dev (gitignored, real token)
- Angular's `fileReplacements` in `angular.json` swaps environments per build config

**`.github/workflows/`**
- CI/CD pipeline definitions (GitHub Actions YAML)
- `backend-ci.yml` — Maven build, test, coverage
- `frontend-ci.yml` — ng build, npm test, lint
- `pr-title-check.yml` — enforce `[SCRUM-XXX] type(scope):` format

**`.refactor/`, `.planning/`**
- Analysis documents (concerns, plans, tracking)
- Generated by GSD tools; committed to repo for context

---

*Structure analysis: 2026-08-14*
