# Phase 2: Exports, Completion & Search - Pattern Map

**Mapped:** 2026-08-21
**Files analyzed:** 13
**Analogs found:** 13 / 13

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `client/mapbox/MapboxProperties.java` | config | request-response | `client/ors/OrsProperties.java` | exact |
| `client/mapbox/MapboxClientConfig.java` | config | request-response | `client/ors/OrsClientConfig.java` | exact (with one deviation — see below) |
| `client/mapbox/MapboxClient.java` | service | request-response (byte[] image fetch) | `client/ors/OrsClient.java` | exact |
| `exception/MapboxClientException.java` | utility (exception) | — | `exception/OrsClientException.java` | exact |
| `exception/GlobalExceptionHandler.java` (modified) | middleware | request-response | itself, extending `handleOrsFailure`/`handleGeminiFailure` block | exact |
| `service/PdfExportService.java` | service | file-I/O (streamed bytes) | `service/IcsExportService.java` | exact |
| `controller/TripExportController.java` (modified) | controller | request-response | itself (`exportIcs` method) | exact |
| `dto/TripOwnerSummaryResponse.java` | model (DTO record) | CRUD | `dto/TripSummaryResponse.java` | exact |
| `dto/TripResponse.java` (modified) | model (DTO record) | CRUD | itself + `mapper/TripMapper.java` | exact |
| `mapper/TripMapper.java` (modified) | utility (mapper) | transform | itself (`toResponse`) | exact |
| `repository/TripSearchRepository.java` (modified) | repository | CRUD | itself (`searchPublicTrips` signature) | exact |
| `repository/TripSearchRepositoryImpl.java` (modified) | repository | CRUD | itself (`searchPublicTrips`/`matchingIds`/`countMatches`) | exact |
| `controller/TripController.java` (modified — `listTrips`) | controller | CRUD | itself | exact |
| `service/TripService.java` (modified) | service | CRUD | itself (existing `listTrips`, not shown in excerpts — same class as `getTrip` used above) | exact |
| frontend `trip.service.ts` (modified — `exportPdf`, search params) | service | request-response | itself (`exportIcs` blob method) | exact |

## Pattern Assignments

### `backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxProperties.java` (config)

**Analog:** `backend/src/main/java/com/tripflow/backend/client/ors/OrsProperties.java` (full file, 34 lines)

```java
package com.tripflow.backend.client.ors;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.tripflow.backend.config.SecretMask;

@ConfigurationProperties(prefix = "ors")
public record OrsProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    @Override
    public String toString() {
        return "OrsProperties[baseUrl=" + baseUrl
                + ", apiKey=" + SecretMask.mask(apiKey)
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
```
Copy verbatim structure: `@ConfigurationProperties(prefix = "mapbox")`, record fields `baseUrl`, `accessToken`, `connectTimeout`, `readTimeout`, masked `toString()` via existing `SecretMask.mask(...)` (`config/SecretMask.java` — reuse, don't reimplement).

---

### `backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClientConfig.java` (config)

**Analog:** `backend/src/main/java/com/tripflow/backend/client/ors/OrsClientConfig.java` (full file, 33 lines)

```java
@Configuration
@EnableConfigurationProperties(OrsProperties.class)
public class OrsClientConfig {
	@Bean
    public RestClient orsRestClient(OrsProperties props, RestClient.Builder builder) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, props.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
```
**Deviation required (per RESEARCH.md Pitfall/Anti-Pattern):** Mapbox auth is a `?access_token=` query param, not a default header — do NOT copy the `.defaultHeader(HttpHeaders.AUTHORIZATION, ...)` line. Keep `baseUrl`/`requestFactory` timeout wiring identical; drop the auth header line and instead append `access_token` per-request in `MapboxClient` (query param on each `uri(...)` call).

---

### `backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClient.java` (service)

**Analog:** `backend/src/main/java/com/tripflow/backend/client/ors/OrsClient.java` (full file, 90 lines)

Core shared `execute()` translation pattern (lines 68-87 of `OrsClient.java`):
```java
private <T> T execute(Supplier<T> call, String failureMessage) {
    try {
        return call.get();
    } catch (HttpClientErrorException.TooManyRequests ex) {
        log.warn("ORS call rate-limited (429): {}", ex.getMessage());
        throw new OrsRateLimitException("OpenRouteService quota exceeded", ex);
    } catch (RestClientException ex) {
        log.warn("ORS call failed: {}", ex.getMessage());
        throw new OrsClientException(failureMessage, ex);
    }
}
```
For `MapboxClient`: no rate-limit-specific catch needed (Mapbox free tier is 1,250 req/min, not hit by this flow per RESEARCH.md) — just the `RestClientException -> MapboxClientException` translation. Call shape:
```java
return execute(() -> mapboxRestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/styles/v1/{username}/{style}/static/{overlay}/auto/{w}x{h}")
                .queryParam("access_token", props.accessToken())
                .build(username, style, overlay, width, height))
        .retrieve()
        .body(byte[].class), "Mapbox static image request failed");
```
Add the URL-length guard (8,192 chars, RESEARCH.md Pitfall 2) before calling `execute` — build the full request URI, check length, fall back to marker-only overlay if exceeded.

---

### `backend/src/main/java/com/tripflow/backend/exception/MapboxClientException.java` (utility)

**Analog:** `exception/OrsClientException.java` — mirror its constructor shape (`(String message)`, `(String message, Throwable cause)`), a plain `RuntimeException` subclass.

**GlobalExceptionHandler addition** (`exception/GlobalExceptionHandler.java` lines 80-90, extend directly beneath):
```java
@ExceptionHandler(OrsClientException.class)
public ResponseEntity<ApiError> handleOrsFailure(OrsClientException ex, HttpServletRequest req) {
    log.error("502 Bad Gateway on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
    return error(HttpStatus.BAD_GATEWAY, "Route service is temporarily unavailable", req, null);
}

@ExceptionHandler(GeminiClientException.class)
public ResponseEntity<ApiError> handleGeminiFailure(GeminiClientException ex, HttpServletRequest req) {
    log.error("502 Bad Gateway on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
    return error(HttpStatus.BAD_GATEWAY, "AI itinerary service is temporarily unavailable", req, null);
}
```
Add a third `@ExceptionHandler(MapboxClientException.class)` block, same shape, message e.g. "Map snapshot service is temporarily unavailable" — BUT per RESEARCH.md, `PdfExportService` should catch this internally and degrade gracefully (omit map image, log, continue) rather than let it bubble to a 502 for the whole PDF export. The `GlobalExceptionHandler` entry is a safety net only, not the primary path.

---

### `backend/src/main/java/com/tripflow/backend/service/PdfExportService.java` (service)

**Analog:** `backend/src/main/java/com/tripflow/backend/service/IcsExportService.java` (full file, 95 lines)

```java
@Service
@RequiredArgsConstructor
public class IcsExportService {
    private final TripService tripService;
    private final RouteScheduleProperties scheduleProperties;

    public record IcsExport(String tripTitle, String icsContent) {
    }

    public IcsExport exportIcs(Long tripId, Long requesterId) {
        TripResponse trip = tripService.getTrip(tripId, requesterId);
        // ... build content ...
        return new IcsExport(trip.title(), ical.write());
    }
}
```
Copy shape exactly: `@Service @RequiredArgsConstructor`, inject `TripService`, delegate ownership/visibility check to `tripService.getTrip(tripId, requesterId)` as the very first line (never reimplement the owner-or-PUBLIC check), return an export record (`PdfExport(String tripTitle, byte[] pdfBytes)`) so the controller doesn't need a second lookup. Inject `MapboxClient` alongside `TripService` for the map snapshot step; wrap that specific call in a try/catch that logs and proceeds without the image on failure (see MapboxClientException note above).

---

### `backend/src/main/java/com/tripflow/backend/controller/TripExportController.java` (modified)

**Analog:** itself — `exportIcs` method (lines 34-47) and `sanitizeFilename` (lines 63-69), both to be reused directly.

```java
@Operation(summary = "Export a trip as an .ics calendar file", ...)
@GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
public ResponseEntity<String> exportIcs(
        @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
    IcsExportService.IcsExport export = icsExportService.exportIcs(id, principal.userId());
    return ResponseEntity.ok()
            .contentType(TEXT_CALENDAR)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + sanitizeFilename(export.tripTitle()) + ".ics\"")
            .body(export.icsContent());
}

static String sanitizeFilename(String title) {
    String sanitized = title.replaceAll("[^a-zA-Z0-9 \\-]", "").trim();
    if (sanitized.isEmpty()) { sanitized = "trip"; }
    return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
}
```
New `exportPdf` method: same shape, inject `PdfExportService`, `produces = MediaType.APPLICATION_PDF_VALUE`, `ResponseEntity<byte[]>`, call the **same** package-private `sanitizeFilename` (D-05 — do not duplicate it).

---

### `backend/src/main/java/com/tripflow/backend/dto/TripOwnerSummaryResponse.java` (new DTO)

**Analog:** `dto/TripSummaryResponse.java` (full file, 25 lines)

```java
public record TripSummaryResponse(
        Long id, String title, TripVisibility visibility, TripStatus status,
        Instant createdAt, Instant updatedAt, long stopCount, String coverPhotoUrl
) {}
```
New record: same fields plus `long visitedStopCount, double completionPercentage` (per D-06/D-07/D-08). Keep `TripSummaryResponse` byte-for-byte unchanged — used by `findSummariesByVisibility(PUBLIC)`/`searchPublicTrips`. `TripOwnerSummaryResponse` backs only `findSummariesByUserId` and the new `searchOwnedTrips`.

---

### `backend/src/main/java/com/tripflow/backend/mapper/TripMapper.java` (modified)

**Analog:** itself, `toResponse` (lines 35-55) — pure field-by-field record construction from a `Trip` entity, no branching logic today.

```java
public TripResponse toResponse(Trip trip) {
    List<StopResponse> stopResponses = trip.getStops().stream().map(stopMapper::toResponse).toList();
    return new TripResponse(
            trip.getId(), trip.getTitle(), trip.getDescription(), trip.getTags(),
            trip.getVisibility(), trip.getStatus(), trip.getUser().getId(), stopResponses,
            trip.getCreatedAt(), trip.getUpdatedAt(), trip.getRouteGeometry(),
            trip.getStartDate(), trip.getLikeCount());
}
```
Add `visitedStopCount`/`completionPercentage` computed here (D-06/D-07): `long visited = stopResponses.stream().filter(s -> s.status() == StopStatus.VISITED).count(); double pct = stopResponses.isEmpty() ? 0.0 : (double) visited / stopResponses.size();` — append both as new trailing `TripResponse` constructor args.

---

### `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java` (modified)

**Analog:** itself, full file (84 lines) — `searchPublicTrips`/`matchingIds`/`countMatches`.

```java
@Override
public Page<TripSummaryResponse> searchPublicTrips(String query, Pageable pageable) {
    String pattern = "%" + query + "%";
    List<Long> ids = matchingIds(pattern, pageable);
    long total = countMatches(pattern);
    if (ids.isEmpty()) { return new PageImpl<>(List.of(), pageable, total); }
    TypedQuery<TripSummaryResponse> summaryQuery = entityManager.createQuery("""
            SELECT new com.tripflow.backend.dto.TripSummaryResponse(
                t.id, t.title, t.visibility, t.status, t.createdAt, t.updatedAt,
                (SELECT COUNT(s) FROM Stop s WHERE s.trip = t), null)
            FROM Trip t WHERE t.id IN :ids ORDER BY t.createdAt DESC, t.id DESC
            """, TripSummaryResponse.class);
    summaryQuery.setParameter("ids", ids);
    return new PageImpl<>(summaryQuery.getResultList(), pageable, total);
}

@SuppressWarnings("unchecked")
private List<Long> matchingIds(String pattern, Pageable pageable) {
    return entityManager.createNativeQuery("""
            SELECT t.id FROM trips t
            WHERE t.visibility = 'PUBLIC'
              AND (t.title ILIKE :pattern
                   OR EXISTS (SELECT 1 FROM unnest(t.tags) tag WHERE tag ILIKE :pattern))
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT :limit OFFSET :offset
            """)
            .setParameter("pattern", pattern)
            .setParameter("limit", pageable.getPageSize())
            .setParameter("offset", pageable.getOffset())
            .getResultList();
}
```
Two-step native-id-then-JPQL-refetch pattern is the load-bearing thing to copy — new `searchOwnedTrips(userId, query, filters, pageable)` follows the exact same shape:
1. Extract shared WHERE fragment (title ILIKE / tags unnest / **new**: `EXISTS (SELECT 1 FROM stops s JOIN places p ON p.id = s.place_id WHERE s.trip_id = t.id AND p.name ILIKE :pattern)`) into a private constant/helper per D-11, used by both `matchingIds`/`countMatches` variants.
2. `searchOwnedTrips`'s native query scopes on `t.user_id = :userId` instead of `t.visibility = 'PUBLIC'`, AND-chains new filter params (`status`, `visibility`, `startDateFrom/To`, `durationDays` via `COALESCE(MAX(s.day_number),0)` subquery — see RESEARCH.md Pattern 4).
3. Refetch step: same `SELECT new ... FROM Trip t WHERE t.id IN :ids` shape, but constructs `TripOwnerSummaryResponse` instead, adding `visitedStopCount`/`completionPercentage` computed subqueries or an application-layer post-fetch (either is consistent with existing style — prefer subquery/JPQL for a single round trip since `stopCount` already does this).
4. All params bound via `.setParameter(...)` — never string-concatenate user input (SQL injection guard already enforced by every existing query here).

**Interface change** — `repository/TripSearchRepository.java`: add `Page<TripOwnerSummaryResponse> searchOwnedTrips(Long userId, String query, ..., Pageable pageable)` alongside the existing `Page<TripSummaryResponse> searchPublicTrips(String query, Pageable pageable)` signature.

---

### `backend/src/main/java/com/tripflow/backend/controller/TripController.java` (modified `listTrips`)

**Analog:** itself, `listTrips` (lines 52-59).

```java
@GetMapping
public ResponseEntity<PagedModel<TripSummaryResponse>> listTrips(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<TripSummaryResponse> page = tripService.listTrips(principal.userId(), pageable);
    return ResponseEntity.ok(new PagedModel<>(page));
}
```
Add new optional `@RequestParam` args (`search`, `status`, `visibility`, `startDateFrom`, `startDateTo`, `durationDays`), all nullable/optional, delegate to a new `tripService.searchOwnedTrips(...)` when any filter/search param is present (or always delegate and let empty filters mean "no-op AND", matching D-12's independently-optional param design) — return type becomes `PagedModel<TripOwnerSummaryResponse>` per D-08. Validate `status`/`visibility` against real enum constants (`TripStatus`: `DRAFT, PLANNED, ACTIVE, COMPLETED` — RESEARCH.md Pitfall 1 flags the frontend's stale `IN_PROGRESS` value as a drive-by fix).

---

### Frontend `trip.service.ts` (modified)

**Analog:** existing `exportIcs` blob method (`frontend/src/app/core/services/trip.service.ts` ~lines 103-111):
```typescript
exportIcs(tripId: number): Observable<Blob> {
  return this.http
    .get(`${this.baseUrl}/${tripId}/export/pdf`, { responseType: 'blob' })
    .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
}
```
New `exportPdf(tripId: number): Observable<Blob>` — identical shape, `/export/pdf` path, same `sanitizeFilename` duplicate convention in `trip-view.page.ts` per D-05 (extend that file's existing sanitizer usage, not a new one). Trip-list search: extend the existing `listTrips`-calling method with new optional query params, debounced (300-400ms, D-15) via RxJS `debounceTime`/`distinctUntilChanged` on the search input in the list page component — no existing debounce pattern found in this codebase; this is genuinely new but uses stock RxJS operators, not a new library.

## Shared Patterns

### External client isolation (`client/{service}/` triple)
**Source:** `backend/src/main/java/com/tripflow/backend/client/ors/{OrsProperties,OrsClientConfig,OrsClient}.java`
**Apply to:** `client/mapbox/MapboxProperties.java`, `MapboxClientConfig.java`, `MapboxClient.java`
Masked-secret `toString()`, independent connect/read timeouts, `@Component` wrapper with a single `execute(Supplier, failureMessage)` exception-translation helper. One deviation: Mapbox token goes in the query string per-request, not a default `Authorization` header.

### Ownership/visibility delegation
**Source:** `IcsExportService.exportIcs` line 49 (`tripService.getTrip(tripId, requesterId)`), `TripController.getTrip` javadoc (lines 69-76)
**Apply to:** `PdfExportService`, any new search entry point — always delegate the owner-or-PUBLIC (or owner-only, per D-09) check to `TripService`, never reimplement; non-owner access to a private trip is 404, not 403.

### Native-id-then-JPQL-refetch for search/list queries
**Source:** `TripSearchRepositoryImpl.searchPublicTrips`/`matchingIds`/`countMatches` (full file)
**Apply to:** new `searchOwnedTrips` — never fetch-join `stops`/`places` with `Pageable` (confirmed anti-pattern, `.planning/codebase/ARCHITECTURE.md:569-573`); always bind params via `.setParameter(...)`, never string-concatenate.

### External-client failure -> 502
**Source:** `GlobalExceptionHandler.handleOrsFailure`/`handleGeminiFailure` (lines 80-90)
**Apply to:** new `handleMapboxFailure(MapboxClientException...)` block, same shape, log at `ERROR` with throwable, generic user-facing message, `HttpStatus.BAD_GATEWAY`.

### Filename sanitization
**Source:** `TripExportController.sanitizeFilename` (lines 63-69) + `trip-view.page.ts`'s documented duplicate
**Apply to:** PDF export filename — call the existing static method directly (D-05), do not write a second sanitizer.

## No Analog Found

None — every file in this phase's scope extends an existing pattern already present in the codebase (per RESEARCH.md's own conclusion: "this phase is 'add a sibling,' not 'invent an architecture'"). The only genuinely new client-side behavior is the debounced search input (D-15), which has no prior debounce pattern in this codebase but uses stock RxJS operators already available via the existing `rxjs` dependency — no new library, no analog needed.

## Metadata

**Analog search scope:** `backend/src/main/java/com/tripflow/backend/{client/ors,client/gemini,controller,service,repository,dto,mapper,exception}`, `frontend/src/app/core/services/trip.service.ts`
**Files scanned:** 13 (read in full or targeted sections)
**Pattern extraction date:** 2026-08-21
