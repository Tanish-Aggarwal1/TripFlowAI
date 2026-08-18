# Codebase Concerns

**Analysis Date:** 2026-08-06

## Tech Debt

**OrsProperties API key exposure in toString():**
- Issue: `OrsProperties` (a Java record) does not override `toString()`, so auto-generated record toString includes the plaintext `apiKey` field. Unlike `GeminiProperties`, which explicitly masks the API key in a custom `toString()` override (lines 31-37 of `GeminiProperties.java`), OrsProperties exposes the secret.
- Files: `backend/src/main/java/com/tripflow/backend/client/ors/OrsProperties.java`
- Impact: If OrsProperties is ever logged (via `log.debug("{}", orsProps)`, Spring Boot actuator endpoints that serialize beans, or error messages), the API key will leak into logs, monitoring systems, or error tracking services.
- Fix approach: Add an explicit `toString()` override to `OrsProperties` following the same masking pattern as `GeminiProperties`, or convert to a class and annotate with `@ToString.Exclude` on the apiKey field.

**ToastService migration incomplete (frontend):**
- Issue: During SCRUM-260 (error-toast centralization), `ToastService` was created with only a `showError()` method. However, two components still inject `ToastController` directly and create their own success/error toasts instead of using the service:
  - `ai-suggestion-cards.component.ts` (line 84-89): creates a success toast directly via `toastCtrl.create()`
  - `dashboard.page.ts` (line 34, 137-144): injects `ToastController` and implements a `showToast()` helper that creates toasts directly
- Files: 
  - `frontend/src/app/pages/trips/components/ai-suggestion-cards/ai-suggestion-cards.component.ts`
  - `frontend/src/app/pages/trips/dashboard/dashboard.page.ts`
  - `frontend/src/app/core/services/toast.service.ts`
- Impact: Toast creation logic is not centralized, making it harder to change presentation patterns globally. The ToastService is incomplete (no `showSuccess()` method), forcing components to work around it. Testing becomes harder with scattered toast creation.
- Fix approach: Either (a) extend `ToastService` with `showSuccess()` and other variants, then migrate both components to use it, or (b) continue centralizing by adding typed `show(message, type, duration)` helper and migrate the two call sites.

**Frontend PWA offline caching incomplete:**
- Issue: `ngsw-config.json` (lines 1-30) only caches static assets (app shell, images, fonts). It does not configure caching for API responses (`/api/**`), so offline users cannot access trip data or perform any backend operations beyond what's cached in service worker's static manifest.
- Files: `frontend/ngsw-config.json`
- Impact: PWA advertises offline capability via the manifest but offers no offline data access for API calls. Users on poor networks or in airplane mode see "offline" UI but cannot view cached trip information.
- Fix approach: Add a `dataGroups` section to `ngsw-config.json` to cache GET API responses (with appropriate TTL/staleness strategy) and add UI feedback indicating which features require connectivity. This is a **stretch goal** — document it as deferred and not blocking the MVP.

## Known Bugs

**Social features not yet implemented:**
- Issue: The social features traceability audit (`docs/social-features-traceability-audit.md`, sections 1-2) identified the following gaps between proposed features and shipped code:
  - **For You Feed**: No `GET /api/discovery/**` endpoints, no public-trip discovery page, no like/bookmark/rating features. Partially tracked in SCRUM-71 (To Do).
  - **Clone Trip**: No `POST /api/trips/{id}/clone` endpoint. Tracked in SCRUM-71d (To Do, awaiting SCRUM-71 dependency).
  - **Trip Tracking (geolocation-based auto-arrival)**: Manual visited toggle (`Stop.status`) is shipped via SCRUM-250. Automatic GPS detection is 0% complete — no geolocation code in frontend, no arrival-detection logic, no completion-percentage field.
- Files: N/A (feature absence, not code bugs)
- Trigger: Scheduled as fall/winter work per `docs/TripFlow_fall_Break_Plan.md` sections FB-19–FB-26. Not a blocker for the Aug 6 capstone presentation.
- Workaround: None — these are tracked as future feature work, not defects in existing code.

## Security Considerations

**Public trip sharing blocked by default-deny SecurityConfig:**
- Risk: The social audit (section 2) notes a gap: `SecurityConfig` denies all requests by default except `/api/auth/**` and `/actuator/health` (lines 54-56 of `SecurityConfig.java`). This means even a PUBLIC trip's `GET /api/trips/{id}` returns 401 for a logged-out visitor. Sharing a trip via a public link to someone without an account will not work unless `SecurityConfig` is modified to allow unauthenticated reads on public trips.
- Files: `backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java`
- Current mitigation: None — "sharing" currently means "share with other logged-in users only." The audit recommends explicitly **not** attempting to support public unauthenticated sharing (section 5, "Deliberately not recommending") because it would require reworking the deny-by-default posture, which is a larger security refactor than justified for a capstone scope.
- Recommendations: If public sharing becomes a requirement, add an endpoint matcher for `/api/trips/*/public` (or similar) that permits unauthenticated reads. Document the security implications (enumeration risk: an attacker can guess trip IDs). For now, leave as-is and update feature specs to reflect "share with logged-in users only."

**JWT token leakage in logs:**
- Risk: `JwtAuthFilter` (line 50) logs `log.warn("JWT validation threw on {}: {}", ...)` but does not log the token itself — good. However, any downstream code that logs the raw request/response body or exception message could include the Authorization header or token value if an error occurs outside the filter.
- Files: `backend/src/main/java/com/tripflow/backend/security/JwtAuthFilter.java`, `backend/src/main/java/com/tripflow/backend/exception/GlobalExceptionHandler.java`
- Current mitigation: CLAUDE.md rules (quoted in project instructions) state "Never log passwords, JWTs, `Authorization` headers, API keys, or PII bodies." `GlobalExceptionHandler` masks sensitive error details (line 94-97 for malformed JSON, line 111-113 for DataIntegrityViolation). But responsibility falls on all developers to follow this pattern.
- Recommendations: Add a security-review checklist item for PR reviews: "no Authorization/jwt/token in logged payloads". Consider using a servlet filter or logging library extension to strip sensitive headers from request logging if a request/response body logger is ever added.

**Rate limiting per-user, in-memory only:**
- Risk: `RateLimiterService` (lines 1-46 of `backend/src/main/java/com/tripflow/backend/ratelimit/RateLimiterService.java`) holds all token buckets in a `ConcurrentHashMap`. This works for the current single-instance deployment but will NOT work in a horizontally scaled cluster — each instance maintains its own buckets, so a user can bypass the limit by round-robining requests across multiple backend instances.
- Files: `backend/src/main/java/com/tripflow/backend/ratelimit/RateLimiterService.java`
- Impact: If the backend is ever deployed to multiple instances (e.g. via Kubernetes, load-balanced across dynos), rate limiting becomes ineffective. A bad actor can multiply the effective limit by the number of instances.
- Fix approach: When clustering becomes a requirement, migrate to a distributed bucket store (Bucket4j supports Redis/Hazelcast). For now, this is acceptable — document it and flag as a scaling concern.

## Performance Bottlenecks

**Geolocation polling without background support (Trip Tracking foreground MVP):**
- Problem: The social audit (section 2) recommends a foreground-only stop-arrival detection (FB-25) using the web Geolocation API, which is realistic for a PWA without a native shell. However, the audit explicitly warns against attempting true background geolocation (FB-26) until the native Capacitor build spike (`FB-14`) is complete, because background tracking requires either a native wrapper or complex service-worker hacks that are unreliable on iOS Safari.
- Files: `frontend/src/app/pages/trips/components/trip-map/trip-map.component.ts` (currently handles map display only; geolocation feature not yet implemented)
- Cause: Missing infrastructure (native shell) for background work.
- Improvement path: Implement foreground-only geolocation watch (web API) as part of FB-25. Defer background tracking (FB-26) until FB-14 (Capacitor native build) is shipped and stable. Document this constraint explicitly in feature specs.

## Fragile Areas

**RouteOptimizationService transaction management (SCRUM-210):**
- Files: `backend/src/main/java/com/tripflow/backend/service/RouteOptimizationService.java`
- Why fragile: The class is **intentionally not** `@Transactional` (line 55-61 javadoc explains: holding a database connection across two external HTTP calls to ORS would cause connection pool exhaustion). Instead, it orchestrates separate transactional calls on different beans:
  1. `tripOwnershipService.loadOwnedTrip()` (transactional, commits)
  2. Two ORS HTTP calls (no DB access)
  3. `tripRepository.save()` (itself transactional)
  
  This is correct, but fragile: adding `@Transactional` to this method would reintroduce the original bug. Adding DB calls between step 1 and 3 without understanding the transaction boundary could cause timeouts or connection leaks.
- Safe modification: Never add `@Transactional` to this method. If adding DB calls (e.g. logging, audit trails) in the ORS orchestration section, wrap them in a new `@Transactional` helper method on a separate bean. Add a comment explaining why.
- Test coverage: Covered by `RouteOptimizationControllerIT` and `RouteOptimizationConcurrencyIT`. Modification should add new concurrency test.

**Flyway migration order (R1 — tech debt from sprint 1):**
- Files: `backend/src/main/resources/db/migration/` (V1–V8 currently)
- Why fragile: `application.properties` sets `spring.jpa.hibernate.ddl-auto=validate` in every profile. Flyway is the single source of truth for schema. However, adding a new migration (e.g., SCRUM-71c for trip likes) must maintain sequential version numbers. SCRUM-161 in Jira has a Jira-only mistake: it says `V7__create_trip_likes.sql`, but V7 is already taken by `V7__stop_scheduling.sql`. This is an easy slip-up at implementation time.
- Safe modification: When implementing SCRUM-71c, use `V9__...` (not V7). The risk register (R1) flagged this as a known pitfall — always double-check the next free version number against the `db/migration` directory before creating a migration.
- Test coverage: Flyway validates on every boot; bad versions fail immediately. Risk is typo/confusion, not silent corruption.

## Scaling Limits

**Single-instance rate limiting (already discussed under Security):**
- Current capacity: Per-user token buckets, one endpoint, not shared across instances.
- Limit: Does not scale to multiple backend instances. Each instance gets its own bucket memory.
- Scaling path: Migrate to Redis-backed Bucket4j (requires adding Redis dependency and `RateLimitConfig` updates).

**Database connection pool sizing:**
- Current capacity: 5 connections in prod (`application-prod.properties`, line: `spring.datasource.hikari.maximum-pool-size=5`).
- Limit: Small default, but appropriate for a single-instance, modest-traffic capstone app. Will exhaust at ~5 concurrent requests (or fewer if requests hold connections longer).
- Scaling path: Increase pool size as traffic grows; monitor connection wait times via `spring.datasource.hikari.metrics` (Spring Boot 3 supports actuator histograms). For multi-instance, add a managed PostgreSQL pool (e.g. PgBouncer) in front.

## Dependencies at Risk

**Bucket4j added but not fully utilized:**
- Risk: `bucket4j-core` (version 8.10.1) is declared in `backend/pom.xml` and used correctly by `RateLimiterService`. However, SCRUM-173 (rate limiting) is listed in the risk register as "blocked on SCRUM-149 (AI-suggest endpoint)" — meaning rate limiting was architected early but only wired on specific endpoints once SCRUM-149 shipped. Verify that rate limiting is active on Gemini/ORS endpoints that matter (check `AiController`, `RouteOptimizationController` for `@RateLimited` or similar).
- Impact: Unused dependency is a small risk; active-but-incomplete rate limiting (applied to some endpoints, not others) is a bigger risk.
- Migration plan: None urgent — Bucket4j is mature and stable. If removing rate limiting is ever considered, remove the dependency and delete `backend/src/main/java/com/tripflow/backend/ratelimit/` directory.

**Mapbox token placeholder injection (SCRUM-13):**
- Risk: Real Mapbox token is injected at CI build time via `sed` substitution in `.github/workflows/frontend-ci.yml`. The token never appears in code; it's a placeholder (`__MAPBOX_TOKEN__`) in version control. This is correct, but if the CI step is ever modified (e.g., build tooling change), the substitution could break silently and the app would ship with a placeholder token, breaking the map.
- Files: `frontend/src/environments/environment.ts`, `environment.prod.ts` (contain placeholder)
- Impact: Map renders but shows "Mapbox credentials missing" error.
- Mitigation: CI includes a verification step (documented in risk register R13 as "mitigated"). A pre-release test confirms Mapbox initializes correctly.

## Missing Critical Features

**Trip discovery / For You Feed (SCRUM-71):**
- Problem: No public-trip discovery endpoint, no discovery UI, no like/save/rating features. Fully scoped in SCRUM-71 but not yet implemented (To Do status in Jira).
- Blocks: Social features, trip sharing, discovery-based trip planning.
- Scheduled: Fall/winter work per `docs/TripFlow_fall_Break_Plan.md`, not a blocker for capstone.

**Trip cloning (SCRUM-71d):**
- Problem: No `POST /api/trips/{id}/clone` endpoint. Scoped in SCRUM-71d but not implemented (To Do).
- Blocks: Users cannot duplicate their own trips as templates.
- Scheduled: Fall/winter work, depends on SCRUM-71 (discovery visibility patterns) landing first.

**Geolocation-based trip tracking:**
- Problem: Manual "mark visited" is complete (`Stop.status`). Automatic arrival detection via GPS is 0% implemented. Requires frontend geolocation watch + backend arrival-detection logic.
- Blocks: "Automatic" trip tracking. Users must manually tap "visited" on each stop.
- Scheduled: Foreground-only MVP (FB-25, realistic without native build) is fall/winter work. Background push notifications (FB-26, requires native shell) deferred to winter.

## Test Coverage Gaps

**Social features (SCRUM-71/72) have no integration tests yet:**
- What's not tested: Discovery endpoint query logic, like/rating idempotency, clone deep-copy semantics, existence-hiding for private trips (404 vs 403).
- Files: Not applicable (features not yet implemented).
- Risk: Once SCRUM-71/72 are implemented, test-first development should cover the acceptance criteria. The audit (`docs/social-features-traceability-audit.md`, section 2) explicitly calls for IT coverage (query-count test, idempotency test, deep-copy invariant test).
- Priority: Medium — not blocking Aug 6 presentation, but required before merging social feature PRs.

**Frontend geolocation watch (FB-25, foreground arrival detection):**
- What's not tested: Geolocation permission handling, distance-to-stop calculation, prompt UX, update-stop integration.
- Files: Not applicable (feature not yet implemented).
- Risk: Geolocation APIs have permission/browser compatibility quirks. E2E or integration tests are essential.
- Priority: Medium — required before shipping FB-25.

**Offline caching / service worker behavior:**
- What's not tested: Offline-mode fallback behavior, cache invalidation, stale-while-revalidate patterns.
- Files: Not applicable (offline feature not yet implemented).
- Risk: PWA claims offline capability but currently doesn't cache API responses. If this is added, testing becomes critical (hard to debug service-worker issues).
- Priority: Low — deferred pending offline caching implementation.

---

*Concerns audit: 2026-08-06*
