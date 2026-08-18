# Codebase Concerns

**Analysis Date:** 2026-08-14

## Tech Debt

### Profile Misconfiguration Silent Failure

**Files:** `backend/src/main/resources/application.properties:3`

**Issue:** `spring.profiles.default=dev` means a prod deploy missing `SPRING_PROFILES_ACTIVE=prod` silently boots with dev security posture (SQL logging on, Swagger enabled, CORS to localhost only, debug logging with per-request userId in logs, and critically — remote IP header rewriting disabled so all callers collapse into one login rate-limit bucket).

**Impact:** A configuration typo or environment variable drop produces no error signal and degrades the deployment from correctly rate-limited to "one attacker locks everyone out for an hour." This is the failure mode that survives longest because there is no log line naming it.

**Fix approach:** Remove `spring.profiles.default=dev` and make the profile explicit everywhere. Add a startup guard (ApplicationRunner or @PostConstruct) throwing when prod indicators (DB_URL) are present but the `prod` profile is not active. Also add a `/actuator/health` custom indicator surfacing the active profile so wrong deploys are visible without reading logs.

### Unverified Production Assumption on Auth Rate Limiting

**Files:** `backend/src/main/resources/application-prod.properties:29,42`, `backend/src/main/java/com/tripflow/backend/controller/AuthController.java:36,44`, `docs/auth.md:46-51`

**Issue:** The auth rate limiter's correctness depends on `server.forward-headers-strategy=native` and `CF-Connecting-IP` header rewriting working correctly in production — an assumption **never empirically verified against a real deployment**. If the valve does not engage, every caller collapses into one shared bucket, and the `10 requests/hour` login limit becomes `10 requests/hour for the entire system`.

**Impact:** Silent global authentication outage repeatable indefinitely with zero attacker cost. The code fix (SCRUM-312, merged) is reasoned correctly, but the failure mode is indistinguishable from success without live verification. No log line records which mode is actually live.

**Fix approach:** Add one `log.debug` line in `AuthController` recording the resolved bucket key, then empirically verify once against production that it contains the actual client IP (not the proxy IP). This converts an assumption into a fact. Separately, add a much higher global ceiling as a distinct bucket so exhausting one does not silently mean exhausting the other.

### Visibility-Check Logic Duplicated Across Four Services

**Files:** `backend/src/main/java/com/tripflow/backend/service/TripService.java:101-113`, `TripCloneService.java:76-86`, `TripLikeService.java:60-70`, `StopPhotoService.java:141-151`

**Issue:** All four implement the identical rule — load trip, compare owner to requester, throw 404 for private trips when not owner. `TripOwnershipService` was extracted specifically to prevent this duplication ("REF-40"), but the owner-or-public variant was re-duplicated as new features landed.

**Impact:** An access-control rule with four independent implementations. They agree today, but a future change (adding `UNLISTED` visibility, enabling trip collaborators) must be applied in four places, and missing one is a silent authorization bug no test will catch.

**Fix approach:** Add `Trip loadVisibleTrip(Long tripId, Long requesterId)` to `TripOwnershipService` and have all four services call it. Keep both repository fetch variants (with/without stops) as separate methods on the shared bean — the fetch strategy is a legitimate per-caller choice, the rule is not.

### Prompt Template Substitution Mechanisms Diverge

**Files:** `backend/src/main/java/com/tripflow/backend/ai/ItineraryPromptTemplate.java:78-87`, `TripGenerationPromptTemplate.java:36`

**Issue:** `ItineraryPromptTemplate` uses regex-based substitution with a long javadoc explaining why chained `String.replace` is unsafe (a user value containing `{{budget}}` could be re-scanned). `TripGenerationPromptTemplate` then uses `String.replace`. This happens to be safe today (single pass, one placeholder), but a reader who absorbed the sibling's warning will read this as the exact bug the warning describes.

**Impact:** If a second placeholder is ever added to `generate-trip.txt`, it becomes a real bug. The divergence sits on different templates and is therefore error-prone to maintain.

**Fix approach:** Extract the regex `substitute` helper to a shared location or a small utility, and use it in both templates.

### Shared `mask()` Helper Duplicated Across Property Records

**Files:** `backend/src/main/java/com/tripflow/backend/security/JwtProperties.java`, `client/ors/OrsProperties.java`, `client/gemini/GeminiProperties.java`, `client/cloudinary/CloudinaryProperties.java`

**Issue:** Three properties records (`OrsProperties`, `GeminiProperties`, `CloudinaryProperties`) have identical `mask()` helper methods in their `toString()` overrides. `JwtProperties` does not override `toString()` at all, leaving the JWT signing key — the single highest-value secret — unmasked.

**Impact:** Any future log that renders `jwtProperties` writes the full signing secret to logs in plaintext. The team's convention (three times repeated elsewhere) clearly states secrets should be masked. This is not a missing rule; it is a forgotten application of an existing one.

**Fix approach:** Add masked `toString()` to `JwtProperties` using the same pattern. Extract one shared `mask()` helper to a package-private utility.

---

## Known Bugs

### Race Condition in Place Resolution Poison Entire Transaction

**Files:** `backend/src/main/java/com/tripflow/backend/service/PlaceResolutionService.java:112-130`

**Issue:** When two concurrent requests try to create the same place, the first `INSERT` violates the unique index and PostgreSQL aborts the entire transaction. The catch-block tries to recover with `findExistingPlace()`, but that call runs in the poisoned transaction — it will fail with "current transaction is aborted" rather than returning the row. Hibernate also marks the persistence context `rollbackOnly`, so commit fails with `UnexpectedRollbackException`. Either way, `GlobalExceptionHandler.handleGeneric` returns **500 Unexpected error**.

**Trigger:** Two users creating trips referencing the same Mapbox place simultaneously (entirely plausible for popular POIs, and near-certain for the AI-generate flow which resolves a batch of well-known landmarks at once).

**Workaround:** Retry the request; the second attempt will find the place already created.

**Fix approach:** The recovery must run on a fresh transaction. Two options: (a) `@Transactional(REQUIRES_NEW)` on an extracted `insertPlace()` method (requires self-injection via lazy proxy to hit Spring's AOP), or (b) use `INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING id` — the same atomic pattern `TripLikeRepository.insertIfAbsent` already uses successfully. (b) is smaller, more consistent, and removes the try/catch altogether.

### Password Registration Exceeding 72 Bytes Returns 500 Instead of 400

**Files:** `backend/src/main/java/com/tripflow/backend/dto/RegisterRequest.java:10`, `backend/src/main/java/com/tripflow/backend/service/AuthService.java:49`

**Issue:** Spring Boot 4.1 pulls Spring Security 7.x, which throws `IllegalArgumentException("password cannot be more than 72 bytes")` when BCrypt encodes an over-long input. `RegisterRequest` has `@Size(min=8)` but no `max`, so anything over 72 bytes is accepted at the API boundary. `AuthService.register` has no guard, and `IllegalArgumentException` has no dedicated exception handler, so it falls through to `handleGeneric` → **500 Internal Server Error** instead of **400 validation error**.

**Trigger:** Any user with a passphrase over 72 bytes (realistic for password-manager users or sentence-style passwords).

**Workaround:** Use a shorter password.

**Fix approach:** Add `@Size(min=8, max=72)` to `RegisterRequest.password` and add an integration test asserting 400 (not 500) for a 100-character password. **Do not** add `max=72` to `LoginRequest.password` — a login `matches()` call against an over-long candidate should fail as invalid credentials, not validate-error.

### JWT Expiry Check Uses `atob()` on base64url Token

**Files:** `frontend/src/app/core/services/auth.service.ts` (JWT decoding logic)

**Issue:** The frontend JWT expiry check decodes the token's payload using `atob()`, which fails on any token whose base64 payload happens to contain `-` or `_` characters (the base64url variants of `+` and `/`). The error is caught silently, treating a decode failure as an expired token and forcing logout. **Test suite can't see this because its fixtures use `btoa()` instead of real base64url encoding.**

**Trigger:** Rare by chance, but will happen to some users. Any JWT payload containing the decoded bytes `0xfb-0xff` or `0xfd-0xff` will include base64url special chars.

**Workaround:** Log in again; the token was not actually expired.

**Fix approach:** Use a proper base64url decoder instead of the vanilla `atob()`. Angular's `base64.decode()` or `atob()` with manual `->/+` and `_>///` conversion will work.

---

## Security Considerations

### Unindexed, Unescaped, Uncapped Discovery Search Is Denial of Service

**Files:** `backend/src/main/java/com/tripflow/backend/controller/DiscoveryController.java:31-45`, `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java:34,57-82`

**Risk:** `/api/discovery/search` is unauthenticated, unrated, unindexed, and passes user input directly into an `ILIKE '%...%'` pattern without escaping. A caller sends `q=%` (matches every row) or `q=%a%a%a%a%a%` (pathological backtracking) and forces a full table scan per request. The endpoint is the **only** publicly-reachable path with no rate limit, and it's the cheapest available DoS against the deployment — saturates the 5-connection pool and locks out all authenticated users.

**Current mitigation:** None.

**Recommendations:**
1. Escape `%`, `_`, and `\` before wrapping in pattern, and add `ESCAPE '\'` to both `ILIKE` clauses
2. Add `@Size(max=100)` on the `q` parameter (`@Validated` on controller)
3. Extend bucket4j coverage to `/api/discovery/**` using the existing `RateLimiterService` pattern
4. Longer term: add `pg_trgm` GIN index on `title`

### Cloudinary Upload Signature Constrains Only Folder, Not Size or Format

**Files:** `backend/src/main/java/com/tripflow/backend/service/StopPhotoService.java:48`, `backend/src/main/java/com/tripflow/backend/client/cloudinary/CloudinarySigningService.java:20-22,32-55`

**Risk:** The signed parameter is only `folder`. Nothing signs `allowed_formats`, `max_file_size`, `resource_type`, or `moderation`. An authenticated user with a valid signature can upload arbitrary files of arbitrary size (video, raw data, hundreds of megabytes) to the project's Cloudinary account.

**Current mitigation:** URL validation checks the prefix is under the configured cloud, but doesn't restrict delivery type.

**Recommendations:**
1. Sign the constraints: pass `allowed_formats=jpg,png,webp`, `resource_type=image`, and `max_file_size` to `sign(...)`
2. Remove `resource_type` from `UNSIGNED_KEYS` so it's part of the signed payload
3. Tighten `validateCloudinaryUrl` to reject `fetch` delivery URLs (whitelist `upload` instead)
4. Restrict "Allowed fetch domains" in the Cloudinary account console

### Cloudinary URL Allowlist Bypassable via Fetch Delivery

**Files:** `backend/src/main/java/com/tripflow/backend/service/StopPhotoService.java:106-113`

**Risk:** `validateCloudinaryUrl` requires the URL to start with `https://res.cloudinary.com/<cloudName>/`. Cloudinary's fetch delivery feature defeats this: a URL of the form `https://res.cloudinary.com/<cloudName>/image/fetch/https://attacker.example/beacon.png` passes the prefix check but serves attacker-controlled content. The attacker uploads this URL to a stop, sets the trip `PUBLIC`, and every viewer's browser fetches it — enabling tracking, content-swapping, and XSS if the attacker can modify the served content.

**Current mitigation:** The prefix check exists, but doesn't exclude fetch delivery.

**Recommendations:**
1. Parse the URL and reject if delivery type is `fetch` (whitelist `upload` instead)
2. Better: verify the `cloudinaryPublicId` against the folder it was issued for (`stops/<stopId>`)
3. Restrict fetch domains in Cloudinary account settings

### Rate-Limiter Buckets Grow Without Bound, Keyed on Attacker-Controlled Cardinality

**Files:** `backend/src/main/java/com/tripflow/backend/ratelimit/RateLimiterService.java:22,29-37`, `backend/src/main/java/com/tripflow/backend/controller/AuthController.java:36,44`

**Risk:** `ConcurrentHashMap<String, Bucket> buckets` is never evicted. Keys for `login` and `register` are derived from client IP (attacker-controlled). From a single IPv6 /64 an attacker has 2^64 distinct source addresses; each unique IP allocates a `Bucket` + `Bandwidth` + map entry forever. This is a slow but certain heap exhaustion / OOM.

**Current mitigation:** None.

**Recommendations:**
1. Replace raw `ConcurrentHashMap` with a size- and TTL-bounded cache (Caffeine with `maximumSize` and `expireAfterAccess`)
2. Set eviction window slightly above the longest configured window (max is 1 hour)
3. Expiring an idle bucket is safe: a fully-refilled bucket is indistinguishable from a fresh one

### Account Enumeration via Registration Conflict and Login Timing

**Files:** `backend/src/main/java/com/tripflow/backend/service/AuthService.java:39-44,74-78`, `backend/src/main/java/com/tripflow/backend/exception/GlobalExceptionHandler.java:54-58`, `frontend/src/app/core/services/auth.service.ts:57`

**Risk:** `POST /api/auth/register` returns `409` with the email address in the message when it's already taken — direct "is this address registered?" oracle. `POST /api/auth/login` calls `findByEmail` before `passwordEncoder.matches`, so the timing gap between non-existent (fast lookup) and existing (slow BCrypt) is measurable and consistent.

**Current mitigation:** Rate limits on both endpoints (`5/hour` and `10/hour` per IP) bound the enumeration rate.

**Recommendations:**
1. For login: always run `passwordEncoder.matches` against a fixed dummy hash when user lookup misses
2. For registration: stop echoing the submitted email in the message, keep the 409 but remove the oracle signal
3. Ensure the register bucket is genuinely per-IP (verify M-1's assumption)

---

## Performance Bottlenecks

### Discovery Search Executes Two Unindexed Full Scans Per Request

**Files:** `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java:34,57-82`

**Problem:** Both `matchingIds` and `countMatches` queries run `t.title ILIKE '%<q>%'` plus a correlated `EXISTS (SELECT 1 FROM unnest(t.tags) tag WHERE tag ILIKE :pattern)`. Leading-wildcard `ILIKE` cannot use a btree index, and no trigram/GIN index exists. Cost scales with total table size, not page size.

**Improvement path:** Add a `pg_trgm` GIN index on `lower(title)` and switch tag matching to a GIN index on `tags[]` array column.

### Orphan Place Cleanup Job Will Double-Run on Multi-Instance Deploy

**Files:** `backend/src/main/java/com/tripflow/backend/service/OrphanPlaceCleanupJob.java:27-34`

**Problem:** `@Scheduled` fires on every JVM. `deleteOrphans()` is an unguarded `DELETE ... WHERE NOT EXISTS` with no locking. Two instances scanning simultaneously can deadlock on row locks in unpredictable order.

**Improvement path:** Guard the job with `SELECT pg_try_advisory_lock(<constant>)` so only one instance runs. Narrow the delete to rows older than some interval to avoid racing live traffic.

### Over-Long Descriptions/Notes Accumulate in Response Payloads

**Files:** `backend/src/main/java/com/tripflow/backend/dto/CreateTripRequest.java:16`, `UpdateTripRequest.java:16`, `CreateStopRequest.java:15`, `UpdateStopRequest.java:17`, `UpsertStopRequest.java:35`, `CreateStopPhotoRequest.java:6-8`

**Problem:** `description`, `notes`, `url`, `caption` fields are unbounded. A single `POST /api/trips` with a 50 MB `description`, or 50 stops each with 10 MB `notes`, is accepted and persisted. `TripMapper.toResponse` materializes the whole thing in memory.

**Improvement path:** Add `@Size(max=...)` constraints: 5000 for `description`, 2000 for `notes`, 500 for `caption`, 2048 for `url`, 255 for `cloudinaryPublicId`. Set `server.tomcat.max-http-request-size` as a global backstop.

### ICS Export Uses Implicit Timezone Assumption

**Files:** `backend/src/main/java/com/tripflow/backend/service/IcsExportService.java:37,91-93`

**Problem:** `toDate()` uses `ZoneId.systemDefault()` to convert `LocalDateTime` to `Instant`. biweekly's floating-time writer reverses this using the same default zone, so the round trip is lossless only by coincidence. A DST boundary in the server's zone or a container TZ change breaks the invariant.

**Improvement path:** Use `ZoneOffset.UTC` instead of `systemDefault()` — UTC has no DST gaps, so the round trip is total. Add a test with a non-UTC timezone.

---

## Fragile Areas

### Route Optimization Dereferences ORS Response Internals Without Null Guards

**Files:** `backend/src/main/java/com/tripflow/backend/service/RouteOptimizationService.java:152,202-203,212-213`

**Why fragile:** `OrsClient` validates some internals (`routes()` non-empty, `features()` non-empty), but `steps()`, `properties()`, and `geometry()` are never checked and can be `null` on an unexpected response. A null `steps()` NPEs at line 152; a null `geometry()` silently passes `null` into JSON serialization.

**Safe modification:** Push validation down into `OrsClient` where it belongs. Extend the existing response checks to assert first route's `steps` is present and first feature's `properties`/`geometry` are present. Throw `OrsClientException` so the 502 pipeline engages.

### Gemini Quota Exhaustion Returns 502 Instead of 429

**Files:** `backend/src/main/java/com/tripflow/backend/client/gemini/GeminiClient.java:39-42`, contrast `backend/src/main/java/com/tripflow/backend/client/ors/OrsClient.java:77-87`

**Why fragile:** A 429 from Gemini returns **502** instead of **429 with Retry-After**, because there's no `execute()` helper like the ORS client has. Clients see a server fault (retry immediately) instead of rate limit (obey Retry-After). This is the exact failure mode SCRUM-221 fixed on ORS but was never ported to Gemini.

**Safe modification:** Mirror `OrsClient.execute` in `GeminiClient` — add `GeminiRateLimitException`, catch `HttpClientErrorException.TooManyRequests` first, and add an `@ExceptionHandler` in `GlobalExceptionHandler`.

### Visibility Checks Diverge Subtly Across Four Services

**Files:** `backend/src/main/java/com/tripflow/backend/service/TripLikeService.java:60-70` vs the three others

**Why fragile:** `TripLikeService.loadVisibleTrip` calls `findById` (cheaper, no stops) while the others call `findWithStopsById`. This difference is correct and intentional but is invisible to readers and could be "fixed" wrongly in either direction.

**Safe modification:** Add `boolean` overload or `findVisible` variant to `TripOwnershipService` so the fetch strategy choice is explicit and documented.

---

## Scaling Limits

### Rate-Limiter Buckets Unbounded by Map Size

**Files:** `backend/src/main/java/com/tripflow/backend/ratelimit/RateLimiterService.java:22`

**Current capacity:** Unbounded growth, limited only by available heap.

**Limit:** Each unique IPv6 address allocates one permanent `Bucket` entry. With 2^64 addresses per /64, an attacker can exhaust heap with negligible cost.

**Scaling path:** Replace with Caffeine cache with `maximumSize` bound (e.g., 100,000 buckets) and `expireAfterAccess` TTL.

### Discovery Search Scales with Total Public Trip Count

**Files:** `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java:57-82`

**Current capacity:** Full table scans are acceptable for hundreds of trips, slow for thousands, unusable for tens of thousands.

**Limit:** No trigram index, leading-wildcard `ILIKE` is unindexable without it.

**Scaling path:** Add `pg_trgm` GIN index on `title`.

### Text Field Sizes Unbounded at API Boundary

**Files:** `backend/src/main/java/com/tripflow/backend/dto/*.java` (multiple)

**Current capacity:** Single request with 50 MB `description` or 50 stops × 10 MB `notes` will consume gigabytes of heap + storage.

**Limit:** No request-size limit; Jackson materializes full body before validation.

**Scaling path:** Add `@Size(max=...)` constraints and `server.tomcat.max-http-request-size` global limit.

---

## Dependencies at Risk

### Spring Security 7.x BCrypt Rejection Is Not Backwards Compatible

**Files:** `backend/src/main/java/com/tripflow/backend/service/AuthService.java:49`, `backend/pom.xml:9` (Spring Boot 4.1.0)

**Risk:** Spring Boot 4.1 upgraded to Spring Security 7.x, which rejects passwords >72 bytes as `IllegalArgumentException` instead of silently truncating. This breaks any client sending a valid password that's over 72 bytes — a realistic scenario for password-manager users. The application has no guard, so it's a **500 Unexpected error**, not a 400 validation error.

**Migration plan:** Add `@Size(max=72)` to `RegisterRequest.password` and test with IT suite to confirm Testcontainers Postgres behavior. This is a low-risk change with immediate benefit (users over 72 bytes get a clear 400 instead of a 500).

### Node v23 Is End-of-Life

**Files:** `frontend/` (package.json engine constraint)

**Risk:** Local Node is v23.3.0 (EOL odd-numbered release). The toolchain rejects it (`EBADENGINE: ^20.19.0 || ^22.13.0 || >=24`). Subtle frontend tooling failures can result from version mismatch.

**Migration plan:** Upgrade to Node 22.13.0+ or 24.x LTS.

---

## Missing Critical Features

### No Logged Signal of Auth Rate Limiter Mode

**Files:** `backend/src/main/java/com/tripflow/backend/controller/AuthController.java:36,44`

**Problem:** The rate limiter's per-IP mode depends on `RemoteIpValve` engaging correctly in production — an assumption never verified empirically. There is no log line recording whether the resolved key is a real client IP or the proxy IP, so the mode (working vs. broken) is invisible.

**Blocks:** Runtime observability of the fix for SCRUM-312.

### No Cleanup Job for Orphaned Cloudinary Assets

**Files:** `backend/src/main/java/com/tripflow/backend/service/StopPhotoService.java:82-94`, `backend/src/main/java/com/tripflow/backend/domain/StopPhoto.java:31-32`

**Problem:** Deleting photos cascades the `stop_photos` row but never calls the Cloudinary destroy API. Orphaned assets accumulate permanently in a metered third-party service.

**Blocks:** Recovery path when photos are accidentally deleted; privacy issue for users deleting private trip photos.

---

## Test Coverage Gaps

### PlaceResolutionService Race Condition Not Covered by Unit Tests

**Files:** `backend/src/test/java/com/tripflow/backend/service/PlaceResolutionServiceTest.java`

**What's not tested:** The race-recovery path mocks the repository, so a `DataIntegrityViolationException` followed by successful re-read passes green. Real concurrent inserts on Postgres never replay this path because the transaction is poisoned.

**Risk:** The bug (C1) was invisible to unit tests and only caught during the full integration test audit.

**Priority:** High — add an IT test that forces concurrent inserts via Testcontainers Postgres.

### JWT Expiry Check Not Tested with Real base64url Payloads

**Files:** `frontend/src/app/core/services/auth.service.spec.ts`

**What's not tested:** Fixtures use `btoa()` (vanilla base64) instead of real base64url encoding. Any token whose payload bytes contain `0xfb-0xff` or `0xfd-0xff` will have the URL-safe `-` or `_` and will fail `atob()`.

**Risk:** Silent logout on certain valid tokens, unreproducible in test suite.

**Priority:** Medium — generate test fixtures with real base64url payloads, and add a test asserting decode succeeds.

### Discovery Search Not Tested with Wildcard Injection or Pathological Patterns

**Files:** `backend/src/test/java/com/tripflow/backend/repository/TripSearchRepositoryImplTest.java` (or similar)

**What's not tested:** Queries with `q=%` (matches all), `q=_` (pathological backtracking), or escape-sequence injection.

**Risk:** The vulnerability (H2 in backend audit) is invisible to normal query tests.

**Priority:** Medium — add IT tests with malicious patterns and assert they don't cause unintended matches or excessive query cost.

### Orphan Place Cleanup Not Tested in Multi-Instance Scenario

**Files:** `backend/src/test/java/com/tripflow/backend/service/OrphanPlaceCleanupJobTest.java` (or similar)

**What's not tested:** The job with concurrent executions on shared database.

**Risk:** Deadlock on multi-instance deploy is invisible to single-instance tests.

**Priority:** Low — difficult to test without real Postgres; issue is latent until deployment scales.

### HSTS and CF-Connecting-IP Header Behavior Not Verified in Prod

**Files:** `backend/src/main/resources/application-prod.properties`

**What's not tested:** Whether HSTS header is actually sent by the platform, whether `CF-Connecting-IP` is populated correctly by Cloudflare/Render edge.

**Risk:** Two unverified production assumptions noted in the audit (security.md M-1).

**Priority:** Medium — requires live prod environment access; should be confirmed by ops/devops during next deploy.

---

*Concerns audit: 2026-08-14*
