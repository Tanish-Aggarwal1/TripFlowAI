# Technology Stack

**Analysis Date:** 2026-08-14

## Languages

**Primary:**
- Java 21 - Backend service logic, APIs, database access
- TypeScript - Frontend application (strict mode enabled)
- SQL - Database schema and migrations (PostgreSQL)

**Secondary:**
- HTML/SCSS - Frontend templates and styling (Ionic/Angular)
- Bash - Maven wrapper scripts (`backend/mvnw`)

## Runtime

**Environment:**
- JVM: Eclipse Temurin 25 (JDK for build, JRE for production container)
- Node.js: 20 (frontend development and build only)

**Package Manager:**
- Maven 3 (via `backend/mvnw` wrapper)
  - Lockfile: `pom.xml` (dependency versions resolved by Maven's BOM strategy)
- npm (frontend)
  - Lockfile: `frontend/package-lock.json` (present, pinned versions)

## Frameworks

**Core:**
- Spring Boot 4.1.0 - Backend REST API framework
- Ionic 8.8.17 - Hybrid mobile/PWA framework
- Angular 20.3.27 - Frontend application framework (standalone components, no NgModules)

**Testing:**
- JUnit 5 (Jupiter) - Backend unit tests (via Spring Boot starter)
- Testcontainers 2.0.5 - Integration test containers (PostgreSQL) (`*IT.java` tests)
- Karma 6.4.0 - Frontend test runner
- Jasmine 5.13.0 - Frontend unit test framework

**Build/Dev:**
- Angular CLI 20.3.33 - Frontend build tooling
- Angular DevKit 20.3.33 - Build system
- Maven Surefire - Backend unit test execution (excludes `*IT.java`)
- Maven Failsafe - Backend integration test execution (CI-only, via `ci` profile)
- ESLint 9.16.0 - Frontend linting
- TypeScript 5.9.0 - Frontend type checking

## Key Dependencies

**Authentication & Security:**
- JJWT 0.13.0 - JWT token creation/validation (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- Spring Security 6 - Authorization and authentication framework

**Database & ORM:**
- Spring Data JPA - Object-relational mapping
- Hibernate (via Spring Data) - JPA provider
- Flyway 2 - Database migrations
- PostgreSQL driver - JDBC driver for PostgreSQL 16

**HTTP & REST:**
- Spring RestClient - Synchronous HTTP client for external APIs
- Jackson 2.18 - JSON serialization/deserialization (includes `jackson-databind`, `jackson-datatype-jsr310`)
- Capacitor 8.5.0 - Native bridge for Ionic PWA

**Utilities & Infrastructure:**
- Lombok - Code generation (getters, setters, logging annotations)
- Bucket4j 8.10.1 - Token bucket rate limiting (in-memory, per-user)
- Biweekly 0.6.8 - iCalendar (RFC 5545) generation for .ics export
- SpringDoc 3.1.0 - OpenAPI/Swagger UI documentation

**Testing Support:**
- Spring Boot Testcontainers 1.3.8 - Testcontainers integration
- Spring Security Test - Mock authentication for `@WebMvcTest` slices
- Mockito (via Maven dependency plugin) - Mock object framework
- ArchUnit 1.5.0 - Layer boundary tests (confirms `controller → service → repository` layering)

**Frontend Utilities:**
- MapBox GL 3.28.1 - Vector mapping library (interactive maps)
- RxJS 7.8.0 - Reactive programming (observables, operators)
- Angular CDK 20.2.14 - Component development kit
- Ionicons 8.1.0 - Icon library
- Angular Service Worker 20.3.27 - PWA/offline caching

**Development:**
- Axe-core 4.13.0 - Accessibility testing library
- `@types/*` packages - TypeScript type definitions (geojson, mapbox-gl, jasmine)

## Configuration

**Backend (Spring Boot):**
- `backend/src/main/resources/application.properties` - Default config, imports `.env` via `spring.config.import`
- `backend/src/main/resources/application-dev.properties` - Development profile (local PostgreSQL)
- `backend/src/main/resources/application-prod.properties` - Production profile (cloud database, Render proxy handling)
- `backend/src/test/resources/application-test.properties` - Test profile (low rate limits, test placeholders)

**Frontend (Angular):**
- `frontend/src/environments/environment.ts` - Development config (localhost:8080, placeholder tokens)
- `frontend/src/environments/environment.prod.ts` - Production config (injected at build time via `sed` in CI)
- `frontend/src/environments/environment.local.ts.template` - Template for local development (copied to `environment.local.ts`, gitignored)
- `frontend/angular.json` - Build configuration with `fileReplacements` for environment swapping
- `frontend/.eslintrc.json` - ESLint configuration

**Database:**
- `backend/src/main/resources/db/migration/V*__*.sql` - Flyway migrations (11 total, schema-driven)
- `spring.jpa.hibernate.ddl-auto=validate` (all profiles) - Enforces schema-first, never auto-generates

## Platform Requirements

**Development:**
- Local PostgreSQL 16 with `tripflow` database (dev profile)
- JDK 21 (build)
- Node.js 20 (frontend)
- Maven 3.x (or use `mvnw` wrapper)
- npm 10+ (or bundled with Node 20)

**Production:**
- Cloud-hosted PostgreSQL 16
- Docker (for containerized deployment)
- Render.com (current host, auto-deploys from git main branch)
- Environment variables: `JWT_SECRET`, `JWT_EXPIRY_MS`, `ORS_API_KEY`, `GEMINI_API_KEY`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`

## Build Artifacts

**Backend:**
- Docker multi-stage build: `backend/Dockerfile`
  - Stage 1 (build): Eclipse Temurin 25 JDK on Debian Jammy
  - Stage 2 (runtime): Eclipse Temurin 25 JRE (smaller image)
  - Entry: `java -jar app.jar` on port 8080
  - Runs with `SPRING_PROFILES_ACTIVE=prod` by default (no dev security fallback)

**Frontend:**
- Angular build: `npm run build` → `frontend/dist/app/` (production-optimized, AOT)
- Deployment: Render web service (Node.js buildpack, serves static files)
- Environment substitution: Mapbox token injected via `sed` in CI, API base URL if provided

---

*Stack analysis: 2026-08-14*
