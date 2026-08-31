# Phase 6: Community & Social - Pattern Map

**Mapped:** 2026-08-31
**Files analyzed:** 15 (net-new + modified)
**Analogs found:** 13 / 15

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `domain/SavedTrip.java` | model | CRUD (join-table toggle) | `domain/TripLike.java` | exact |
| `domain/SavedTripId.java` | model (embeddable PK) | CRUD | `domain/TripLikeId.java` | exact |
| `repository/SavedTripRepository.java` | model/repository | CRUD | `repository/TripLikeRepository.java` | exact |
| `service/TripSaveService.java` | service | CRUD | `service/TripLikeService.java` | exact |
| `domain/TripRating.java` | model | CRUD (upsert) | `domain/TripLike.java` | role-match (upsert vs toggle) |
| `domain/TripRatingId.java` | model (embeddable PK) | CRUD | `domain/TripLikeId.java` | exact |
| `repository/TripRatingRepository.java` | model/repository | CRUD (upsert) | `repository/TripLikeRepository.java` | role-match (`DO UPDATE` vs `DO NOTHING`) |
| `service/TripRatingService.java` | service | CRUD | `service/TripLikeService.java` | role-match |
| `controller/ProfileController.java` | controller | request-response | `controller/AuthController.java` | role-match |
| `service/ProfileService.java` | service | CRUD (read/patch) | `service/AuthService.java` | role-match |
| `domain/User.java` (add `interests`) | model (modified) | CRUD | `domain/Trip.java` (`tags` field) | exact |
| `db/migration/V14__create_trip_saves.sql` | migration | — | `db/migration/V9__create_trip_likes.sql` | exact |
| `db/migration/V15__create_trip_ratings.sql` | migration | — | `db/migration/V9__create_trip_likes.sql` | role-match (adds CHECK constraint) |
| `db/migration/V16__add_user_interests.sql` | migration | — | `db/migration/V9__create_trip_likes.sql` (`ALTER TABLE trips ADD COLUMN like_count`) | exact |
| `security/SecurityConfig.java` (modified) | config | request-response | itself (existing file) | n/a — edit in place |
| `dto/FeedTripResponse.java` | model (DTO) | transform | `dto/TripSummaryResponse.java` (per RESEARCH.md) | role-match, no analog read this session |
| `controller/DiscoveryController.java` (extend `/feed`) | controller | request-response | itself (existing `/trips`, `/search` handlers) | exact |
| frontend Swiper feed components | component | streaming/UI | none — first-of-kind | no analog |

## Pattern Assignments

### `domain/SavedTrip.java` / `domain/SavedTripId.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/tripflow/backend/domain/TripLike.java` (full file, 59 lines) and `TripLikeId.java` (full file, 50 lines).

**Full pattern to copy verbatim, renaming `TripLike`→`SavedTrip`, `trip_likes`→`trip_saves`:**
```java
@Entity
@Table(name = "trip_saves")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SavedTrip {

    @EmbeddedId
    private SavedTripId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tripId")
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public SavedTrip(User user, Trip trip) {
        this.id = new SavedTripId(user.getId(), trip.getId());
        this.user = user;
        this.trip = trip;
    }
}
```
`SavedTripId` is a byte-for-byte copy of `TripLikeId` with the class name changed — same `@Embeddable`, same `userId`/`tripId` fields, same `equals`/`hashCode`.

**No `saved_count` column** — per RESEARCH.md Pattern 1 / A4, saves don't denormalize a count (nothing displays it). Skip the `Trip.likeCount`-equivalent field entirely.

---

### `repository/SavedTripRepository.java` (model/repository, CRUD)

**Analog:** `backend/src/main/java/com/tripflow/backend/repository/TripLikeRepository.java` (full file, 37 lines).

**Copy verbatim, substitute table/type names:**
```java
public interface SavedTripRepository extends JpaRepository<SavedTrip, SavedTripId> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO trip_saves (user_id, trip_id, created_at)
            VALUES (:userId, :tripId, NOW())
            ON CONFLICT (user_id, trip_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("tripId") Long tripId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SavedTrip st WHERE st.id.userId = :userId AND st.id.tripId = :tripId")
    int deleteByUserIdAndTripId(@Param("userId") Long userId, @Param("tripId") Long tripId);
}
```
Add a `findByUserId(Long userId, Pageable pageable)` (returns `Page<SavedTrip>`) for the `GET /api/trips/saved` list endpoint RESEARCH.md flags as an open question — no `TripLikeRepository` equivalent exists since likes have no list view, but the paged-query convention itself comes from `TripRepository`'s existing paged finder methods.

---

### `service/TripSaveService.java` / `service/TripRatingService.java` (service, CRUD)

**Analog:** `backend/src/main/java/com/tripflow/backend/service/TripLikeService.java` (full file, 59 lines).

**Core pattern — ownership check, then atomic insert/delete, log only on actual mutation:**
```java
// Source: backend/src/main/java/com/tripflow/backend/service/TripLikeService.java:31-57
@Transactional
public void likeTrip(Long tripId, Long requesterId) {
    tripOwnershipService.loadVisibleTripLite(tripId, requesterId);
    int inserted = tripLikeRepository.insertIfAbsent(requesterId, tripId);
    if (inserted > 0) {
        tripRepository.incrementLikeCount(tripId);
        log.info("Trip liked tripId={} userId={}", tripId, requesterId);
    } else {
        log.debug("Trip already liked tripId={} userId={}", tripId, requesterId);
    }
}
```
`TripSaveService.saveTrip`/`unsaveTrip` mirror this exactly minus the `tripRepository.incrementLikeCount` call (no count column to maintain). `TripRatingService.rateTrip` mirrors the ownership-check-first shape but calls an upsert query instead of insert-if-absent (see repository pattern below) — no "unrate" method needed since a rating is a value, not a toggle.

**Constructor injection convention:** `@Slf4j @Service @RequiredArgsConstructor` with `final` fields for each repository/service dependency — same as `TripLikeService.java:22-29`.

---

### `repository/TripRatingRepository.java` (model/repository, CRUD upsert)

**Analog:** `TripLikeRepository.insertIfAbsent`, adapted per RESEARCH.md Pitfall 3 (`DO UPDATE` not `DO NOTHING`):
```sql
-- Adapt TripLikeRepository's mechanism (native @Modifying @Query), change ON CONFLICT clause:
INSERT INTO trip_ratings (user_id, trip_id, rating, created_at, updated_at)
VALUES (:userId, :tripId, :rating, NOW(), NOW())
ON CONFLICT (user_id, trip_id) DO UPDATE SET rating = excluded.rating, updated_at = NOW()
```
Composite PK entity/id classes (`TripRating`/`TripRatingId`) copy `TripLike`/`TripLikeId`'s `@EmbeddedId`/`@MapsId` shape, plus a `rating` `Integer` column (`@Min(1) @Max(5)` validated at the DTO layer per RESEARCH.md Security Domain).

---

### `controller/ProfileController.java` / `service/ProfileService.java` (controller/service, request-response)

**Analog:** `controller/AuthController.java` + `service/AuthService.java` (both full files read this session) — closest existing analog for a user-scoped, non-trip-scoped controller/service pair. AuthController's specific auth/cookie/CSRF machinery does NOT apply to Profile (no cookies involved) — copy only the structural shape:

**Structural pattern to copy:**
```java
// Shape from AuthController.java:36-41 — @Tag, @RestController, @RequestMapping, @RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile: username, join date, stored interests")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.getProfile(principal.userId()));
    }

    @PatchMapping("/interests")
    public ResponseEntity<ProfileResponse> updateInterests(
            @Valid @RequestBody UpdateInterestsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.updateInterests(principal.userId(), request));
    }
}
```
Scope every read/write to `principal.userId()` — never a path-supplied user id (RESEARCH.md Security Domain V4). `ProfileService` follows `AuthService`'s `@Slf4j @Service @RequiredArgsConstructor` shape and its `userRepository.findById(...).orElseThrow(...)` pattern (see `AuthService.java:80` for the `orElse(null)`-then-check idiom, though Profile can use a straight `orElseThrow(ResourceNotFoundException::new)` since the principal is already authenticated — no timing-oracle concern like login has).

**Join date** = `user.getCreatedAt()` from `BaseEntity` (`backend/src/main/java/com/tripflow/backend/domain/BaseEntity.java:28-30`) — no new field needed, per RESEARCH.md SOCIAL-05 note.

---

### `domain/User.java` — add `interests` field (model, modified)

**Analog:** `backend/src/main/java/com/tripflow/backend/domain/Trip.java:50-52` (`tags` field, verbatim):
```java
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(columnDefinition = "TEXT[]")
private List<String> tags = new ArrayList<>();
```
Add the identical annotation pair to `User.java` as:
```java
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(columnDefinition = "TEXT[]")
private List<String> interests = new ArrayList<>();
```
`User.java` currently has no `@JdbcTypeCode`/array imports (`org.hibernate.annotations.JdbcTypeCode`, `org.hibernate.type.SqlTypes`, `java.util.ArrayList`, `java.util.List`) — add these imports alongside the existing `jakarta.persistence.*`/lombok imports at the top of the file (current imports: `Column`, `Entity`, `Table`, `Getter`, `NoArgsConstructor`, `Setter` — see `User.java:1-8`).

---

### Migrations (V14/V15/V16)

**Analog:** `backend/src/main/resources/db/migration/V9__create_trip_likes.sql` (full file, 18 lines).

**V14__create_trip_saves.sql** — copy verbatim, rename table:
```sql
CREATE TABLE trip_saves (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id    BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, trip_id)
);

CREATE INDEX idx_trip_saves_trip_id ON trip_saves(trip_id);
```
No `ALTER TABLE trips ADD COLUMN saved_count` line (unlike V9's `like_count` addition) — no count is denormalized for saves.

**V15__create_trip_ratings.sql** — same join-table shape plus a `rating` column and a `CHECK` constraint (project convention per `V10__add_enum_check_constraints.sql`, cited in RESEARCH.md):
```sql
CREATE TABLE trip_ratings (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id    BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, trip_id)
);

CREATE INDEX idx_trip_ratings_trip_id ON trip_ratings(trip_id);
```

**V16__add_user_interests.sql** — single `ALTER TABLE`, same idiom as V9's trailing `ALTER TABLE trips ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;`:
```sql
ALTER TABLE users ADD COLUMN interests TEXT[] NOT NULL DEFAULT '{}';
```

---

### `security/SecurityConfig.java` (modified — remove `/api/discovery/**` from permitAll)

**File to edit directly** — `backend/src/main/java/com/tripflow/backend/security/SecurityConfig.java:61-62`:
```java
// CURRENT (line 61-62):
.requestMatchers("/api/auth/**", "/api/discovery/**", "/actuator/health", "/actuator/metrics", "/actuator/metrics/**",
        "/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**").permitAll()
```
Remove `"/api/discovery/**"` from this matcher list so it falls through to `.anyRequest().authenticated()`. Per RESEARCH.md Pitfall 1, this is a deliberate breaking change to `DiscoveryControllerIT`'s existing `listPublicTrips_noAuth_returnsOnlyPublicTrips`-style tests — those tests must be updated to expect 401, not just left passing. Confirm whether `/api/discovery/search` and `/api/discovery/trips` (the two existing endpoints) should also require auth now, or only the new `/api/discovery/feed` — CONTEXT.md's Phase Boundary implies the whole discovery surface should require auth ("the feed requires authentication, it is not a public unauthenticated surface"), which argues for removing the whole `/api/discovery/**` prefix rather than carving out just `/feed`.

---

### `controller/DiscoveryController.java` — extend with `/feed` endpoint

**Analog:** itself — the existing `listPublicTrips`/`searchPublicTrips` handlers in the same file (full file read, 60 lines) establish the controller's own conventions to extend:
```java
// Source: backend/src/main/java/com/tripflow/backend/controller/DiscoveryController.java:52-58
@Operation(summary = "List public trips", description = "Paginated feed of PUBLIC trips. No authentication required.")
@GetMapping("/trips")
public ResponseEntity<PagedModel<TripSummaryResponse>> listPublicTrips(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<TripSummaryResponse> page = tripService.listPublicTrips(pageable);
    return ResponseEntity.ok(new PagedModel<>(page));
}
```
New `/feed` handler follows the identical `@Operation` + `@GetMapping` + `PagedModel` wrapping shape, but adds `@AuthenticationPrincipal UserPrincipal principal` (a parameter this controller doesn't currently have — pull the exact parameter/import shape from `TripController`'s `likeTrip`/`cloneTrip` handlers instead, e.g. `TripController.java:148-151`) and returns `PagedModel<FeedTripResponse>` instead of `TripSummaryResponse`. Update the `@Operation` description text — remove "No authentication required" once `SecurityConfig` changes land.

---

## Shared Patterns

### Ownership/visibility check (apply to TripSaveService, TripRatingService)
**Source:** `backend/src/main/java/com/tripflow/backend/service/TripOwnershipService.java:94-96`
```java
@Transactional(readOnly = true)
public Trip loadVisibleTripLite(Long tripId, Long requesterId) {
    return findVisible(tripRepository.findById(tripId), tripId, requesterId);
}
```
Call `tripOwnershipService.loadVisibleTripLite(tripId, requesterId)` as the first statement of every new mutation touching someone else's trip (save, unsave, rate) — throws 404 (not 403) for a private trip belonging to someone else, per the already-centralized SCRUM-274 convention. Do not reimplement this check inline.

### Idempotent join-table toggle (apply to save; ratings use the upsert variant)
**Source:** `backend/src/main/java/com/tripflow/backend/repository/TripLikeRepository.java:21-27` — `INSERT ... ON CONFLICT DO NOTHING` native query, never a Java `existsById` + conditional `save()`/`delete()`. See RESEARCH.md "Don't Hand-Roll" table for the concurrency rationale.

### Postgres array column (apply to User.interests)
**Source:** `backend/src/main/java/com/tripflow/backend/domain/Trip.java:50-52` — `@JdbcTypeCode(SqlTypes.ARRAY)` + `columnDefinition = "TEXT[]"`. Apply identical field-limit validation (`@Size(max=20)` elements, each `@Size(max=50)` chars) at the request-DTO layer, matching `docs/api-contracts.md`'s existing `Trip.tags` limits table.

### Logging convention (apply to all new services)
`@Slf4j`, parameterized messages, `log.info` only on an actual state change (row inserted/deleted/updated), `log.debug` on a no-op idempotent call — see `TripLikeService.java:40-43` for the exact info-vs-debug split to replicate in `TripSaveService`/`TripRatingService`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `dto/FeedTripResponse.java` | model (DTO) | transform | No existing DTO returns full trip+stops+photos+owner-username in one shape (`TripSummaryResponse` is deliberately a card projection with none of these) — build new, following RESEARCH.md's Architecture Patterns sketch and `TripResponse.java`'s general DTO-record conventions (not read this session; use existing `dto/` package's record style as the baseline). |
| `frontend/.../feed/`, `feed-card/`, `feed-action-rail/` components + Swiper integration | component | streaming/UI (gesture-driven) | No swipeable/carousel primitive exists anywhere in the frontend (`stop-photo-gallery` is a static grid). First-of-kind — build from RESEARCH.md's Swiper.js Code Examples section, not from a codebase analog. |
| `repository/StopPhotoRepository.findByStopIdIn` | repository method | batch fetch | Existing method (`findByStopIdOrderByCreatedAtAsc`) is single-stop only; the batch variant is a small net-new addition to an existing repository interface, not a new file — no separate analog needed beyond standard Spring Data derived-query naming. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/tripflow/backend/{domain,repository,service,controller,security}/`, `backend/src/main/resources/db/migration/`
**Files scanned this session:** `TripLike.java`, `TripLikeId.java`, `TripLikeRepository.java`, `TripLikeService.java`, `SecurityConfig.java`, `DiscoveryController.java`, `User.java`, `Trip.java`, `AuthController.java`, `AuthService.java`, `BaseEntity.java`, `TripOwnershipService.java`, `V9__create_trip_likes.sql` (plus RESEARCH.md's already-cited reads of `TripController.java`, `TripCloneService.java`, `TripRepository.java`, `TripSummaryResponse.java`, `StopPhotoRepository.java`).
**Pattern extraction date:** 2026-08-31
