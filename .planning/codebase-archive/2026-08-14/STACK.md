# Technology Stack

**Analysis Date:** 2026-08-06

## Languages

**Primary:**
- Java 21 - Backend (Spring Boot 4.1)
- TypeScript 5.9 - Frontend (Angular 20, strict mode enabled)
- SQL (PostgreSQL dialect) - Database migrations via Flyway

**Secondary:**
- SCSS - Frontend styling
- HTML5 - Frontend templates
- JSON - Configuration and data interchange

## Runtime

**Environment:**
- JVM 21 (Temurin distribution in CI via actions/setup-java@v5)
- Node.js 20 (Frontend build and development, specified in frontend-ci.yml)
- PostgreSQL 16 (database server, local dev requirement per CLAUDE.md)

**Package Manager:**
- Maven 3.9+ (Backend) - uses `.mvn/wrapper/maven-wrapper.properties` for deterministic builds
- npm (Frontend) - `package-lock.json` present for dependency locking

## Frameworks

**Core:**
- Spring Boot 4.1.0 - Web application framework, REST API
  - `spring-boot-starter-data-jpa` - ORM via Hibernate
  - `spring-boot-starter-security` - Authentication/authorization
  - `spring-boot-starter-webmvc` - MVC and REST
  - `spring-boot-starter-restclient` - HTTP client for external APIs
  - `spring-boot-starter-actuator` - Runtime monitoring and health checks
  - `spring-boot-starter-validation` - Bean validation (Jakarta)

- Angular 20.3.27 - Frontend SPA framework (standalone components, no NgModules)
- Ionic 8.8.16 - Mobile PWA framework built on Angular
- Capacitor 8.5.0 - Bridge for native mobile capabilities (iOS/Android)

**Database:**
- Flyway 10 (version from Spring Boot 4.1 parent BOM) - Database migrations (PostgreSQL-specific)
  - Migrations located at `backend/src/main/resources/db/migration/`
  - Schema enforcement via `spring.jpa.hibernate.ddl-auto=validate`
- PostgreSQL JDBC Driver (runtime scope)
- Hibernate - JPA provider

**Auth:**
- JJWT 0.13.0 (JSON Web Tokens) - JWT generation and validation
  - `jjwt-api`, `jjwt-impl`, `jjwt-jackson` - API, runtime, Jackson serialization support
  - Configured via `JwtConfig` and `JwtProperties` in `backend/src/main/java/com/tripflow/backend/security/`
  - Stateless auth via `JwtAuthFilter` (OncePerRequestFilter)

**HTTP & REST:**
- RestClient (Spring 6.1 built-in) - HTTP calls to external APIs, configured per-service
- OpenAPI 3.x via Springdoc - API documentation and Swagger UI (exposed in dev, disabled in prod)
  - `springdoc-openapi-starter-webmvc-ui` v3.0.3

**Testing:**
- JUnit 5 - Test framework (via Spring Boot starter parent)
- Mockito - Mocking framework (agent-based via maven-dependency-plugin)
- Jasmine 5.13.0 - Frontend test framework (Angular/Karma)
- Karma 6.4.0 - Frontend test runner with Chrome launcher
- Testcontainers 2.0.5 - Docker-based integration tests (CI-only, `-Pci` profile)
  - `testcontainers-postgresql` - PostgreSQL container for `*IT` integration tests
  - `testcontainers-junit-jupiter` - JUnit 5 integration
- ArchUnit 1.4.2 - Architecture boundary tests (layer enforcement, no Docker required)

**Build/Dev:**
- Maven Surefire - Unit test execution (excludes `*IT.java`)
- Maven Failsafe - Integration test execution (CI-only, `-Pci` profile)
- JaCoCo 0.8.15 - Code coverage measurement and reporting
  - Merges unit + integration coverage in CI via `jacoco-merged.exec`
  - Coverage floors: 92% overall / 80% changed files for backend, 93% statements / 84% branches / 90% functions / 94% lines for frontend
- Lombok - Annotation-based code generation (compile-time, excluded from Spring Boot plugin build)
- Angular CLI 20.3.32 - Frontend build tooling
- Angular DevKit 20.3.32 - Build orchestration

**Rate Limiting:**
- Bucket4j 8.10.1 - In-memory per-user rate limiting on Gemini/ORS endpoints (SCRUM-173)

**Calendar/ICS:**
- Biweekly 0.6.8 - iCalendar (RFC 5545) generation for `.ics` export endpoint (SCRUM-176)
  - Jackson 2 databind excluded to avoid version conflict

**Other:**
- Jackson 2 (jackson-databind 2.18 via Boot BOM, jackson-datatype-jsr310 for java.time support)
  - Required because Boot 4.1's web starters use Jackson 3, but RestClient and OrsService need Jackson 2
- Mapbox GL JS 3.27.0 - Frontend map rendering library
- @angular/cdk 20.2.14 - Component Dev Kit (layout utilities, accessibility)
- @angular/service-worker 20.3.27 - PWA service worker

## Configuration

**Environment:**
- `.env` file pattern (backend/.env, never committed) - holds secrets:
  - `DB_USERNAME`, `DB_PASSWORD` - PostgreSQL credentials
  - `JWT_SECRET`, `JWT_EXPIRY_MS` - JWT configuration
  - `ORS_API_KEY` - OpenRouteService API key
  - `GEMINI_API_KEY` - Google Gemini API key
  - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` - Image storage
  - Backend loads via `spring.config.import=optional:file:.env[.properties]`

- Profile-specific properties:
  - `application.properties` - Shared defaults (server port defaults to 8080, database driver, JPA settings, JWT, ORS, Gemini, Actuator, rate limits, schedule times, Cloudinary)
  - `application-dev.properties` - Local development (localhost PostgreSQL, debug logging, CORS for localhost:8100)
  - `application-prod.properties` - Production (environment-variable-driven DB/CORS, Swagger UI disabled, connection pool limited to 5)
  - `application-test.properties` - Test profile (test database via Testcontainers)

- Frontend environment files (`src/environments/`):
  - `environment.ts` - Development (localhost:8080 API backend, `__MAPBOX_TOKEN__` placeholder)
  - `environment.prod.ts` - Production (injected via CI sed substitution at build time)

**Build:**
- `pom.xml` - Maven configuration
  - Properties: Java 21, Testcontainers 2.0.5, JJWT 0.13.0, Springdoc 3.0.3, ArchUnit 1.4.2, Bucket4j 8.10.1, Biweekly 0.6.8
  - Plugins: Spring Boot Maven, Maven Compiler, Maven Dependency, Surefire, Failsafe (CI-only), JaCoCo
- `angular.json` - Angular build configuration (production budget: 5MB max, PWA service worker enabled)
- `tsconfig.json`, `tsconfig.app.json`, `tsconfig.spec.json` - TypeScript configuration (strict mode, es2022 target, es2020 modules)
- `karma.conf.js` - Test runner configuration (Jasmine + Chrome launcher, coverage thresholds)
- `.eslintrc.json` - ESLint rules for frontend (Angular/template linting)

## Platform Requirements

**Development:**
- JDK 21 (Temurin or compatible)
- Maven 3.9+
- Node.js 20+
- npm 9+
- PostgreSQL 16 (local database instance with `tripflow` database)
- Git

**CI/CD:**
- GitHub Actions (`.github/workflows/backend-ci.yml`, `frontend-ci.yml`)
- Ubuntu Linux runners
- Docker (Testcontainers, CI-only, requires `-Pci` Maven profile)

**Production:**
- JVM 21 runtime
- PostgreSQL 16 or later
- Node.js 20+ (if deploying as separate frontend artifact) or container
- Environment variables for secrets (DB credentials, JWT secret, API keys, CORS origins)

---

*Stack analysis: 2026-08-06*
