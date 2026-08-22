# Phase 2: Exports, Completion & Search - Research

**Researched:** 2026-08-21
**Domain:** Spring Boot PDF generation (OpenPDF), external HTTP image API (Mapbox Static Images), Spring Data JPA native-query joins, paged search/filter
**Confidence:** MEDIUM-HIGH — backend conventions and DB schema are directly read from source (HIGH); OpenPDF/Mapbox API specifics are web-verified against authoritative sources but not exercised in this codebase yet (MEDIUM)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**PDF export (EXPORT-02)**
- **D-01:** PDF content = header + ordered stops + notes **+ a route map snapshot** image embedded in the document.
- **D-02:** PDF library: OpenPDF (LGPL/MPL fork of iText 4) — new pom.xml dependency, no PDF lib exists today.
- **D-03:** Map snapshot is rendered server-side via the Mapbox Static Images API, authenticated with a **new backend env var** (e.g. `MAPBOX_TOKEN` or equivalent) — mirrors the existing `client/{service}/` pattern (`OrsProperties`, `GeminiProperties`) rather than having the frontend render and upload an image. — **Reversibility:** costly — once shipped, switching to a client-rendered-image approach changes the request contract and removes a documented backend secret.
- **D-04:** Map source: use `Trip.routeGeometry` (optimized route) when present; fall back to plain stop pins (no line) for trips never optimized.
- **D-05:** PDF filename uses the exact same `sanitizeFilename` convention already established for `.ics` (`TripExportController.sanitizeFilename` + its documented frontend duplicate in `trip-view.page.ts`) — same character-set/length rules, same dual-implementation-with-shared-fixture-tests pattern.

**Trip completion percentage (EXPORT-03)**
- **D-06:** Only `StopStatus.VISITED` counts toward completion. `SKIPPED` stays in the denominator (total stop count) but not the numerator. `completionPercentage = visitedStopCount / stopCount`.
- **D-07:** Zero-stop trips: `completionPercentage = 0` (not null). No null-handling needed downstream.
- **D-08:** `visitedStopCount`/`completionPercentage` are exposed on `TripResponse` (detail view) **and** on a richer summary DTO for the owner's own trip list — but **NOT** on the DTO the public discovery feed uses. `TripSummaryResponse` today is shared by `TripRepository.findSummariesByUserId` (owner list — Phase 2 target), `TripRepository.findSummariesByVisibility(PUBLIC)` (discovery listing), and `TripSearchRepositoryImpl.searchPublicTrips` (discovery search) — the same record backs all three. Adding completion fields to it as-is would leak a stranger's progress on their PUBLIC trip into the discovery feed. **Split into two DTOs**: `findSummariesByUserId` (and the new owner-search method, D-10) gets the richer one; `findSummariesByVisibility(PUBLIC)` and `searchPublicTrips` keep the existing lean `TripSummaryResponse` unchanged. — **Reversibility:** one-way — once the public discovery feed's DTO is locked without completion data, exposing it later is a contract change reviewers (Neel, per CLAUDE.md serialize-point rule) would need to sign off on.

**Search & filter (SEARCH-01)**
- **D-09:** Confirmed scope: `GET /api/trips` is the owner's own trip list only (`principal.userId()`), not public trips. Public-trip search (`GET /api/discovery/search`) already exists separately and is untouched.
- **D-10:** "Destination" search matches title + tags (same fields `TripSearchRepositoryImpl` already searches for discovery) **plus stop/place names** — requires a new join to `Stop` → `Place`, which no existing query does today.
- **D-11:** Reuse: extend the existing `TripSearchRepository` interface with a second method (`searchOwnedTrips(userId, query, filters, pageable)`) rather than a new repository. Share WHERE-clause construction with `searchPublicTrips` via a private helper — the only real difference between the two is scope (`visibility='PUBLIC'` vs `user_id=:userId`) plus the new filter params. One repository, one place the row-shape/query-building logic lives (per `TripSearchRepositoryImpl`'s own doc comment already tracking this principle for the row shape).
- **D-12:** Filters (status, visibility, date range, duration) all AND together; `search` is a separate, independently-optional query param. E.g. `?search=paris&status=ACTIVE&visibility=PUBLIC&startDateFrom=...&durationDays=...`.
- **D-13:** Date range filter applies to `Trip.startDate` (not `createdAt`) — filters by when the trip happens, not when the record was created. Confirmed no privacy concern since this is the owner's own data only (D-09).
- **D-14:** New filter dimension added to SEARCH-01's original scope (status/date/visibility): **trip duration in days**. Computed as `max(dayNumber)` across a trip's stops (stops already carry `dayNumber` from the scheduler/optimizer) — not a new stored field. Zero-stop trips have no computable duration (excluded from a duration filter, or treated as 0 — same convention as D-07; leave exact handling to planner).
- **D-15:** Frontend trip-list search input debounces as-you-type (~300-400ms) against the API rather than search-on-submit-only.

### Claude's Discretion
- Exact Mapbox Static Images API params (image dimensions, zoom/padding, marker styling) — standard defaults, follow whatever the existing frontend map styling suggests for visual consistency.
- Whether a zero-stop trip is excluded from duration-filter results entirely or reports `durationDays: 0` — pick whichever is simpler to implement consistently with D-07's zero-stops convention.
- Naming of the new richer owner-list summary DTO (D-08) — e.g. `TripOwnerSummaryResponse` or similar; just needs to be clearly distinct from the untouched public-facing `TripSummaryResponse`.
- Whether `durationDays` itself gets exposed as a response field (for a frontend badge) or stays purely internal to the filter query.

### Deferred Ideas (OUT OF SCOPE)
- **Public discovery feed should not expose future `startDate` for other users' trips.** Raised during this discussion but is a Phase 6 (discovery feed / `DiscoveryController`) concern — Phase 2 never touches that controller or its response shape. Flag for Phase 6 discussion: whether `startDate` (or any date field) on a PUBLIC trip should be hidden/redacted when in the future, for privacy reasons.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EXPORT-02 | Users can export a trip as a formatted PDF itinerary (header, ordered stops, notes) | OpenPDF version/package/API surface verified against the real published jar (see Standard Stack, Code Examples); Mapbox Static Images request shape and GeoJSON-overlay reuse of `Trip.routeGeometry` verified against official docs (see Architecture Patterns) |
| EXPORT-03 | Trip responses expose enough data (visited/total stop counts) to compute a completion percentage, handling the zero-stops case without dividing by zero | `StopStatus`/`Stop.dayNumber` nullability confirmed by reading `Stop.java`/`V10__add_enum_check_constraints.sql`; existing `TripSummaryResponse`/`TripRepository` projection pattern documented for the D-08 DTO split |
| SEARCH-01 | Users can search (by title/destination) and filter (by status, date range, visibility) their trip list, using the paged response convention from REF-21/SCRUM-110 | `TripSearchRepositoryImpl`'s native-query pattern read in full; `stops`/`places` table/column names confirmed from Flyway migrations for the D-10 join; `TripStatus`/`TripVisibility` enum values confirmed from source for filter validation |
</phase_requirements>

## Summary

All three remaining Phase 2 slices extend patterns that already exist in this codebase almost verbatim — this phase is "add a sibling," not "invent an architecture." PDF export gets its own service next to `IcsExportService` and its own controller method next to `exportIcs`, reusing `sanitizeFilename` and the "delegate ownership to `TripService.getTrip`" pattern. The Mapbox call gets its own `client/mapbox/` module mirroring `client/ors/`'s `*Properties`/`*ClientConfig`/`*Client` triple. Completion percentage is a pure computed field added at the mapper layer, no new persistence. Search/filter extends `TripSearchRepositoryImpl`'s existing native-query-then-JPQL-refetch pattern with a new method and a new `stops JOIN places` clause.

Two verification-driven corrections worth flagging up front: (1) OpenPDF's actual latest published Maven Central artifact is **2.2.2** using the legacy `com.lowagie.text` package — not `org.openpdf`/3.0.5 as web search results claimed (confirmed by downloading and inspecting the real jar, see Package Legitimacy Audit). (2) `Trip.routeGeometry` is stored as a raw GeoJSON `LineString` JSON string already — Mapbox's Static Images API accepts a `geojson(...)` overlay directly, so **no polyline-encoding library is needed**; the stored string can be URL-encoded and dropped straight into the overlay segment.

**Primary recommendation:** Add `com.github.librepdf:openpdf:2.2.2` (package `com.lowagie.text`) for PDF generation; build `client/mapbox/` following the `client/ors/` triple exactly, using the `geojson()` overlay form of the Static Images API against `Trip.routeGeometry` with a URL-length guard (8,192 chars) that falls back to marker-only; add `visitedStopCount`/`completionPercentage` to `TripResponse` and a new `TripOwnerSummaryResponse` record (not `TripSummaryResponse`); extend `TripSearchRepository`/`TripSearchRepositoryImpl` with a `searchOwnedTrips` method that joins `stops` → `places` in the same native-query-then-JPQL-refetch shape `searchPublicTrips` already uses.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| PDF document generation (layout, table, embedded image) | API / Backend | — | Server-owned per D-02/D-03; OpenPDF runs entirely server-side, response streamed as `ResponseEntity<byte[]>` |
| Route map snapshot rendering | API / Backend (calls external CDN/Static tier) | CDN / Static (Mapbox) | D-03 explicitly rejects frontend-rendered-and-uploaded image in favor of a backend-owned external call, mirroring `OrsClient`/`GeminiClient` |
| Completion percentage computation | API / Backend | — | Pure derived field from already-loaded `Stop.status` counts; no new persistence, computed in the mapper/service layer on every read |
| Search/filter query construction | Database / Storage (native SQL) → API / Backend (JPQL refetch + DTO projection) | — | Follows the existing two-step pattern: native id-matching query does the Postgres-specific work (`ILIKE`, `unnest`, join), then a portable JPQL query re-fetches the flat projection |
| Search UI (debounced input, filter controls) | Browser / Client (Angular) | — | D-15's 300-400ms debounce is a pure client-side concern against the existing paged `GET /api/trips` |
| PDF/`.ics` download trigger | Browser / Client | — | Existing `TripService.exportIcs` blob pattern (`responseType: 'blob'`) is the template; PDF export mirrors it 1:1 |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.github.librepdf:openpdf` | **2.2.2** [VERIFIED: Maven Central solrsearch API, queried this session] | PDF document generation (header, stop table, notes, embedded map image) | D-02 locked choice; actively maintained iText-2/4-compatible fork, LGPL/MPL, no new transitive Jackson/servlet conflicts observed in the jar listing |

**Package name correction (important):** OpenPDF 2.2.2's classes live under `com.lowagie.text` / `com.lowagie.text.pdf` — **not** `org.openpdf` [VERIFIED: downloaded `openpdf-2.2.2.jar` from `repo1.maven.org` this session and listed its contents — `com/lowagie/text/Document.class`, `com/lowagie/text/pdf/PdfWriter.class`, `com/lowagie/text/pdf/PdfPTable.class`, `com/lowagie/text/Image.class`, `com/lowagie/text/Paragraph.class` are all present under `com/lowagie/text/**`]. A WebSearch/WebFetch pass on OpenPDF's GitHub README and Maven Central listing both **incorrectly** reported a "3.0.5" release using a renamed `org.openpdf` package — that version does not exist on Maven Central (`v:"3.0.5"` query returned zero results; `latestVersion` field for `com.github.librepdf:openpdf` is `2.2.2`, indexed 2025-06-16). Do not trust `org.openpdf` imports from any AI-generated OpenPDF example — use `com.lowagie.text.*`.

**Java compatibility:** the downloaded jar's `Document.class` has bytecode major version `0x41` (65 decimal), which is the Java 21 class-file version [VERIFIED: `unzip -p ... | xxd`, this session] — compatible with the project's Java 21 / Spring Boot 4.1 baseline, no toolchain changes needed.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.boot:spring-boot-starter-restclient` | already in `pom.xml` | HTTP client for the Mapbox Static Images GET | Already used identically by `OrsClientConfig`/`GeminiClientConfig` — no new HTTP client dependency needed; `RestClient.retrieve().body(byte[].class)` is sufficient for a raw image response [ASSUMED — standard Spring `RestClient`/`HttpMessageConverter` behavior via the built-in `ByteArrayHttpMessageConverter`, not exercised in this codebase yet, no existing binary-response caller to point at] |

**No new dependency for polyline/route rendering.** `Trip.routeGeometry` is already a JSON-encoded GeoJSON `LineString` string (per `TripResponse.routeGeometry` javadoc and `RouteOptimizationService`'s `objectMapper.writeValueAsString(geometry)`, confirmed in `docs/api-contracts.md`). Mapbox's Static Images API accepts a raw GeoJSON object as an overlay via `geojson({geojson})`, URI-component-encoded [CITED: docs.mapbox.com/api/maps/static-images/, fetched this session] — so the existing stored string can be `URLEncoder.encode(...)`'d (Java stdlib) directly into the overlay segment. No polyline-encoding algorithm/library needs to be hand-rolled or added.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| OpenPDF | Apache PDFBox, iText 7 (AGPL/commercial) | D-02 already locks OpenPDF — PDFBox has a lower-level, more verbose API for tables; iText 7 requires a commercial license for this project's use case |
| Mapbox `geojson()` overlay | Encode `routeGeometry` coordinates into a Mapbox/Google encoded-polyline string and use the `path-` overlay | Polyline is more URL-length-efficient for long routes, but requires hand-writing/importing an encoding algorithm; GeoJSON overlay reuses the stored string with zero new code and is simpler — prefer it unless URL length becomes a real problem (see Common Pitfalls) |

**Installation:**
```xml
<!-- backend/pom.xml — add alongside the existing <biweekly.version> property block -->
<properties>
    <openpdf.version>2.2.2</openpdf.version>
</properties>

<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>${openpdf.version}</version>
</dependency>
```

**Version verification performed this session:**
```bash
curl -s "https://search.maven.org/solrsearch/select?q=g:%22com.github.librepdf%22+AND+a:%22openpdf%22&core=gav&rows=5&wt=json"
# → latestVersion 2.2.2, versionCount 84, timestamp 2025-06-16 (com.github.librepdf:openpdf)
curl -sL "https://repo1.maven.org/maven2/com/github/librepdf/openpdf/2.2.2/openpdf-2.2.2.jar" -o openpdf.jar
unzip -l openpdf.jar | grep -E "Document.class|PdfWriter.class|PdfPTable.class|Image.class|Paragraph.class"
# → all under com/lowagie/text/** and com/lowagie/text/pdf/**
```

## Package Legitimacy Audit

The project's `package-legitimacy check` tool only supports `npm|pypi|crates` ecosystems — it does not cover Maven, so verification below was done directly against Maven Central's own registry API and by inspecting the downloaded jar (equivalent rigor, same session).

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `com.github.librepdf:openpdf` | Maven Central | 84 published versions on this artifact, active project since ~2015 (LibrePDF fork of the original iText 4 / OpenPDF project) | Not directly queryable via solrsearch; widely used (parent of `openpdf-html`, `openpdf-kotlin`, `pdf-toolbox` sibling artifacts, all co-released) | github.com/LibrePDF/OpenPDF (official, referenced by groupId `com.github.librepdf`) | **OK** — mature, active, well-known LGPL/MPL fork; version and package name independently verified against the actual jar, not just a registry existence check | Approved — use `2.2.2` / `com.lowagie.text` package |

**Packages removed due to [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none. (The "3.0.5"/`org.openpdf` claim surfaced by WebSearch was not a slopsquat — it is the same real project's aspirational/in-progress next major version described inaccurately by a summarizing tool, not a malicious or fake package. Still, treat any AI-suggested `org.openpdf` import as wrong until that version is confirmed actually published.)

No new npm/PyPI packages are introduced by this phase — Mapbox Static Images is a plain HTTP GET against an existing Spring `RestClient`, not a new library dependency.

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────┐        ┌──────────────────────────────────────────────┐
│  Angular trip-view page  │        │              Spring Boot backend               │
│  (existing exportIcs     │  GET   │                                                │
│   blob pattern extended) │───────▶│  TripExportController                          │
└─────────────────────────┘        │    GET /{id}/export/pdf                        │
                                    │        │                                       │
                                    │        ▼                                       │
                                    │  PdfExportService (new, mirrors IcsExportService)│
                                    │    1. tripService.getTrip(id, requesterId)      │
                                    │       (ownership/visibility check, reused)      │
                                    │    2. MapboxClient.staticSnapshot(routeGeometry │
                                    │       or stop pins)  ───────────────┐            │
                                    │    3. build OpenPDF Document        │            │
                                    │       (header, PdfPTable of stops,  │            │
                                    │        notes, embedded map Image)   │            │
                                    │    4. write to ByteArrayOutputStream│            │
                                    │        │                            │            │
                                    │        ▼                            ▼            │
                                    │  ResponseEntity<byte[]>      client/mapbox/      │
                                    │  (Content-Disposition via    MapboxClientConfig  │
                                    │   sanitizeFilename, reused)  MapboxProperties    │
                                    │                               MapboxClient       │
                                    │                                   │              │
                                    └───────────────────────────────────┼──────────────┘
                                                                        ▼
                                                          Mapbox Static Images API
                                                          GET /styles/v1/{style}/static/
                                                              geojson({routeGeometry})/
                                                              auto/{w}x{h}?access_token=…
                                                          ──▶ raw PNG/JPEG bytes


┌─────────────────────────┐        ┌──────────────────────────────────────────────┐
│  Angular trip list page  │  GET   │  TripController.listTrips (extended)          │
│  (debounced search input,│───────▶│    ?search=&status=&visibility=&startDateFrom=│
│   filter controls, D-15) │        │     &startDateTo=&durationDays=&page=&size=   │
└─────────────────────────┘        │        │                                       │
                                    │        ▼                                       │
                                    │  TripService.searchOwnedTrips / listTrips       │
                                    │        │                                       │
                                    │        ▼                                       │
                                    │  TripSearchRepositoryImpl.searchOwnedTrips (new)│
                                    │    1. native query: trips ⋈ stops ⋈ places      │
                                    │       (ILIKE title/tags/place-name + filters)   │
                                    │       → matching trip ids, page-limited         │
                                    │    2. JPQL refetch: same flat projection shape  │
                                    │       used by findSummariesByUserId, now the    │
                                    │       new TripOwnerSummaryResponse (D-08)       │
                                    └──────────────────────────────────────────────┘
```

### Recommended Project Structure
```
backend/src/main/java/com/tripflow/backend/
├── client/mapbox/                        # new — mirrors client/ors/, client/gemini/
│   ├── MapboxProperties.java             # @ConfigurationProperties, masked toString()
│   ├── MapboxClientConfig.java           # RestClient bean, connect/read timeouts
│   └── MapboxClient.java                 # staticSnapshot(...) -> byte[]
├── service/
│   ├── PdfExportService.java             # new — sibling to IcsExportService
│   └── TripService.java                  # extended: searchOwnedTrips, completion computation site
├── repository/
│   ├── TripSearchRepository.java         # extended: + searchOwnedTrips(...)
│   └── TripSearchRepositoryImpl.java     # extended: + stops⋈places join, shared WHERE helper
├── dto/
│   ├── TripOwnerSummaryResponse.java     # new — D-08's richer owner-list DTO
│   └── TripResponse.java                 # extended: + visitedStopCount, completionPercentage
├── mapper/TripMapper.java                # extended: completion computation
├── exception/
│   └── MapboxClientException.java        # new — mirrors OrsClientException, mapped to 502
└── controller/
    └── TripExportController.java         # extended: + GET /{id}/export/pdf
```

### Pattern 1: External client module (`client/{service}/` triple)
**What:** A `*Properties` `@ConfigurationProperties` record with masked `toString()`, a `*ClientConfig` `@Configuration` building a `RestClient` bean with independent connect/read timeouts, and a `*Client` `@Component` wrapping calls with a shared `execute(Supplier, failureMessage)` exception-translation helper.
**When to use:** Any new external HTTP integration — Mapbox Static Images fits exactly the shape `OrsClient`/`GeminiClient` already use.
**Example (verified against real source, not paraphrased — see `client/ors/OrsClientConfig.java:17-31` and `client/ors/OrsProperties.java:13-33`):**
```java
// MapboxProperties.java — mirrors OrsProperties.java:13-33
@ConfigurationProperties(prefix = "mapbox")
public record MapboxProperties(
        String baseUrl,
        String accessToken,
        Duration connectTimeout,
        Duration readTimeout
) {
    @Override
    public String toString() {
        return "MapboxProperties[baseUrl=" + baseUrl
                + ", accessToken=" + SecretMask.mask(accessToken)
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}

// MapboxClientConfig.java — mirrors OrsClientConfig.java:13-32 exactly
// (RestClient.Builder + HttpClientSettings.defaults().withConnectTimeout/.withReadTimeout)
// NOTE: unlike OrsClientConfig (Authorization header) or GeminiClientConfig
// (x-goog-api-key header), Mapbox Static Images requires the token as a
// *query parameter* (?access_token=...), not a default header — see Common Pitfalls.
```

### Pattern 2: OpenPDF document build (header + table + image, streamed as bytes)
**What:** Build a `Document`, attach a `PdfWriter` to a `ByteArrayOutputStream`, add a title `Paragraph`, a `PdfPTable` for the ordered stops, `notes` text, and an embedded `Image`.
**When to use:** `PdfExportService.exportPdf(tripId, requesterId)`.
**Example (verified against downloaded `openpdf-2.2.2.jar` contents this session — class names/paths only; method-level API surface below is standard OpenPDF/iText-4-lineage usage, not independently re-verified line-by-line, treat as [CITED: LibrePDF/OpenPDF project conventions] not [VERIFIED]):**
```java
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;

ByteArrayOutputStream out = new ByteArrayOutputStream();
Document doc = new Document();
PdfWriter.getInstance(doc, out);
doc.open();

doc.add(new Paragraph(trip.title()));

PdfPTable table = new PdfPTable(3); // e.g. order, name, notes
for (StopResponse stop : trip.stops()) {
    table.addCell(String.valueOf(stop.stopOrder() + 1));
    table.addCell(stop.name());
    table.addCell(stop.notes() != null ? stop.notes() : "");
}
doc.add(table);

if (mapSnapshotBytes != null) {
    Image mapImage = Image.getInstance(mapSnapshotBytes); // byte[] overload, no temp file needed
    doc.add(mapImage);
}

doc.close();
byte[] pdfBytes = out.toByteArray();
```
Mirror `IcsExportService`'s shape: delegate the ownership/visibility check to `tripService.getTrip(tripId, requesterId)` exactly as `IcsExportService.exportIcs` does (`service/IcsExportService.java:48-49`), and return an `export record` (`tripTitle`, `pdfBytes`) so the controller can build the filename without a second lookup — same as `IcsExportService.IcsExport` (`service/IcsExportService.java:45-46`).

### Pattern 3: Mapbox Static Images request (GeoJSON overlay, byte[] response)
**What:** `GET https://api.mapbox.com/styles/v1/{username}/{style_id}/static/{overlay}/{lon,lat,zoom|bbox|auto}/{width}x{height}?access_token=...` [CITED: docs.mapbox.com/api/maps/static-images/, fetched this session].
**Overlay for the route-present case:** `geojson({URI-encoded routeGeometry string})` — the GeoJSON object must be stringified and URI-component-encoded, wrapped in `geojson(...)` [CITED: same source]. Since `Trip.routeGeometry` is already a JSON string, this is `"geojson(" + URLEncoder.encode(trip.getRouteGeometry(), StandardCharsets.UTF_8) + ")"`.
**Overlay for the fallback case (D-04, no route yet):** one or more `pin-s+{hex-color}({lon},{lat})` marker overlays, comma-separated, matching the trip's existing marker convention (`trip-map.component.ts` numbers markers 1..n on a blue accent — see Claude's Discretion note below for exact color).
**Position:** use `auto` for the `{lon,lat,zoom}` segment so Mapbox auto-fits the bounding box to the overlay — mirrors `trip-map.component.ts`'s own `fitBounds(...)` behavior (`trip-map.component.ts:193-206`) rather than hand-computing a center/zoom.
**Auth:** `?access_token=...` query parameter, **not** a header [CITED: docs.mapbox.com/api/maps/static-images/].
**Response:** raw image bytes (PNG for vector-layer styles, JPEG for raster-only) [CITED: same source] — fetch with `RestClient.get().uri(...).retrieve().body(byte[].class)`.
**URL length cap:** 8,192 characters total [CITED: docs.mapbox.com/api/maps/static-images/ — "The Static Images API only accepts requests that are 8,192 or fewer characters long"]. See Common Pitfalls for what happens when a long `routeGeometry` exceeds this.
**Rate limit:** 1,250 requests/minute on the free tier [CITED: docs.mapbox.com/api/maps/static-images/], far above what a single-user PDF-export flow would ever hit — no rate limiting needed on this endpoint beyond what already protects `/optimize`.

**Visual-consistency defaults (Claude's Discretion, informed by existing frontend styling):** the trip map component uses style `mapbox://styles/mapbox/streets-v12`, route line color `#3b82f6` at width 4 (`trip-map.component.ts:94,189`). The Static Images API's `path-` overlay (if ever used instead of `geojson()`) accepts a hex stroke color — `3b82f6` would match. The `geojson()` overlay renders with the *map style's* default line paint, not a custom color, since it has no per-feature style properties applied — if exact color-matching the frontend map matters, either accept the style default or switch to the `path-` overlay (which requires polyline-encoding `routeGeometry`'s coordinates first, the tradeoff already noted in Alternatives Considered).

### Pattern 4: Native-query search extension (D-10/D-11 — stops → places join)
**What:** `TripSearchRepositoryImpl.matchingIds`/`countMatches` today only ILIKE-match `trips.title` and `unnest(trips.tags)` (`repository/TripSearchRepositoryImpl.java:57-70`). D-10 requires also matching `places.name` via `stops.place_id → places.id`.
**Schema confirmed from Flyway migrations, read this session:**
- `stops` table: `place_id BIGINT NOT NULL REFERENCES places(id)` [VERIFIED: `backend/src/main/resources/db/migration/V4__create_stops.sql:4`, quoted: `place_id   BIGINT NOT NULL REFERENCES places(id),`]
- `places` table: `name VARCHAR(200) NOT NULL` [VERIFIED: `backend/src/main/resources/db/migration/V3__create_places.sql:3`, quoted: `name              VARCHAR(200) NOT NULL,`]
- `trips` table owner/visibility columns: `user_id BIGINT NOT NULL REFERENCES users(id)`, `visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'` [VERIFIED: `backend/src/main/resources/db/migration/V2__create_trips.sql:3,7`, quoted: `user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,` and `visibility     VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',`]
- `stops.day_number` is nullable (no `NOT NULL`) [VERIFIED: `backend/src/main/resources/db/migration/V7__stop_scheduling.sql:9`, quoted: `day_number INT,` — no `NOT NULL` clause, and the migration's own header comment states "All columns are nullable ... so existing trips/stops remain valid without any backfill"]

**Extended native query (illustrative — follows the exact shape of `TripSearchRepositoryImpl.matchingIds`, `repository/TripSearchRepositoryImpl.java:57-70`):**
```java
// D-11: shared WHERE-clause builder extracted so searchPublicTrips and the new
// searchOwnedTrips differ only in scope (visibility='PUBLIC' vs user_id=:userId)
// plus the new filter params.
private static final String BASE_MATCH_SQL = """
        (t.title ILIKE :pattern
         OR EXISTS (SELECT 1 FROM unnest(t.tags) tag WHERE tag ILIKE :pattern)
         OR EXISTS (
             SELECT 1 FROM stops s JOIN places p ON p.id = s.place_id
             WHERE s.trip_id = t.id AND p.name ILIKE :pattern))
        """;
// searchOwnedTrips's native query:
//   SELECT t.id FROM trips t WHERE t.user_id = :userId AND (<BASE_MATCH_SQL>)
//     AND (:status IS NULL OR t.status = :status)
//     AND (:visibility IS NULL OR t.visibility = :visibility)
//     AND (:startDateFrom IS NULL OR t.start_date >= :startDateFrom)
//     AND (:startDateTo IS NULL OR t.start_date <= :startDateTo)
//     [AND duration filter — see below]
//   ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset
```
The `EXISTS (... JOIN places p ...)` subquery avoids row-multiplication (a trip with 3 matching stops must not produce 3 id rows) — same reasoning `unnest(t.tags)` already uses for tags in the existing query.

**Duration filter (D-14) — one viable native-SQL approach:**
```sql
AND (:durationDays IS NULL OR (
    SELECT COALESCE(MAX(s.day_number), 0) FROM stops s WHERE s.trip_id = t.id
) = :durationDays)
```
`COALESCE(..., 0)` implements the "zero-stop/never-optimized trips report 0" convention consistent with D-07 (Claude's Discretion note: planner may instead choose to exclude these trips entirely from a duration-filtered result — both are valid per CONTEXT.md, pick whichever composes more simply with the AND-chain above).

**Refetch step:** unchanged pattern — once matching ids + total count are resolved via the native query, re-fetch through a JPQL `SELECT new ... TripOwnerSummaryResponse(...) FROM Trip t WHERE t.id IN :ids` projection, exactly as `TripSearchRepositoryImpl.searchPublicTrips` already does (`repository/TripSearchRepositoryImpl.java:43-53`) and per the anti-pattern documented in `.planning/codebase/ARCHITECTURE.md:569-573` (`SELECT t FROM Trip t JOIN FETCH t.stops` + `Pageable` triggers Hibernate's in-memory pagination, HHH90003004) — never fetch-join `stops`/`places` with `Pageable` in the summary path.

### Anti-Patterns to Avoid
- **Fetch-joining `stops`/`places` with `Pageable` in any list/search query:** confirmed anti-pattern in this codebase (`.planning/codebase/ARCHITECTURE.md:569-573`) — always use the native-id-then-JPQL-projection two-step, never a single fetch-joined paginated query.
- **Trusting AI-suggested OpenPDF package names without verification:** `org.openpdf` does not exist in the actual published 2.2.2 jar — always `com.lowagie.text`.
- **Encoding `routeGeometry` into a hand-rolled polyline string:** unnecessary — the GeoJSON overlay accepts the stored string directly (see Don't Hand-Roll).
- **Putting the Mapbox access token in a default request header:** unlike `OrsClientConfig`/`GeminiClientConfig`, Mapbox Static Images requires `?access_token=` as a query parameter on every request — a header-based `MapboxClientConfig` bean (copy-pasting `OrsClientConfig` verbatim) will silently 401.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Encoding a route polyline for the map overlay | A Google/Mapbox polyline encoding algorithm | The `geojson()` overlay form, passing `Trip.routeGeometry` (already GeoJSON) directly, URL-encoded via `java.net.URLEncoder` (stdlib) | Mapbox's Static Images API natively accepts GeoJSON as an overlay [CITED: docs.mapbox.com/api/maps/static-images/] — the data is already in the right shape, no transformation algorithm needed |
| PDF table layout | Manual coordinate-based cell drawing (`PdfContentByte` positioning) | `PdfPTable` (`com.lowagie.text.pdf.PdfPTable`) | `PdfPTable` handles column widths, row wrapping, and pagination across page breaks automatically; hand-positioned cells break the moment stop notes wrap to a second line |
| Filename sanitization for the PDF download | A new sanitizer | `TripExportController.sanitizeFilename` (`controller/TripExportController.java:63-69`, package-private static, directly callable) | D-05 locks this explicitly — a second sanitizer would drift from the frontend's matching implementation in `trip-view.page.ts`, which the fixture tests (`TripExportControllerTest`) exist specifically to prevent |
| Exception→HTTP-status translation for the new Mapbox client | Ad hoc try/catch in the service layer | A new `MapboxClientException` mapped in `GlobalExceptionHandler` at 502, mirroring `OrsClientException`'s registration (`exception/GlobalExceptionHandler.java:80-84`) | Every other external client (`OrsClient`, `GeminiClient`) already funnels failures through this exact mechanism — a one-off try/catch in `PdfExportService` would be the only inconsistent error path in the codebase |

**Key insight:** every "new" piece of infrastructure this phase needs (client isolation, filename sanitization, native-query-then-JPQL-refetch, 502 exception mapping) already has exactly one canonical implementation in this codebase. The work is almost entirely "add the second instance of an existing pattern," which is also why the plan-checker/ARCHITECTURE.md anti-pattern list should be treated as load-bearing here, not advisory.

## Common Pitfalls

### Pitfall 1: Frontend `TripStatus` type is stale relative to the backend enum
**What goes wrong:** `frontend/src/app/core/models/trip.model.ts:6` declares `export type TripStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED';` — but the actual backend enum, confirmed by reading both the entity and the DB constraint, is `DRAFT | PLANNED | ACTIVE | COMPLETED` [VERIFIED: `backend/src/main/java/com/tripflow/backend/domain/enums/TripStatus.java:3-8`, quoted: `DRAFT,\n    PLANNED,\n    ACTIVE,\n    COMPLETED` — cross-confirmed by `backend/src/main/resources/db/migration/V10__add_enum_check_constraints.sql:11`, quoted: `CHECK (status IN ('DRAFT', 'PLANNED', 'ACTIVE', 'COMPLETED'))`]. `dashboard.page.ts:147-161` (`statusColor`/`statusLabel`) also branches on `'IN_PROGRESS'`, which never actually occurs.
**Why it happens:** the frontend model was written before/independently of a later backend enum change and was never updated — no test currently catches the drift since neither `'PLANNED'` nor `'ACTIVE'` is exercised by existing frontend specs.
**How to avoid:** any status filter added for SEARCH-01 (a `<select>`/segment of status options) must use the **real** four values (`DRAFT`, `PLANNED`, `ACTIVE`, `COMPLETED`), not `IN_PROGRESS`. This phase is a good place to fix `trip.model.ts:6` and `dashboard.page.ts`'s status helpers as a small drive-by correction, since SEARCH-01 already touches the status-filter surface — flag to the planner as in-scope cleanup, not a separate ticket.
**Warning signs:** a status filter dropdown that silently never matches any `ACTIVE` or `PLANNED` trip, or a backend 400 if `IN_PROGRESS` is ever sent as a `status` query param (it isn't a valid enum constant).

### Pitfall 2: Mapbox `geojson()` overlay can exceed the 8,192-character URL cap on long/complex routes
**What goes wrong:** a multi-stop, multi-day trip's ORS-derived `routeGeometry` LineString can have many coordinate pairs; stringified and URL-encoded, it can push the total request URL over Mapbox's documented 8,192-character limit [CITED: docs.mapbox.com/api/maps/static-images/], causing the snapshot request to fail with an error response instead of an image.
**Why it happens:** `routeGeometry` is stored verbatim from ORS's own GeoJSON directions response with no length cap — the field was never designed with a downstream URL-embedding consumer in mind.
**How to avoid:** treat this the same as D-04's existing "no route → fall back to marker pins" case — if the constructed request URL length exceeds a safety threshold (e.g. 8,000 chars, leaving margin for the access token), fall back to a marker-only overlay (or a no-line overlay) rather than sending a doomed request. Catch a 4xx from `MapboxClient` and degrade gracefully (log + omit the map image) rather than 502ing the whole PDF export — a missing map image is a much better failure mode than a failed download.
**Warning signs:** PDF export works for short/simple trips but fails specifically on trips with many optimized stops or long inter-stop distances.

### Pitfall 3: `stops.day_number` is a nullable boxed `Integer`, not a primitive — completion/duration math must null-check
**What goes wrong:** `Stop.dayNumber` is `Integer` (nullable, `@Column(name = "day_number")` with no `nullable = false`) [VERIFIED: `backend/src/main/java/com/tripflow/backend/domain/Stop.java:53-54`, quoted: `@Column(name = "day_number")\n    private Integer dayNumber;`]. A `MAX(dayNumber)` aggregate over stops where none have ever been optimized returns SQL `NULL`, which if not coalesced becomes a Java `null` in a duration field or throws on unboxing if mapped to a primitive `int`.
**Why it happens:** `dayNumber` is only ever set by `RouteOptimizationService`/`ItineraryScheduler` — a trip created but never optimized has every stop's `dayNumber` at its default `null` [confirmed by `V7__stop_scheduling.sql`'s own comment, quoted above under Pattern 4].
**How to avoid:** use `COALESCE(MAX(day_number), 0)` in the native duration query (see Pattern 4) and keep any Java-side DTO field for duration as a boxed `Integer`/nullable type if it's ever exposed, matching the D-07 zero-stops convention already established for completion percentage.
**Warning signs:** a `NullPointerException` on unboxing, or every never-optimized trip mysteriously vanishing from duration-filtered results if the `COALESCE` is omitted and the comparison against `NULL` silently evaluates false.

### Pitfall 4: `TripSummaryResponse` must NOT gain completion fields — a second DTO is required (D-08)
**What goes wrong:** the natural first instinct is to add `visitedStopCount`/`completionPercentage` fields to the existing `TripSummaryResponse` record. That record is shared by `findSummariesByUserId` (owner list), `findSummariesByVisibility(PUBLIC)` (discovery listing), and `searchPublicTrips` (discovery search) [VERIFIED: `backend/src/main/java/com/tripflow/backend/repository/TripRepository.java:43-66`, quoted queries above use the identical `SELECT new com.tripflow.backend.dto.TripSummaryResponse(...)` constructor shape for both `findSummariesByUserId` and `findSummariesByVisibility`]. Adding fields there leaks a stranger's trip-completion progress into the public discovery feed.
**Why it happens:** it's the path of least resistance — one record, one place to add fields — but D-08 explicitly forbids it for a privacy reason already discussed and locked.
**How to avoid:** create a new `TripOwnerSummaryResponse` record (Claude's Discretion naming) used only by `findSummariesByUserId` and the new `searchOwnedTrips`; leave `TripSummaryResponse` byte-for-byte unchanged so `findSummariesByVisibility`/`searchPublicTrips` compile against it with zero changes.
**Warning signs:** a compile error or test failure in `TripSearchRepositoryIT`/discovery-feed tests if `TripSummaryResponse`'s constructor signature changes — those tests are the tripwire.

## Code Examples

### PDF export controller endpoint (extends TripExportController, mirrors exportIcs)
```java
// Source: pattern read from controller/TripExportController.java:34-47, adapted
@Operation(summary = "Export a trip as a formatted PDF itinerary")
@GetMapping(value = "/{id}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
public ResponseEntity<byte[]> exportPdf(
        @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
    PdfExportService.PdfExport export = pdfExportService.exportPdf(id, principal.userId());

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + sanitizeFilename(export.tripTitle()) + ".pdf\"")
            .body(export.pdfBytes());
}
```

### Frontend export trigger (extends TripService, mirrors exportIcs)
```typescript
// Source: pattern read from frontend/src/app/core/services/trip.service.ts:103-111, adapted
exportPdf(tripId: number): Observable<Blob> {
  return this.http
    .get(`${this.baseUrl}/${tripId}/export/pdf`, { responseType: 'blob' })
    .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| iText 4/5 for PDF generation | OpenPDF (LGPL/MPL fork of iText 4, community-maintained) | Ongoing since iText's 5.x AGPL relicensing (~2011) | Same API family (`com.lowagie.text.*`), permissively licensed, actively maintained — the right choice for a project that can't take an AGPL/commercial dependency, per D-02 |

**Deprecated/outdated:** none directly relevant — this phase's tech choices (OpenPDF 2.x, Mapbox Static Images v1) are current, non-deprecated APIs as of this research date.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | `RestClient.retrieve().body(byte[].class)` correctly deserializes a raw binary (image) response via Spring's built-in `ByteArrayHttpMessageConverter` | Standard Stack / Supporting | Low — this is standard, widely-documented Spring Framework behavior, but has not been exercised anywhere in this codebase yet; if it fails, the fix is a one-line `Accept: image/*` header addition or an explicit converter registration, not an architecture change |
| A2 | OpenPDF's `PdfPTable`/`Document`/`Image`/`Paragraph` method-level API (not just class/package names) behaves as shown in the Code Examples section | Architecture Patterns / Pattern 2 | Low-Medium — class and package names are jar-verified [VERIFIED], but exact method signatures (e.g. `Image.getInstance(byte[])`) were not independently re-verified against the jar's method table this session; if a signature differs, it will fail at compile time (cheap, fast feedback), not silently at runtime |
| A3 | The Static Images API's `geojson()` overlay renders using the map style's default line paint (no custom color) rather than a configurable stroke color | Architecture Patterns / Pattern 3 | Low — cosmetic only; if wrong, the map snapshot's route line just won't match `#3b82f6` exactly, no functional impact on EXPORT-02 |

## Open Questions

1. **Exact Mapbox style/username to use in the Static Images URL path.**
   - What we know: the frontend uses `mapbox://styles/mapbox/streets-v12` (`trip-map.component.ts:94`), a Mapbox-hosted default style under the `mapbox` username.
   - What's unclear: whether the Static Images URL should target `mapbox/streets-v12` (same style, matches frontend visually) or a project-specific custom style if one exists — no custom Mapbox style was found referenced anywhere in the backend or frontend.
   - Recommendation: use `mapbox/streets-v12` for visual parity with the in-app map; this is Claude's Discretion territory per CONTEXT.md, not a blocking decision.

2. **Whether the duration filter excludes zero-stop/never-optimized trips or reports `durationDays: 0`.**
   - What we know: CONTEXT.md explicitly defers this to the planner (Claude's Discretion), suggesting consistency with D-07's "report 0, don't null" convention.
   - What's unclear: which choice composes more simply with the AND-chain filter query — this is a genuine implementation-detail toss-up, not a research gap.
   - Recommendation: default to the `COALESCE(..., 0)` approach shown in Pattern 4 (simpler, one fewer conditional branch, consistent with D-07) unless the planner finds a strong reason otherwise.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven Central network access (dependency resolution) | OpenPDF install | ✓ (verified this session via direct `curl`/jar download) | — | — |
| Mapbox account + API access token with Static Images API enabled | EXPORT-02 map snapshot (D-03) | ✗ — not present in `backend/.env.example` today (file access denied this session per CLAUDE.md's `backend/.env*` permission restriction, but no `MAPBOX_TOKEN`/similar reference was found anywhere in `application.properties`, `client/`, or `docs/`) | — | None viable within EXPORT-02's scope — this is a new secret that must be provisioned (Render dashboard + local `.env`) before the PDF map-snapshot feature can function; D-04's "fall back to plain stop pins" only covers the no-route case, not a missing token. Flag as a `checkpoint:human-verify` / environment-setup task in the plan. |
| Existing Mapbox **frontend** token (`MAPBOX_TOKEN` GitHub secret, `mapboxToken` in `environment.local.ts`) | Frontend map rendering only | ✓ (`docs/ci.md:27` confirms a `frontend-ci.yml` step verifies `secrets.MAPBOX_TOKEN` is set) | — | This is a **separate** token from the new backend one D-03 requires — the frontend token is a public/client-side Mapbox token scoped for browser use; do not reuse it server-side without checking Mapbox's token-scoping rules for server-side Static Images calls (a browser-restricted token, if URL-restricted, would reject backend requests). Treat the backend token as genuinely new provisioning, not a reuse of the existing secret. |

**Missing dependencies with no fallback:**
- A backend-scoped Mapbox access token (`MAPBOX_TOKEN` or similar new env var, per D-03) must be provisioned before EXPORT-02's map-snapshot feature works end-to-end. This blocks only the map-image portion of the PDF — the header/stops/notes portion of EXPORT-02 has no such dependency.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito (backend unit), JUnit 5 + Testcontainers-Postgres (backend `*IT`), Karma + Jasmine (frontend) |
| Config file | `backend/pom.xml` (Surefire excludes `*IT.java`, Failsafe includes it under `-Pci`); `frontend/karma.conf.js` |
| Quick run command | `./mvnw verify` (backend, unit only, no Docker) / `npm run test:ci` (frontend) |
| Full suite command | `./mvnw verify -Pci` (backend, requires Docker/Testcontainers — CI-only per CLAUDE.md, "no team machine runs Docker") |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EXPORT-02 | `GET /{id}/export/pdf` returns a valid PDF, 404 on private/non-owned trip | unit + `*IT` | `./mvnw test -Dtest=PdfExportServiceTest` / `TripExportControllerIT` | ❌ Wave 0 — new files, mirror `IcsExportServiceTest`/`TripExportControllerIT` |
| EXPORT-02 | `sanitizeFilename` reuse produces identical output to `.ics` export for the same title | unit | `./mvnw test -Dtest=TripExportControllerTest` | ✅ existing file, extend with a PDF-filename assertion if the extension differs the logic under test doesn't — likely no new test needed since it's the same static method |
| EXPORT-02 | Mapbox client translates a 4xx/timeout into `MapboxClientException` → 502, and a too-long URL degrades to marker-only rather than failing | unit | `./mvnw test -Dtest=MapboxClientTest` | ❌ Wave 0 — mirror the `execute()` translation test pattern implied by `OrsClient.java:77-87` (no existing `OrsClientTest` was located in this pass; check for one and mirror it, or write a Mockito-based `RestClient` stub test) |
| EXPORT-03 | `completionPercentage`/`visitedStopCount` computed correctly, including the zero-stops = 0 case | unit | `./mvnw test -Dtest=TripMapperTest` | ✅ existing file (`mapper/TripMapperTest.java`), extend |
| SEARCH-01 | `searchOwnedTrips` matches title, tags, **and** place names; AND-combines all filters; excludes other users' trips | `*IT` (real Postgres, native query) | `./mvnw verify -Pci -Dit.test=TripSearchRepositoryIT` | ✅ existing file (`repository/TripSearchRepositoryIT.java`), extend with new test methods for the place-name and filter cases — mirror the existing five test methods' structure exactly |
| SEARCH-01 | `GET /api/trips?search=&status=&...` end-to-end, paged-response shape | `*IT` | `./mvnw verify -Pci -Dit.test=TripControllerIT` | ✅ existing file, extend |

### Sampling Rate
- **Per task commit:** `./mvnw verify` (backend unit) / `npm run test:ci` (frontend) — no Docker required, fast feedback
- **Per wave merge:** `./mvnw verify -Pci` (full suite including `*IT`) — CI-only per CLAUDE.md, cannot be run locally; treat CI as the actual gate for this phase's `*IT` tests
- **Phase gate:** Full suite green (CI) before `/gsd-verify-work`, plus the 92%/80% coverage floor (`docs/ci.md`)

### Wave 0 Gaps
- [ ] `backend/src/test/java/com/tripflow/backend/service/PdfExportServiceTest.java` — covers EXPORT-02 unit behavior
- [ ] `backend/src/test/java/com/tripflow/backend/controller/TripExportControllerIT.java` extension — covers EXPORT-02 end-to-end (an existing `TripExportControllerIT` was found — check whether it already covers `.ics` only and needs a new PDF test class or just new test methods)
- [ ] `backend/src/test/java/com/tripflow/backend/client/mapbox/MapboxClientTest.java` — covers the new client's error translation and URL-length fallback
- [ ] `openpdf` dependency addition itself is a Wave 0 prerequisite (`pom.xml` edit) before any PDF test can compile

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | no (new endpoints reuse existing `@AuthenticationPrincipal UserPrincipal` — no new auth mechanism) | existing JWT filter, unchanged |
| V3 Session Management | no | unchanged |
| V4 Access Control | yes | new PDF export endpoint must delegate to `TripService.getTrip`'s existing owner-or-PUBLIC visibility check (same as `.ics` export) — do not reimplement; new search endpoint must scope to `principal.userId()` only (D-09), never accept an arbitrary user id |
| V5 Input Validation | yes | new query params (`search`, `status`, `visibility`, `startDateFrom/To`, `durationDays`) must validate against the real enum constants (see Pitfall 1) and reject malformed dates the same way existing `@RequestParam`/`Pageable` binding already does (400, not 500) |
| V6 Cryptography | no | no new cryptographic material introduced |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|-----------------------|
| SQL injection via search query / filter params in the new native query | Tampering | Continue the existing pattern of `.setParameter(...)` bind variables on `createNativeQuery(...)` (already used throughout `TripSearchRepositoryImpl` — no string concatenation of user input into SQL, confirmed by reading the file in full this session) |
| Existence/enumeration leak via search results across owner boundary | Information Disclosure | D-09 already scopes `searchOwnedTrips` to `t.user_id = :userId` in the native query itself (not a post-filter in Java) — never expose another user's trip via the search endpoint, matching the same "404 not 403" existence-hiding philosophy already used elsewhere in `TripService` |
| SSRF-adjacent: server-side fetch to Mapbox using data partially derived from user-controlled trip content (`routeGeometry`) | Tampering / (limited) SSRF | `routeGeometry` is server-generated (from `RouteOptimizationService`'s ORS call), not directly user-supplied, and the Mapbox base URL/host is fixed via `MapboxProperties.baseUrl` (not user-controllable) — no meaningful SSRF surface, but do not let any future change make the Mapbox request URL/host configurable from request input |
| New backend secret (`MAPBOX_TOKEN`) leak via logs | Information Disclosure | Mask in `toString()` exactly like `OrsProperties`/`GeminiProperties` already do (`SecretMask.mask(...)`) — confirmed pattern, apply identically to `MapboxProperties` |

## Sources

### Primary (HIGH confidence)
- `backend/src/main/java/com/tripflow/backend/**` — read directly this session (Trip.java, Stop.java, TripStatus/TripVisibility/StopStatus enums, TripRepository.java, TripSearchRepositoryImpl.java, TripSearchRepository.java, TripSummaryResponse.java, TripResponse.java, StopResponse.java, TripService.java, TripController.java, TripExportController.java, IcsExportService.java, TripOwnershipService.java, client/ors/*, client/gemini/GeminiClientConfig.java, exception/GlobalExceptionHandler.java, exception/OrsClientException.java)
- `backend/src/main/resources/db/migration/V2,V3,V4,V7,V10*.sql` — read directly this session, quoted verbatim for schema/enum claims
- `backend/pom.xml` — read directly this session (Spring Boot 4.1.0 parent, Java 21, biweekly, springdoc, no PDF lib present)
- `frontend/src/app/core/services/trip.service.ts`, `frontend/src/app/core/models/trip.model.ts`, `frontend/src/app/pages/trips/dashboard/dashboard.page.ts`, `frontend/src/app/pages/trips/components/trip-map/trip-map.component.ts` — read directly this session
- `docs/api-contracts.md`, `.planning/codebase/ARCHITECTURE.md` (anti-patterns section) — read directly this session
- Maven Central solrsearch API (`search.maven.org/solrsearch/select`) and downloaded `openpdf-2.2.2.jar` — queried/inspected directly this session

### Secondary (MEDIUM confidence)
- `https://docs.mapbox.com/api/maps/static-images/` — fetched this session, used for URL format, overlay syntax, GeoJSON overlay, URL length limit, rate limit
- `https://github.com/LibrePDF/OpenPDF` (README) — fetched this session; its version-number/package-name claims were **contradicted** by the direct jar inspection above and should not be trusted for those specific facts

### Tertiary (LOW confidence)
- Initial WebSearch results claiming OpenPDF "3.0.5" / `org.openpdf` package — corrected by direct verification, retained here only as a documented example of what NOT to trust

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH for OpenPDF version/package (jar-verified this session); MEDIUM for Mapbox API request shape (docs-verified but not exercised against a live account)
- Architecture: HIGH — every pattern cited traces to a real file read this session, no paraphrase
- Pitfalls: HIGH for the frontend `TripStatus` drift and DB nullability findings (both source-verified); MEDIUM for the Mapbox URL-length pitfall (documented limit, but actual `routeGeometry` sizes in this project's data were not measured)

**Research date:** 2026-08-21
**Valid until:** 30 days (stable domain — Spring Boot/OpenPDF/Mapbox APIs are not fast-moving; re-verify the OpenPDF version if this phase is planned significantly later, since a real 3.x release may land)
