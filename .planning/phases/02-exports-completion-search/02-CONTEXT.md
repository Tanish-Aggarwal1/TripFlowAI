# Phase 2: Exports, Completion & Search - Context

**Gathered:** 2026-08-20
**Status:** Ready for planning

<domain>
## Phase Boundary

PDF trip export, trip completion percentage, and search/filter on the owner's own trip list (`GET /api/trips`). EXPORT-02, EXPORT-03, SEARCH-01. 02-01 (.ics export) is already done and untouched by this phase. No discovery-feed/public-trip-browsing work — that's Phase 6 (`GET /api/discovery/**`), only touched here where a shared DTO forces a boundary decision (see D-08).

</domain>

<decisions>
## Implementation Decisions

### PDF export (EXPORT-02)
- **D-01:** PDF content = header + ordered stops + notes **+ a route map snapshot** image embedded in the document.
- **D-02:** PDF library: OpenPDF (LGPL/MPL fork of iText 4) — new pom.xml dependency, no PDF lib exists today.
- **D-03:** Map snapshot is rendered server-side via the Mapbox Static Images API, authenticated with a **new backend env var** (e.g. `MAPBOX_TOKEN` or equivalent) — mirrors the existing `client/{service}/` pattern (`OrsProperties`, `GeminiProperties`) rather than having the frontend render and upload an image. — **Reversibility:** costly — once shipped, switching to a client-rendered-image approach changes the request contract and removes a documented backend secret.
- **D-04:** Map source: use `Trip.routeGeometry` (optimized route) when present, combined with stop pin markers on top (z-ordered last, per Mapbox's overlay ordering); fall back to plain stop pins (no line) for trips never optimized. *(Revised 2026-08-22 during UAT: originally route-line-only for optimized trips — user found that visually confusing without the pins and asked for both together; Mapbox's Static Images API supports comma-separated combined overlays.)*
- **D-05:** PDF filename uses the exact same `sanitizeFilename` convention already established for `.ics` (`TripExportController.sanitizeFilename` + its documented frontend duplicate in `trip-view.page.ts`) — same character-set/length rules, same dual-implementation-with-shared-fixture-tests pattern.

### Trip completion percentage (EXPORT-03)
- **D-06:** Only `StopStatus.VISITED` counts toward completion. `SKIPPED` stays in the denominator (total stop count) but not the numerator. `completionPercentage = visitedStopCount / stopCount`.
- **D-07:** Zero-stop trips: `completionPercentage = 0` (not null). No null-handling needed downstream.
- **D-08:** `visitedStopCount`/`completionPercentage` are exposed on `TripResponse` (detail view) **and** on a richer summary DTO for the owner's own trip list — but **NOT** on the DTO the public discovery feed uses. `TripSummaryResponse` today is shared by `TripRepository.findSummariesByUserId` (owner list — Phase 2 target), `TripRepository.findSummariesByVisibility(PUBLIC)` (discovery listing), and `TripSearchRepositoryImpl.searchPublicTrips` (discovery search) — the same record backs all three. Adding completion fields to it as-is would leak a stranger's progress on their PUBLIC trip into the discovery feed. **Split into two DTOs**: `findSummariesByUserId` (and the new owner-search method, D-10) gets the richer one; `findSummariesByVisibility(PUBLIC)` and `searchPublicTrips` keep the existing lean `TripSummaryResponse` unchanged. — **Reversibility:** one-way — once the public discovery feed's DTO is locked without completion data, exposing it later is a contract change reviewers (Neel, per CLAUDE.md serialize-point rule) would need to sign off on.

### Search & filter (SEARCH-01)
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` — EXPORT-02, EXPORT-03, SEARCH-01
- `.planning/ROADMAP.md` Phase 2 section — success criteria, plan breakdown (02-01 done, 02-02/03/04 in scope)
- `docs/TripFlow_fall_Break_Plan.md` — FB-05/06/07 source task breakdown

### API & architecture conventions
- `docs/api-contracts.md` — canonical `ApiError` shape, paged-response convention (REF-21/SCRUM-110) that SEARCH-01 must follow
- `README.md` "Architecture rationale" — layered backend convention (controller/service/repository/domain), applies to new PDF export and search code

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/src/main/java/com/tripflow/backend/controller/TripExportController.java` — existing `.ics` export endpoint; its own javadoc already anticipates PDF export growing here ("PDF export is planned next"). New `GET /api/trips/{id}/export/pdf` belongs alongside `exportIcs`.
- `backend/src/main/java/com/tripflow/backend/controller/TripExportController.java:sanitizeFilename` — package-private static method, directly reusable for PDF filenames (D-05). Frontend counterpart in `trip-view.page.ts`.
- `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java` — the exact pattern to extend for D-11: native `ILIKE`/`unnest(tags)` query for id-matching, then re-fetch via the same JPQL projection every other trip-list endpoint uses. Its own doc comment explicitly states the row-shape-in-one-place principle this phase's D-11/D-08 decisions continue.
- `backend/src/main/java/com/tripflow/backend/repository/TripRepository.java` (`findSummariesByUserId`, `findSummariesByVisibility`) — existing summary-projection queries; `findSummariesByUserId` is the one that gains the richer DTO (D-08).
- `backend/src/main/java/com/tripflow/backend/dto/TripSummaryResponse.java` — current shared record (id, title, visibility, status, createdAt, updatedAt, stopCount, coverPhotoUrl). Stays unchanged for discovery; a new sibling record carries the owner-list additions (D-08).
- `backend/src/main/java/com/tripflow/backend/client/{ors,gemini}/` — the established client-isolation pattern (own `*Properties` record, per-client timeouts) to follow for the new Mapbox Static Images API call (D-03).
- `Stop.dayNumber`/`Stop.status` (`StopStatus`: PLANNED/VISITED/SKIPPED) — backs both completion % (D-06) and duration filter (D-14).
- `Trip.routeGeometry` — already-stored optimized route, reused for the PDF map snapshot (D-04).

### Established Patterns
- Controllers stay thin (`TripController`, `TripExportController`); business logic and query construction live in service/repository layers — new PDF generation should get its own service (e.g. `PdfExportService`, mirroring `IcsExportService`), not live in the controller.
- External integrations get their own `client/{service}/` module with masked-secret `toString()` and independent timeouts (see `docs/api-contracts.md` / `INTEGRATIONS.md`) — applies to the new Mapbox Static Images call.
- Summary/list projections never fetch-join collections with `Pageable` (REF-21 audit finding, documented in `TripSummaryResponse`'s own javadoc and `ARCHITECTURE.md`'s anti-patterns section) — any new owner-search query must follow the same projection-not-fetch-join approach.

### Integration Points
- `TripController.listTrips()` (`GET /api/trips`) is the endpoint SEARCH-01 extends with new query params (search, status, visibility, startDateFrom/To, durationDays) — currently takes only `Pageable`.
- `TripExportController` gains a new `GET /{id}/export/pdf` sibling to `exportIcs`.
- `TripResponse` and the new owner-list summary DTO both gain completion fields; the untouched `TripSummaryResponse` stays exactly as-is for discovery.

</code_context>

<specifics>
## Specific Ideas

None beyond what's captured in Decisions — no specific PDF layout mockup or copy was given; standard header/list/notes layout expected.

</specifics>

<deferred>
## Deferred Ideas

- **Public discovery feed should not expose future `startDate` for other users' trips.** Raised during this discussion but is a Phase 6 (discovery feed / `DiscoveryController`) concern — Phase 2 never touches that controller or its response shape. Flag for Phase 6 discussion: whether `startDate` (or any date field) on a PUBLIC trip should be hidden/redacted when in the future, for privacy reasons.

</deferred>

---

*Phase: 2-Exports, Completion & Search*
*Context gathered: 2026-08-20*
