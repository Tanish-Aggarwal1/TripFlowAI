# Phase 6: Community & Social - Research

**Researched:** 2026-08-31
**Domain:** Social feed (feed UI + engagement + ranking), Spring Boot layered backend, Ionic/Angular standalone frontend
**Confidence:** MEDIUM-HIGH (backend patterns HIGH — read from source; frontend gesture-library choice MEDIUM — cross-checked official docs; ranking/UX specifics LOW-MEDIUM — genuinely new territory for this codebase)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** The "For You" feed is a TikTok/Reels-style **full-screen, one-trip-at-a-time vertical swipe** — not an Instagram-style scrolling list of post-cards. Swiping up/down moves between different trips (different "posts"); swiping left/right within a trip's card moves between that trip's stop images. — **Reversibility:** costly — this is a foundational frontend layout choice (full-screen takeover component vs. a scrollable list); switching later means rebuilding the feed component, not just restyling it.
- **D-02:** Card layout, fixed regardless of scroll/swipe position within a trip: trip name + major location at the top, owner username also at the top, description fixed at the bottom. The middle area is the horizontally-swipeable stop content (images, or text fallback — see D-04).
- **D-03:** Trips with zero stop photos fall back to a **text-based card** (stop name/description) in the swipeable middle area instead of breaking the layout or being excluded from the feed. Exact visual treatment of the text-card fallback is deferred — revisit during Phase 6 planning/UI design, not blocking.
- **D-04:** Like, save/bookmark, and clone are all available directly on the feed card via an **on-card action rail** (TikTok's side-rail pattern) — the user never has to leave the full-screen feed to like/save/clone a trip. — **Reversibility:** reversible — this is additive UI on top of the existing like/save/clone endpoints (FB-20/21/24); the endpoints themselves don't change based on where the button lives.
- **D-05:** The feed is not purely chronological — it applies **lightweight interest-based ranking**: PUBLIC trips whose tags match the viewer's stored profile interests are ordered first, with the remainder falling back to recency (or another simple, non-personalized order). This is explicitly a small ranking pass, not a recommendation-engine build. — **Reversibility:** reversible — ranking logic is a query-ordering concern; can be simplified back to pure chronological without touching the feed's data model or UI.
- **D-06:** The source of "interests" for ranking is the new **stored profile interests field** from D-07 (the user profile page), not an inferred signal from the viewer's own trip history. This was the user's explicit choice when asked to pick between the two.
- **D-07:** A minimal user profile page ships as part of **this phase**: username, join date, and stored interests. — **Reversibility:** one-way-ish for the schema piece — adding a `user_interests`-shaped field/table is cheap to add now; retrofitting it after ranking logic and feed UI already assume its absence would mean revisiting both.
- **D-08:** All of Phases 1-7 target the fall-break window; Phase 8 stays winter-only hardening. (Milestone-level sequencing, not phase-specific implementation guidance.)

### Claude's Discretion

- Exact text-card fallback visuals for no-photo trips (D-03) — resolve during planning/UI design.
- Exact interest-tag taxonomy (free text vs. fixed category list) for the profile interests field (D-07) — must be queryable for D-05's ranking match. **Research recommendation: free-text array (mirrors the existing `Trip.tags TEXT[]` column exactly — see Standard Stack/Architecture Patterns below), matched case-sensitively via Postgres array-overlap for simplicity; a fixed taxonomy is more UX-correct long-term but is new complexity this phase doesn't need.**
- Whether ranking (D-05) is computed at query time or precomputed/cached. **Research recommendation: query time** — capstone-scale data volume, and `TripRepository` already has precedent for native queries with custom `ORDER BY` (`decrementLikeCount`); no caching infrastructure exists in this codebase to precompute into.

### Deferred Ideas (OUT OF SCOPE)

None — this discussion stayed within an expanded-but-bounded Phase 6 scope; nothing was pushed to future phases. No reviewed/unfolded todos existed at discussion time.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SOCIAL-01 | Authenticated users browse a full-screen TikTok-style vertically-swipeable feed of PUBLIC trips (name/location/owner top, description bottom, swipeable stops middle, text-card fallback) | See "Critical Finding: discovery endpoints are currently unauthenticated" (Common Pitfalls) — `/api/discovery/**` must be pulled out of `SecurityConfig`'s permitAll list before this requirement can be satisfied as written. See Architecture Patterns (new `FeedTripResponse` DTO) and Code Examples (Swiper.js nested-swiper skeleton) for the UI/data shape. |
| SOCIAL-02 | Like/unlike idempotently from the on-card action rail; count from `trip_likes` join table | Backend fully done (`TripLikeService`, `TripController#likeTrip/unlikeTrip`, `V9__create_trip_likes.sql`) — see Code Examples for exact signatures to wire the frontend against. No backend task needed. |
| SOCIAL-03 | Clone a PUBLIC trip into a new PRIVATE trip from the action rail | Backend fully done (`TripCloneService`, `TripController#cloneTrip`) — see Code Examples. No backend task needed. |
| SOCIAL-04 | Save/bookmark a PUBLIC trip to a private list, idempotently, from the action rail | Net-new — see Architecture Patterns (mirror `TripLike`/`TripLikeId`/`TripLikeRepository`/`TripLikeService` exactly, new `trip_saves` table) and Don't Hand-Roll (idempotent insert pattern already solved once in this codebase, reuse it verbatim). Also needs a `GET` list endpoint — see Open Questions. |
| SOCIAL-05 | User profile page: username, join date, stored interests | Net-new — see Architecture Patterns (new `ProfileController`/`ProfileService`, `User.interests TEXT[]` mirroring `Trip.tags`, `V?__add_user_interests.sql`). Join date = existing `User.createdAt` (`BaseEntity`), no new field needed. |
| SOCIAL-06 | Feed orders PUBLIC trips with interest-tag matches first, recency fallback | See Architecture Patterns (Postgres array-overlap `&&` operator against `Trip.tags`/`User.interests`) and Code Examples for the native-query skeleton. |
</phase_requirements>

## Summary

This phase has an unusually clean split: three of six requirements (SOCIAL-01's data source, SOCIAL-02, SOCIAL-03) are backend-complete and confirmed still correct as of this research pass — `DiscoveryController`, `TripLikeService`, and `TripCloneService` all exist, are wired into `TripController`/`SecurityConfig`, and are documented accurately in `docs/api-contracts.md`. The remaining work is genuinely new: a save/bookmark join table (SOCIAL-04, a near-exact mirror of the already-shipped `trip_likes` pattern), a user-profile surface with a stored-interests array column (SOCIAL-05, best modeled as a Postgres `TEXT[]` exactly like `Trip.tags` already is), a lightweight ranking pass using Postgres array overlap (SOCIAL-06), and — the largest genuinely-new *frontend* piece — a TikTok-style nested vertical/horizontal swipe feed component, for which no swipeable primitive exists anywhere in this codebase today (`stop-photo-gallery` is a static, non-swipeable grid) and Ionic no longer ships one (`ion-slides` was removed; Ionic's own docs point to Swiper.js directly).

**The single most important finding for planning is a contradiction, not a gap:** `06-CONTEXT.md`'s Phase Boundary states the feed "requires authentication, it is not a public unauthenticated surface," and `SOCIAL-01` itself says "Authenticated users can browse" — but the shipped `SecurityConfig` currently has `/api/discovery/**` in its `permitAll` list, and `DiscoveryControllerIT` has passing tests asserting exactly that (`listPublicTrips_noAuth_returnsOnlyPublicTrips`, 200 with no `Authorization` header). Building SOCIAL-01 against the existing discovery endpoints as-is would ship a feed that violates its own requirement. The plan must include removing `/api/discovery/**` from the permitAll list (or introducing a new authenticated feed endpoint and leaving the old ones for a separate, unauthenticated use if one is ever wanted) — this is a `SecurityConfig` change, which the project's own carried-forward risk register (RISK-R2) flags as needing a Postman/browser regression pass, not just a unit-test change.

Second: the existing discovery endpoints return `TripSummaryResponse` — a card-projection DTO deliberately built for a paginated *list*, with no `stops`, no owner info, and no tags. The 06-CONTEXT.md "Established Patterns" note already anticipates this ("adapted for 'one full trip payload per feed item' rather than a summary list") — the planner needs a new feed-shaped DTO, not a reuse of `TripSummaryResponse` as-is.

**Primary recommendation:** Treat this phase as three independent backend slices (save/bookmark, ratings — not actually in the SOCIAL-01..06 set but referenced by CONTEXT's canonical docs, see Open Questions — and profile/interests) that all mirror the existing `trip_likes` join-table pattern almost verbatim, plus one new `FeedTripResponse`-shaped, authenticated, rank-aware discovery query; and one substantial new frontend surface (Swiper.js-based nested feed component) with no existing analog to build from.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Feed vertical/horizontal swipe UI | Browser / Client | — | Pure client-side gesture/rendering concern (Swiper.js); no server involvement beyond data fetch |
| Feed authentication gate | API / Backend | Browser (route guard) | `SecurityConfig` must require a JWT for `/api/discovery/**`; `authGuard` on the Angular route is defense-in-depth, not the actual control |
| Feed data shape (full trip + stops + photos per card) | API / Backend | — | New `FeedTripResponse` DTO + repository query; client only renders what the server sends |
| Like / Save / Clone actions | API / Backend | Browser (action-rail buttons) | Mutation + idempotency + ownership-visibility check belongs server-side (`TripOwnershipService`); client is a thin `HttpClient` call + optimistic UI |
| Interest-based ranking | API / Backend | — | Query-ordering concern (`ORDER BY` clause), explicitly not a recommendation engine per D-05; lives entirely in the repository query |
| Profile page (username/join date/interests) | API / Backend | Browser (page component) | New `ProfileController`/`ProfileService` for read/write; page is a straightforward form + display |
| Trip ratings | API / Backend | — | New join-table + aggregate query, same shape as likes |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `swiper` | 14.2.0 (npm, confirmed current — see Package Legitimacy Audit) | Nested vertical (trip-to-trip) + horizontal (stop-to-stop) swipe feed | Ionic's own docs deprecated and removed `ion-slides`, pointing developers to Swiper's official web-component/Angular integration directly [CITED: ionicframework.com/docs/angular/slides]. No alternative gesture-carousel dependency exists in this codebase; `@ionic/angular`'s `createGesture` API is a lower-level primitive for building *custom* one-off gestures, not a drop-in nested-carousel solution, and would mean hand-rolling exactly the paginated-snap/momentum/nested-scroll-conflict logic Swiper already solves. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (none new on the backend) | — | All backend work reuses already-installed Spring Data JPA / Hibernate / Postgres — no new backend dependency is needed for save/rate/profile/ranking. | — |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Swiper.js | `@ionic/angular`'s `createGesture` + manual CSS transforms | More control, zero new dependency, but reimplements snap-scrolling, momentum, nested vertical/horizontal gesture disambiguation, and pagination from scratch — exactly the kind of hand-rolled carousel logic the "Don't Hand-Roll" section below warns against. Not recommended given D-01 is explicitly flagged "costly to reverse." |
| Postgres array-overlap ranking | A `user_interests` × `trip_tags` join table with per-row scoring | More flexible (weighted matches, multiple tags counted), but D-05 explicitly says "not a recommendation-engine build" — a boolean overlap check in the `ORDER BY` is the smaller, D-05-appropriate solution. |
| Free-text interests array | A fixed enum/lookup-table taxonomy | More correct long-term (typo-proof matching, canonical tag list), but is new schema/UX surface this phase doesn't need — `Trip.tags` already ships as unconstrained free text and nothing in this phase's scope requires interests to be more disciplined than tags already are. |

**Installation:**
```bash
cd frontend
npm install swiper
```

**Version verification:** `npm view swiper version` returned `14.2.0` [VERIFIED: npm registry], most recently published 2026-08-26 (5 days before this research), 4,345,613 weekly downloads, source repo `github.com/nolimits4web/Swiper` [VERIFIED: npm registry]. The publish-date recency reflects Swiper's fast release cadence (frequent point releases), not a "new/unproven package" — see Package Legitimacy Audit for why this still surfaced a `SUS` verdict from the automated check.

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `swiper` | npm | latest version published 2026-08-26 (5 days old); package itself has shipped since ~2014 | 4,345,613/wk | github.com/nolimits4web/Swiper | **SUS** (reason: `too-new`, driven purely by the most recent version's publish timestamp, not package-creation age) | Flagged — planner must add a `checkpoint:human-verify` task before `npm install swiper`, per protocol, even though the underlying signal (multi-million weekly downloads, active canonical repo, no postinstall script, not deprecated) reads as a legitimate, actively-maintained library rather than a slopsquat risk. |

**Packages removed due to `[SLOP]` verdict:** none.
**Packages flagged as suspicious `[SUS]`:** `swiper` — flagged only because the legitimacy check's "too-new" heuristic keys off the latest published version's timestamp, which for a library with a fast release cadence will almost always look "new" regardless of the package's actual multi-year history. The planner should still insert the `checkpoint:human-verify` task per protocol, but the human check here is a quick sanity read (confirm it's still the same `nolimits4web/Swiper` GitHub org, matches the version pinned above) rather than a real suspicion of hallucination/typosquat.

## Architecture Patterns

### System Architecture Diagram

```
                       ┌─────────────────────────────┐
                       │  Angular Feed Page           │
                       │  (outer vertical Swiper)     │
                       └──────────────┬───────────────┘
                                      │ GET /api/discovery/feed (NEW, authenticated)
                                      │ ?page=&size=
                                      ▼
                       ┌─────────────────────────────┐
                       │ DiscoveryController          │
                       │ (auth required — SecurityConfig
                       │  permitAll entry REMOVED)    │
                       └──────────────┬───────────────┘
                                      ▼
                       ┌─────────────────────────────┐
                       │ TripService.listFeed(userId) │
                       │  - loads viewer's interests   │
                       │  - queries PUBLIC trips        │
                       │  - orders: tag-overlap DESC,   │
                       │    createdAt DESC              │
                       └──────────────┬───────────────┘
                                      ▼
                 ┌────────────────────────────────────────┐
                 │ TripRepository (native query)            │
                 │  Trip ⋈ User (owner username)             │
                 │  Trip.tags && :viewerInterests (Postgres) │
                 └──────────────┬─────────────────────────┘
                                │  + batched StopPhoto fetch
                                │    (findByStopIdIn, ONE query
                                │     for the whole page — avoid N+1)
                                ▼
                 ┌────────────────────────────────────────┐
                 │  FeedTripResponse[] (NEW DTO)             │
                 │  id, title, description, tags,            │
                 │  ownerUsername, likeCount, stops[]         │
                 │  (each stop: name, photos[], notes)        │
                 └──────────────┬─────────────────────────┘
                                ▼
        ┌───────────────────────────────────────────────────────┐
        │  Feed card component (per trip — inner horizontal      │
        │  Swiper over stop photos / text-fallback card)          │
        │  ┌────────────┐ ┌────────────┐ ┌────────────┐          │
        │  │ Action rail│ │ Action rail│ │ Action rail│  (like/   │
        │  │ POST/DELETE│ │ POST       │ │ POST       │   save/   │
        │  │ /like      │ │ /save (NEW)│ │ /clone     │   clone)  │
        │  └────────────┘ └────────────┘ └────────────┘          │
        └───────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
backend/src/main/java/com/tripflow/backend/
├── controller/
│   ├── DiscoveryController.java      # extend: new authenticated /feed endpoint
│   ├── TripController.java           # extend: POST/DELETE /{id}/save, POST /{id}/rate
│   └── ProfileController.java        # NEW — GET/PATCH profile + interests
├── service/
│   ├── TripSaveService.java          # NEW — mirrors TripLikeService exactly
│   ├── TripRatingService.java        # NEW — mirrors TripLikeService, upsert not delete
│   └── ProfileService.java           # NEW
├── domain/
│   ├── SavedTrip.java / SavedTripId.java   # NEW — mirrors TripLike/TripLikeId
│   └── TripRating.java / TripRatingId.java # NEW
├── repository/
│   ├── SavedTripRepository.java      # NEW
│   ├── TripRatingRepository.java     # NEW
│   └── StopPhotoRepository.java      # extend: findByStopIdIn(List<Long>) for batch fetch
├── dto/
│   ├── FeedTripResponse.java         # NEW — full-screen feed card shape
│   └── ProfileResponse.java          # NEW
└── db/migration/
    ├── V14__create_trip_saves.sql
    ├── V15__create_trip_ratings.sql
    └── V16__add_user_interests.sql

frontend/src/app/
├── pages/
│   ├── feed/                         # NEW — full-screen feed page (outer Swiper)
│   │   └── components/
│   │       ├── feed-card/            # NEW — per-trip card (inner Swiper + action rail)
│   │       └── feed-action-rail/     # NEW — like/save/clone buttons
│   └── profile/                      # NEW — profile page
├── core/services/
│   ├── discovery.service.ts          # NEW — calls new authenticated /feed endpoint
│   ├── trip-save.service.ts          # NEW
│   └── profile.service.ts            # NEW
```

### Pattern 1: Idempotent join-table toggle (mirror for Save)

**What:** A composite-PK join entity + a native `INSERT ... ON CONFLICT DO NOTHING` / JPQL bulk `DELETE`, so like/unlike-style toggles are a single atomic statement, never a Java read-modify-write.
**When to use:** SOCIAL-04 (save/bookmark) — this is a byte-for-byte structural mirror of the already-shipped `trip_likes` feature; no new pattern needs to be invented.
**Example (verbatim from the shipped `TripLikeRepository`, source read this session):**
```java
// Source: backend/src/main/java/com/tripflow/backend/repository/TripLikeRepository.java:21-27
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(value = """
        INSERT INTO trip_likes (user_id, trip_id, created_at)
        VALUES (:userId, :tripId, NOW())
        ON CONFLICT (user_id, trip_id) DO NOTHING
        """, nativeQuery = true)
int insertIfAbsent(@Param("userId") Long userId, @Param("tripId") Long tripId);
```
For `trip_saves`, this becomes a `SavedTripRepository.insertIfAbsent` with `trip_saves` substituted for `trip_likes` — no other change needed. `SavedTrip`/`SavedTripId` mirror `TripLike`/`TripLikeId` (`backend/src/main/java/com/tripflow/backend/domain/TripLike.java:1-59`, `TripLikeId.java:1-50`) with the same composite-PK-via-`@EmbeddedId`/`@MapsId` shape. Unlike likes, **saves have no reason to denormalize a count column** on `Trip` (nothing in D-04/SOCIAL-04 displays a save count) — skip the `saved_count` column `trip_likes` has for `like_count`; add it only if a later requirement needs to display it.

### Pattern 2: Owner-or-public visibility check (reuse, don't reimplement)

**What:** `TripOwnershipService.loadVisibleTripLite(tripId, requesterId)` — throws `ResourceNotFoundException` (404, not 403) if the trip doesn't exist or is PRIVATE and the requester isn't the owner.
**When to use:** Every new mutation this phase adds (save, rate) that touches someone else's trip. This is also how SCRUM-274 (404-vs-403 existence-hiding standardization, flagged in RISKS.md as needing resolution "as part of this phase's like/clone/rate work") is already resolved for like/clone — the convention exists and is documented (`docs/api-contracts.md` "Trip Cloning & Likes" section, `TripOwnershipService.java:63-73` javadoc). Applying the same call in the new `TripSaveService`/`TripRatingService` *is* the SCRUM-274 resolution for these two new endpoints — there is no separate design decision left to make.
**Example:**
```java
// Source: backend/src/main/java/com/tripflow/backend/service/TripLikeService.java:31-44
@Transactional
public void likeTrip(Long tripId, Long requesterId) {
    tripOwnershipService.loadVisibleTripLite(tripId, requesterId);
    int inserted = tripLikeRepository.insertIfAbsent(requesterId, tripId);
    if (inserted > 0) {
        tripRepository.incrementLikeCount(tripId);
        log.info("Trip liked tripId={} userId={}", tripId, requesterId);
    }
}
```
`TripSaveService.saveTrip`/`TripRatingService.rateTrip` follow this exact shape.

### Pattern 3: Postgres array column for free-text tags (reuse for interests)

**What:** `Trip.tags` is already a Postgres `TEXT[]` column via Hibernate's `@JdbcTypeCode(SqlTypes.ARRAY)`.
**When to use:** SOCIAL-05's stored interests field — same shape, different owning entity (`User` instead of `Trip`).
**Example (verbatim from the shipped `Trip` entity, source read this session):**
```java
// Source: backend/src/main/java/com/tripflow/backend/domain/Trip.java:50-52
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(columnDefinition = "TEXT[]")
private List<String> tags = new ArrayList<>();
```
Add the identical field/annotation pair to `User` as `interests`, with a matching migration:
```sql
-- V16__add_user_interests.sql — mirrors Trip.tags exactly
ALTER TABLE users ADD COLUMN interests TEXT[] NOT NULL DEFAULT '{}';
```
Reuse the same field-limit convention already documented for `Trip.tags` in `docs/api-contracts.md` ("Field limits" table: max 20 elements, each max 50 chars) for `User.interests` — no new limits policy needs inventing.

### Pattern 4: Postgres array-overlap ranking (new, but small)

**What:** Postgres's `&&` operator returns true if two arrays share at least one element — exactly D-05's "tags match the viewer's interests" check, expressible as a single boolean in an `ORDER BY`.
**When to use:** SOCIAL-06.
**Example (new — no existing precedent in this codebase for `&&`, but the native-query mechanism itself is already used, e.g. `TripRepository.decrementLikeCount`, `backend/src/main/java/com/tripflow/backend/repository/TripRepository.java:91-94`):**
```sql
-- Sketch for a new native query backing the authenticated feed endpoint.
-- :interests is the viewer's User.interests array (empty array falls through to
-- pure recency automatically — an empty array never overlaps with anything).
SELECT t.*, u.username AS owner_username
FROM trips t
JOIN users u ON u.id = t.user_id
WHERE t.visibility = 'PUBLIC'
ORDER BY (t.tags && CAST(:interests AS text[])) DESC, t.created_at DESC
```
This is deliberately the entire ranking algorithm — no scoring, no weighting, matching D-05's explicit "not a recommendation-engine build."

### Pattern 5: Batch-fetch child rows across a whole page (avoid N+1)

**What:** `StopPhotoRepository` currently only exposes `findByStopIdOrderByCreatedAtAsc(Long stopId)` — one stop at a time (`backend/src/main/java/com/tripflow/backend/repository/StopPhotoRepository.java:11`). A feed page renders ~20 trips, each with several stops; calling the existing per-stop method once per stop would be an N+1 query storm across a single feed page load.
**When to use:** Building `FeedTripResponse`'s `stops[].photos[]`.
**Example (new method to add):**
```java
// Add to StopPhotoRepository
List<StopPhoto> findByStopIdIn(List<Long> stopIds);
```
Fetch all stop IDs for the page's trips once, call this once, then group the results by `stopId` in the mapper (`Collectors.groupingBy`) before building each `FeedTripResponse`.

### Anti-Patterns to Avoid

- **Reusing `TripSummaryResponse` for the feed as-is:** it deliberately has no `stops`, no owner info, and no `tags` (it was built for the card-projection `GET /api/trips` list, see `TripSummaryResponse.java:9-14` javadoc). D-01/D-02 need the full per-stop content and owner username on every card. Build `FeedTripResponse` instead of stretching this DTO.
- **Building the horizontal stop-swipe and vertical trip-swipe as two independent Swiper instances without `nested: true`/scroll-conflict handling:** a naive nested-Swiper setup will fight itself on diagonal swipes (the classic complaint in Swiper's own GitHub discussions on TikTok-style feeds). Configure the inner (horizontal, per-stop) Swiper with `nested: true` and disable vertical touch-move propagation into the outer Swiper.
- **Hand-writing the like/save toggle as a `findById` + conditional `save()`/`delete()` in a service method:** this reintroduces exactly the race condition `TripLikeRepository`'s `ON CONFLICT DO NOTHING` was built to eliminate at the database layer. Copy the native-query pattern, don't reinvent the ownership-check-then-mutate flow in Java.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Nested vertical/horizontal swipe-to-page carousel with snap/momentum | Custom `HammerJS`/touch-event/CSS-transform carousel | Swiper.js (`nested: true` inner instance) | Snap-scroll physics, momentum, and nested-gesture disambiguation are exactly what Swiper exists to solve; Ionic's own docs deprecated `ion-slides` in favor of it rather than recommending a raw `createGesture` reimplementation for this use case. |
| Idempotent like/save toggle under concurrency | A Java `existsById` check followed by conditional `save()`/`delete()` | `INSERT ... ON CONFLICT DO NOTHING` / bulk `DELETE` native queries (already the shipped `TripLikeRepository` pattern) | Two concurrent requests from the same user racing a read-then-write toggle is a classic double-insert/lost-update bug; the database-level `ON CONFLICT` clause makes the race resolve atomically instead. |
| Existence-hiding on a private resource | A `ForbiddenException` (403) for a PRIVATE trip a stranger tries to like/save/rate | `TripOwnershipService.loadVisibleTripLite` → `ResourceNotFoundException` (404) | A 403 confirms the resource id exists to someone not allowed to know that — this is the exact SCRUM-274 concern; the codebase has already made and centralized this decision, applying it to two more call sites is not a new design choice. |

**Key insight:** every genuinely-new backend piece in this phase (save, rate, interests) is a structural copy of a pattern the codebase has already built once for likes/tags — the risk here is *not* under-researching a novel algorithm, it's accidentally diverging from an established convention (e.g. building save as a `boolean` column with read-modify-write instead of a join table) and reintroducing a bug class this codebase already fixed.

## Common Pitfalls

### Pitfall 1: Building SOCIAL-01 against endpoints that are currently unauthenticated
**What goes wrong:** `SecurityConfig.java:61-62` `permitAll()`s `/api/discovery/**`, and `DiscoveryControllerIT` has a passing test (`listPublicTrips_noAuth_returnsOnlyPublicTrips`) asserting 200 with no `Authorization` header [VERIFIED: backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java:61-62, backend/src/test/java/com/tripflow/backend/controller/DiscoveryControllerIT.java:122]. `06-CONTEXT.md`'s Phase Boundary and SOCIAL-01 itself both require the feed to be authenticated-only.
**Why it happens:** the existing discovery endpoints were built (SCRUM-160/163) for a genuinely different purpose — anonymous public browsing — before this phase's discussion added the authentication requirement.
**How to avoid:** remove `/api/discovery/**` from the `permitAll` matcher list (or add a distinct, newly-authenticated `/api/discovery/feed` path and leave the old two endpoints as they are for whatever future use case wanted anonymous browsing). Either way this is a `SecurityConfig` edit — RISK-R2 in the project's own risk register explicitly calls for a Postman/browser regression pass after any `SecurityConfig` change, not just updated unit tests.
**Warning signs:** if the plan's tasks only add a controller method and never touch `SecurityConfig`, this gap will ship silently — `DiscoveryControllerIT`'s existing tests will keep passing (they test the *old*, still-permitAll endpoints) while the *new* endpoint quietly inherits `anyRequest().authenticated()` by default, or worse, a copy-pasted mapping under `/api/discovery/**` inherits the permitAll unintentionally.

### Pitfall 2: N+1 stop-photo fetch across a feed page
**What goes wrong:** building each `FeedTripResponse`'s nested stop-photo list by calling `StopPhotoRepository.findByStopIdOrderByCreatedAtAsc` once per stop, across every trip on a 20-item feed page, produces dozens of extra queries per page load.
**Why it happens:** that method already exists and is the obvious thing to reach for (it's what `StopPhotoService.listPhotos` uses for a single stop's detail view) — but a feed page needs the same data shape multiplied across many trips at once.
**How to avoid:** add `findByStopIdIn(List<Long> stopIds)`, collect every stop id across the whole page first, fetch once, group in memory.
**Warning signs:** feed page load time scaling linearly with `stops-per-trip × trips-per-page` instead of staying flat; visible query-count spikes in test logs (this codebase already treats query-count regressions as testable — see `TripSearchRepositoryIT`'s existing pattern).

### Pitfall 3: Re-rating a trip creates a duplicate row instead of updating
**What goes wrong:** a naive `TripRatingRepository.save(new TripRating(...))` per rating submission either violates the composite PK (if the entity's `@Id` correctly maps to `(user_id, trip_id)`) or silently double-counts in an average if the PK isn't enforced.
**Why it happens:** likes/saves are boolean toggles (present/absent), but a rating is a *value* (1-5) that a user can change — this needs upsert semantics, not insert-or-noop.
**How to avoid:** `INSERT ... ON CONFLICT (user_id, trip_id) DO UPDATE SET rating = excluded.rating, updated_at = NOW()`, mirroring `TripLikeRepository.insertIfAbsent`'s native-query mechanism but with `DO UPDATE` instead of `DO NOTHING`.
**Warning signs:** `averageRating` computed via aggregate query drifting upward/downward on repeated re-rating by the same small set of users in manual testing — a strong signal duplicate rows are accumulating.

### Pitfall 4: Nested Swiper gesture conflicts (diagonal swipe ambiguity)
**What goes wrong:** without explicit configuration, a diagonal touch gesture on the feed can trigger both the outer vertical (trip-to-trip) and inner horizontal (stop-to-stop) Swiper simultaneously, or the wrong one, producing a feed that feels broken on real touch devices even though it works fine with a mouse in dev tools.
**Why it happens:** this is a known, documented class of issue in Swiper's own community discussions for exactly this TikTok-style nested-feed pattern [ASSUMED — community forum/GitHub discussion consensus, not an official Swiper doc page dedicated to this exact configuration].
**How to avoid:** set `nested: true` on the inner (horizontal) Swiper instance, test specifically on a touch device or touch-emulation mode (not just mouse-drag in desktop dev tools) before considering D-01 done.
**Warning signs:** works fine with mouse-drag testing in a desktop browser, breaks on an actual phone/tablet — always verify D-01 on a real touch device or Chrome DevTools' touch-emulation mode, not mouse-only.

## Code Examples

### Existing like endpoint — exact shape the frontend action rail must call
```java
// Source: backend/src/main/java/com/tripflow/backend/controller/TripController.java:148-163
@PostMapping("/{id}/like")
public ResponseEntity<Void> likeTrip(
        @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
    tripLikeService.likeTrip(id, principal.userId());
    return ResponseEntity.ok().build();
}

@DeleteMapping("/{id}/like")
public ResponseEntity<Void> unlikeTrip(
        @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
    tripLikeService.unlikeTrip(id, principal.userId());
    return ResponseEntity.ok().build();
}
```
Frontend: `POST /api/trips/{id}/like` and `DELETE /api/trips/{id}/like`, Bearer token required, 200 empty body on success, 404 if the trip doesn't exist or is someone else's PRIVATE trip [VERIFIED: docs/api-contracts.md, "Trip Cloning & Likes" section]. Add `likeTrip(id)`/`unlikeTrip(id)` methods to `trip.service.ts` following the exact `catchError(mapApiError(...))` pattern every other method in that file already uses (`frontend/src/app/core/services/trip.service.ts:67-92`).

### Existing clone endpoint — exact shape for the action rail's clone button
```java
// Source: backend/src/main/java/com/tripflow/backend/controller/TripController.java:139-146
@PostMapping("/{id}/clone")
public ResponseEntity<TripResponse> cloneTrip(
        @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
    rateLimiterService.checkLimit("trip-clone:" + principal.userId(), rateLimitProperties.tripClone());
    return new ResponseEntity<>(tripCloneService.cloneTrip(id, principal.userId()), HttpStatus.CREATED);
}
```
Returns `201` with the full new `TripResponse` (visibility always `PRIVATE`, title `"Copy of {original title}"`) [VERIFIED: backend/src/main/java/com/tripflow/backend/service/TripCloneService.java:44-46]. Frontend should navigate to the new trip's edit page on success, using the returned `id`.

### Swiper.js nested vertical/horizontal skeleton (Angular, web-component API — Swiper v9+ shape)
```typescript
// Source: pattern synthesized from Swiper's official Angular integration docs
// (swiperjs.com/angular) and Ionic's migration guide (ionicframework.com/docs/angular/slides)
// [CITED — no single official doc shows this exact nested TikTok-feed configuration;
// the nested/vertical-plus-horizontal combination itself is community-documented,
// see Pitfall 4 above].
import { register } from 'swiper/element/bundle';
register(); // registers <swiper-container>/<swiper-slide> as custom elements

// Outer (vertical, trip-to-trip) — feed.page.ts template:
// <swiper-container direction="vertical" slides-per-view="1" mousewheel="true">
//   @for (trip of trips(); track trip.id) {
//     <swiper-slide>
//       <app-feed-card [trip]="trip" />
//     </swiper-slide>
//   }
// </swiper-container>

// Inner (horizontal, stop-to-stop) — feed-card.component.ts template:
// <swiper-container direction="horizontal" slides-per-view="1" nested="true">
//   @for (stop of trip.stops; track stop.id) {
//     <swiper-slide>
//       @if (stop.photos.length) {
//         <img [src]="stop.photos[0].url" />
//       } @else {
//         <div class="text-fallback-card">{{ stop.name }} — {{ stop.notes }}</div>
//       }
//     </swiper-slide>
//   }
// </swiper-container>
```
`CUSTOM_ELEMENTS_SCHEMA` must be added to any standalone component template using `<swiper-container>`/`<swiper-slide>`, since these are native web components, not Angular components — this is a real, easy-to-miss compile/runtime gotcha with the current Swiper Angular integration approach.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `ion-slides`/`ion-slide` (Ionic-bundled carousel) | Swiper.js directly, via its own Angular/web-component integration | Ionic Framework v6+ (deprecated), fully removed in later majors | This codebase (`@ionic/angular ^9.0.0`) has no `ion-slides` available at all — Swiper must be added as a direct dependency; there is no "just use the built-in one" option anymore. |

**Deprecated/outdated:**
- `ion-slides`/`ion-slide`: removed from Ionic; do not reference in any plan or code — it will not exist in `node_modules` and would fail to compile.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Free-text `TEXT[]` is the right taxonomy shape for `User.interests` (vs. a fixed enum/lookup table) | Standard Stack / Claude's Discretion | Low — this was explicitly left to research/planning discretion in CONTEXT.md, and the recommendation mirrors an already-shipped pattern (`Trip.tags`) rather than inventing something new; worst case is a later migration to a stricter taxonomy. |
| A2 | Nested-Swiper gesture-conflict issue and its `nested: true` mitigation | Common Pitfalls (Pitfall 4), Code Examples | Medium — this is community-forum-sourced, not an official Swiper doc page for this exact configuration. If wrong, the fix is a Swiper config-option adjustment, not an architecture change — low blast radius but should be verified against a real touch device early in implementation, not assumed correct from this research alone. |
| A3 | A new `ProfileController`/`ProfileService` (rather than extending `AuthController`/`UserService`) is the better fit | Architecture Patterns / Recommended Project Structure | Low — CONTEXT.md itself left this as an open discretion call ("likely extends AuthController/UserService or gets its own ProfileController/ProfileService"); either choice is a same-day refactor away from the other, no schema impact either way. |
| A4 | Skipping a denormalized `saved_count` column on `Trip` for SOCIAL-04 (unlike `like_count`) | Architecture Patterns (Pattern 1) | Low — nothing in the locked decisions or requirements displays a save count anywhere; if a later requirement needs one, it's an additive migration, not a rework. |

**If this table is empty:** N/A — see rows above; none of these are compliance/security/retention-policy claims, all are UX/architecture judgment calls explicitly flagged as discretionary in CONTEXT.md or low-blast-radius implementation details.

## Open Questions

1. **Does SOCIAL-04 (save/bookmark) need a `GET` list endpoint this phase, or is idempotent save/unsave alone sufficient?**
   - What we know: SOCIAL-04's own text says trips get saved "to a private 'saved trips' list" — implying somewhere a user can *view* that list, not just toggle membership in it.
   - What's unclear: no such list-view page exists in `frontend/src/app/pages`, and ROADMAP.md's 06-03 plan description ("On-card action rail (like/save/clone)") only mentions the action-rail button, not a saved-trips list page.
   - Recommendation: plan a `GET /api/trips/saved` (paginated, `TripOwnerSummaryResponse`-shaped, owner-only) alongside the save/unsave endpoints even if the list-view *page* itself is deferred — the backend endpoint is cheap to add now and expensive to retrofit once the join table's access pattern is already fixed by the save/unsave-only design.

2. **Trip ratings appear in `docs/social-features-traceability-audit.md` (item C / FB-19d) and are referenced by `06-CONTEXT.md`'s canonical-refs list, but there is no `SOCIAL-0N` requirement ID for ratings in `REQUIREMENTS.md`'s current SOCIAL section (only SOCIAL-01 through SOCIAL-06, none of which mention rating).**
   - What we know: the ROADMAP.md phase-plan breakdown supplied to this research (plan 06-04: "Trip ratings (star, trip-level, join-table pattern) — fully net-new") explicitly includes it as one of the 6 plans this phase must support.
   - What's unclear: whether ratings map to an *existing* SOCIAL requirement (the closest is none of SOCIAL-01..06 as re-read from `REQUIREMENTS.md` this session) or whether `REQUIREMENTS.md` itself is stale and missing a ratings line item.
   - Recommendation: flag this to the user/planner explicitly — either `REQUIREMENTS.md` needs a `SOCIAL-07` (or similar) added before Phase 6 is considered fully traced, or ratings should be confirmed as in-scope-but-untracked and the requirements doc updated as part of this phase's own documentation hygiene (matching the project's own established habit of correcting `REQUIREMENTS.md`/`docs/api-contracts.md` drift, e.g. the 2026-08-14 HARDEN phase-mapping fix).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL (array-overlap `&&` operator) | SOCIAL-06 ranking, SOCIAL-05 interests column, SOCIAL-04 saves table | ✓ (already the project's database; array columns already used by `Trip.tags`) | — | none needed |
| `swiper` (npm) | SOCIAL-01 feed UI | ✗ (not yet installed — `frontend/package.json` has no carousel/gesture dependency today) [VERIFIED: frontend/package.json] | install `14.2.0` | none — this is the recommended primary approach, not one option among several; see Standard Stack |
| Ionic `createGesture` (`@ionic/angular`, already installed) | Fallback only, if Swiper proves unworkable | ✓ | bundled with `@ionic/angular ^9.0.0` | n/a — listed as the hand-rolled fallback, not recommended (see Don't Hand-Roll) |

**Missing dependencies with no fallback:** none — `swiper` is a straightforward `npm install`, no environment/tooling gap.
**Missing dependencies with fallback:** none beyond the Swiper-vs-createGesture tradeoff already covered in Standard Stack/Alternatives Considered.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Spring Boot Test (Surefire for `*Test.java` unit tests, Failsafe + Testcontainers for `*IT.java` integration tests under the `ci` Maven profile) [VERIFIED: CLAUDE.md, cross-checked against `DiscoveryControllerIT.java`/`TripCloneServiceIT.java` existing] |
| Backend config file | `backend/pom.xml` (Surefire/Failsafe plugin config) — not modified by this phase |
| Backend quick run | `mvnw verify` (unit only, no Docker) |
| Backend full suite | `mvnw verify -Pci` (unit + integration, Testcontainers Postgres) |
| Frontend framework | Karma + Jasmine, `ng test` | 
| Frontend config file | Angular CLI default (`angular.json` test target) |
| Frontend quick run | `npm test` (watch mode) |
| Frontend full suite | `npm run test:ci` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SOCIAL-01 | Feed endpoint requires auth, returns ranked PUBLIC trips with full stop/photo data | integration (`*IT`, Testcontainers) | `mvnw verify -Pci -Dtest=DiscoveryFeedControllerIT` | ❌ Wave 0 — extend or replace `DiscoveryControllerIT.java`'s no-auth assertions once the endpoint requires auth |
| SOCIAL-01 (frontend) | Feed page renders and swipes | component spec (Karma/Jasmine) | `npm test -- --include='**/feed.page.spec.ts'` | ❌ Wave 0 |
| SOCIAL-02 | Like/unlike idempotency | already covered | (existing, unchanged) | ✅ backend done; frontend action-rail spec ❌ Wave 0 |
| SOCIAL-03 | Clone deep-copy semantics | already covered (`TripCloneServiceIT.java`) | `mvnw verify -Pci -Dtest=TripCloneServiceIT` | ✅ backend done; frontend action-rail spec ❌ Wave 0 |
| SOCIAL-04 | Save/unsave idempotent, 404-not-403 on private trip | integration (Testcontainers, mirroring `TripCloneServiceIT`'s style since no `TripLikeServiceTest`/`IT` unit-level precedent exists to follow instead) | `mvnw verify -Pci -Dtest=TripSaveServiceIT` | ❌ Wave 0 |
| SOCIAL-05 | Profile returns username/joinDate/interests; interests update validates limits | integration + unit | `mvnw verify -Dtest=ProfileServiceTest` / `-Pci -Dtest=ProfileControllerIT` | ❌ Wave 0 |
| SOCIAL-06 | Ranking orders tag-overlap trips first | integration (assert query order against seeded fixture data) | `mvnw verify -Pci -Dtest=DiscoveryFeedControllerIT` | ❌ Wave 0 (can combine with SOCIAL-01's test class) |

### Sampling Rate
- **Per task commit:** `mvnw verify` (backend) / `npm test -- --watch=false` (frontend) — unit-level only, fast.
- **Per wave merge:** `mvnw verify -Pci` (Testcontainers) + `npm run test:ci`.
- **Phase gate:** Full suite green before `/gsd-verify-work`, plus the manual Postman/browser regression this phase's `SecurityConfig` change owes per RISK-R2 (see Pitfall 1).

### Wave 0 Gaps
- [ ] `DiscoveryFeedControllerIT.java` (or extend `DiscoveryControllerIT.java`) — covers SOCIAL-01 auth requirement + SOCIAL-06 ranking order
- [ ] `TripSaveServiceIT.java` — covers SOCIAL-04
- [ ] `ProfileServiceTest.java` / `ProfileControllerIT.java` — covers SOCIAL-05
- [ ] `feed.page.spec.ts`, `feed-card.component.spec.ts`, `feed-action-rail.component.spec.ts` — new frontend component specs, no existing equivalent to extend
- [ ] No new test framework/config install needed — existing JUnit/Testcontainers (backend) and Karma/Jasmine (frontend) infrastructure covers this phase's needs.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT Bearer token via existing `JwtAuthFilter`/`UserPrincipal` — the feed, save, rate, and profile endpoints must all require it (see Pitfall 1 for the one place this is currently violated) |
| V3 Session Management | no (stateless JWT, no server sessions — already established project-wide) | n/a |
| V4 Access Control | yes | `TripOwnershipService.loadVisibleTripLite`/`isVisible` for every trip-scoped mutation (save, rate); profile endpoints scope to `principal.userId()` only, never a path-supplied user id |
| V5 Input Validation | yes | Bean Validation on new request DTOs: rating `@Min(1) @Max(5)`, interests array `@Size(max=20)` elements each `@Size(max=50)` chars — mirroring `Trip.tags`'s existing limits documented in `docs/api-contracts.md` |
| V6 Cryptography | no (no new secrets/crypto surface in this phase) | n/a |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Existence-oracle via 403 on a private trip's save/rate attempt | Information Disclosure | `TripOwnershipService.loadVisibleTripLite` → 404, matching the already-established SCRUM-274 convention (see Don't Hand-Roll) |
| Public discovery feed accidentally left unauthenticated (this phase's central pitfall) | Information Disclosure / Elevation of Privilege (feed shows content to non-account-holders when SOCIAL-01 requires accounts) | Remove `/api/discovery/**` from `SecurityConfig`'s `permitAll`; verify with a Postman/browser check per RISK-R2, not just an updated `MockMvc` test |
| Rating-value tampering (out-of-range or negative rating) | Tampering | Bean Validation `@Min(1) @Max(5)` on the request DTO, plus a `CHECK (rating BETWEEN 1 AND 5)` database constraint as defense-in-depth (mirrors the project's existing pattern of DB-level `CHECK` constraints for enum-like columns, `V10__add_enum_check_constraints.sql`) |
| Interests array used as an unbounded payload vector (DoS via huge array) | Denial of Service | Same `@Size` limits as `Trip.tags` (max 20 elements/50 chars each), backed by the project's existing global request-body size cap (`RequestSizeLimitFilter`, already in place project-wide) |

## Sources

### Primary (HIGH confidence)
- `backend/src/main/java/com/tripflow/backend/**` — read directly this session: `DiscoveryController.java`, `TripController.java`, `TripLikeService.java`, `TripLikeRepository.java`, `TripLike.java`, `TripLikeId.java`, `TripCloneService.java`, `TripOwnershipService.java`, `TripRepository.java`, `Trip.java`, `User.java`, `Stop.java`, `StopPhoto.java`, `StopResponse.java`, `TripResponse.java`, `TripSummaryResponse.java`, `TripOwnerSummaryResponse.java`, `TripMapper.java`, `SecurityConfig.java`, `AuthController.java`, `AuthService.java`, `AuthResponse.java`, `UserRepository.java`, `BaseEntity.java`, `GlobalExceptionHandler.java`, `StopPhotoService.java`, `StopPhotoRepository.java`, `RateLimitProperties.java`, `TripVisibility.java`, `StopType.java`, migration files `V1`–`V13`.
- `backend/src/test/java/com/tripflow/backend/controller/DiscoveryControllerIT.java` — grepped and read this session for the no-auth-passes-200 test names.
- `docs/api-contracts.md` — read in full this session; authoritative documented contract for every existing endpoint referenced above.
- `frontend/src/app/**` — read this session: `trip.model.ts`, `trip.service.ts`, `auth.model.ts`, `app.routes.ts`, `stop-photo-gallery.component.ts`, `package.json`.
- `.planning/phases/06-community-social/06-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/RISKS.md`, `docs/social-features-traceability-audit.md`, `.planning/config.json` — read in full this session.
- npm registry (`npm view swiper version`) — queried live this session: `14.2.0`, published 2026-08-26, 4,345,613 weekly downloads.

### Secondary (MEDIUM confidence)
- [Migrating from ion-slides to Swiper.js — Ionic Framework docs](https://ionicframework.com/docs/angular/slides) — official first-party confirmation that `ion-slides` is deprecated/removed and Swiper.js is the recommended replacement.
- [ion-slides API — Ionic v6 docs](https://ionicframework.com/docs/v6/api/slides) — confirms the deprecation timeline referenced above.
- [Swiper Angular Components — swiperjs.com/angular](https://swiperjs.com/angular/) — official Swiper docs for the Angular/web-component integration shape used in the Code Examples section.

### Tertiary (LOW confidence)
- [How to implement Swiper with Ionic 7 (ion-slides removed) — Ionic Academy](https://ionicacademy.com/swiper-ionic-7-ion-slides/) and [ionic 7 with angular replace ion slides with swiper — Medium](https://medium.com/@musie.eth/ionic-7-with-angular-replace-ion-slides-with-swiper-on-ion-segments-590df6872004) — community tutorials, used only to corroborate the official-docs finding above, not as a primary source.
- [How to create nested swiper with swiperjs in ionic angular — Ionic Forum](https://forum.ionicframework.com/t/how-to-create-nested-swiper-with-swiperjs-in-ionic-angular/220399) and [Fullscreen TikTok style Swiper — GitHub Discussion](https://github.com/nolimits4web/swiper/discussions/6666) — community sources behind the `nested: true` recommendation and Pitfall 4; flagged `[ASSUMED]` in the body text above since no single official Swiper doc page addresses this exact nested vertical+horizontal TikTok-feed configuration.

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM-HIGH — backend needs zero new dependencies (verified from source); the one new frontend dependency (Swiper) is confirmed via official Ionic docs, though the specific nested-feed configuration pattern is community-sourced.
- Architecture: HIGH for backend (every pattern mirrors a shipped, source-read precedent); MEDIUM for frontend (no existing precedent in this codebase, first-of-its-kind component).
- Pitfalls: HIGH for the discovery-auth contradiction (directly verified against source + a passing test); MEDIUM for the nested-Swiper gesture conflict (community-sourced, not codebase-verified since nothing like it exists yet to verify against).

**Research date:** 2026-08-31
**Valid until:** ~30 days (backend patterns are stable long-term; the Swiper.js recommendation should be re-checked if planning is delayed past ~4-6 weeks given its fast release cadence — pin the exact version at install time regardless).
