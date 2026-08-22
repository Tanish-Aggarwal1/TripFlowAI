---
phase: 02-exports-completion-search
verified: 2026-08-21T22:15:00Z
status: passed
score: 4/4 roadmap success criteria verified (25/29 plan-level truths directly verified; 4 require CI's Testcontainers run to close)
behavior_unverified: 0
overrides_applied: 0
human_verification:

  - test: "Push branch `docs/SCRUM-478-phase-2-planning-docs` (16 commits ahead of origin, unpushed) and let CI's `mvn -B verify -Pci` run the Testcontainers `*IT` suite: `TripExportControllerIT` (PDF 200/404), `TripRepositoryIT` (visited-count correlated subquery + `TripSummaryResponse` 8-component D-08 tripwire), `TripSearchRepositoryIT` (16 `searchOwnedTrips_*` methods — exactly-once matching, filter intersection, ordering stability, owner scoping), `TripControllerIT` (paged envelope + malformed-status 400)."
    expected: "All `*IT` methods pass against real Postgres, matching what the unit-level mocks and code inspection already indicate."
    why_human: "No Docker daemon on this machine (`docker info` reaches the client but the server pipe `dockerDesktopLinuxEngine` is not running) — matches CLAUDE.md's documented team-wide constraint. `*IT` files compile clean under `-Pci` (confirmed) but have never actually executed against Postgres for this phase's code, and the branch has not been pushed, so CI has not run on it either. This is the only mechanism that can prove the native-SQL correctness (EXISTS-based dedup, COALESCE duration-zero, ordering tiebreak, correlated-subquery counts) that unit tests cannot reach."

  - test: "Once `MAPBOX_TOKEN` is provisioned (Render + local `.env`), export the PDF of a trip that has been route-optimized and open it to confirm the embedded map snapshot is legible and matches the app's route line visually."
    expected: "A rendered, non-garbled map image with the correct route/pins."
    why_human: "Cannot provision a real Mapbox token or render/inspect a PDF image in this session; explicitly called out as outstanding in `02-02-SUMMARY.md`."
---

# Phase 2: Exports, Completion & Search Verification Report

**Phase Goal:** Users can export their itinerary, see trip completion progress, and search/filter their trip list
**Verified:** 2026-08-21
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A trip's stops export as a valid `.ics` file that imports cleanly into a major calendar app (EXPORT-01) | ✓ VERIFIED | Pre-GSD, unmodified by this phase. `GET /api/trips/{id}/calendar.ics` still present in `TripExportController.java:39-40` calling `IcsExportService.exportIcs`; frontend `exportToCalendar()` still wired in `trip-view.page.ts:292`. Matches ROADMAP.md's own "confirmed done" note. |
| 2 | A trip exports as a formatted PDF with header, ordered stops, and notes (EXPORT-02) | ✓ VERIFIED | `PdfExportService.java` builds header (title/startDate/description), a `PdfPTable` of stops sorted by `stopOrder`, notes column, null-safe on `notes`/`dayNumber`/`plannedTime`/`description`. `PdfExportServiceTest` 12/12 pass (ran locally), including magic-number, null-tolerance, zero-stop, and Mapbox-throws cases. `TripExportController.exportPdf` wired at `GET /api/trips/{id}/export/pdf`, frontend `exportToPdf()` wired in `trip-view.page.ts:309` and `trip.service.ts:139-141`. |
| 3 | Trip responses expose enough data to compute completion percentage without a divide-by-zero on empty trips (EXPORT-03) | ✓ VERIFIED | `TripCompletion.percentage()` returns `0.0` when `stopCount == 0` (`TripCompletion.java:19`); `TripCompletionTest` 6/6 pass. Both `TripOwnerSummaryResponse.completionPercentage()` and `TripResponse.completionPercentage()` route through it. `TripMapperTest` 7/7 pass covering the 3/5-VISITED, all-SKIPPED, all-VISITED, and zero-stop cases. |
| 4 | Users can search and filter their trip list using the shared paged-response convention (SEARCH-01) | ✓ VERIFIED | `GET /api/trips` gained `search`/`status`/`visibility`/`startDateFrom`/`startDateTo`/`durationDays` params (`TripController.java:69-85`), routed to `TripSearchRepositoryImpl.searchOwnedTrips` or the plain `listTrips` path, both returning `PagedModel<TripOwnerSummaryResponse>` — envelope unchanged. `TripServiceTest` 24/24 pass (null/blank/whitespace/populated search normalization, wildcard-escaping). |

**Score:** 4/4 roadmap success criteria verified.

### Plan-Level Truths (selected, security- and edge-case-bearing)

| # | Truth (plan) | Status | Evidence |
|---|---|---|---|
| 1 | Non-owner GET of a PRIVATE trip's PDF returns 404, not 403, identical to `.ics`/`GET /api/trips/{id}` (02-02) | ✓ VERIFIED (unit) / pending IT | `PdfExportService.exportPdf`'s first statement is `tripService.getTrip(tripId, requesterId)` — same delegation `IcsExportService` already uses; `PdfExportServiceTest` asserts the delegation call. `TripExportControllerIT` has the actual 404 case but needs the Testcontainers run (see human_verification). |
| 2 | A Mapbox failure/blank token/oversized URL never fails the PDF download (02-02, D-04) | ✓ VERIFIED | `PdfExportService.addMapSnapshot` catches `MapboxClientException` and swallows malformed-image `IOException`/`BadElementException`, logging and returning without adding the image. `MapboxClient.staticSnapshot` short-circuits on blank token / no data, and downgrades geojson→marker→empty on URL-length overrun. `PdfExportServiceTest#exportPdf_mapboxClientThrows_stillYieldsPdfBytes` and `MapboxClientTest` (8/8) both pass. |
| 3 | The Mapbox token never appears in a log line, exception message, or PDF (02-02 prohibition) | ✓ VERIFIED | `MapboxProperties.toString()` masks the token (mirrors `OrsProperties`); WARN logs on URL-downgrade log `stopCount`, never the URI (`MapboxClient.java:66-67,74-75`); `MapboxClientTest` asserts `toString()` excludes the constructed token. |
| 4 | WR-02 fix: request URI is singly-, not doubly-, encoded through `RestClient` | ✓ VERIFIED | `MapboxClient.staticSnapshot` builds a `java.net.URI` via `URI.create(...)` and calls `.uri(uri)` (object, not template string), bypassing `UriBuilderFactory`'s re-encoding pass. New test `MapboxClientTest#staticSnapshot_withRouteGeometry_requestUriIsSinglyEncodedNotDoubleEncoded` exact-matches the outgoing URI — present and passing. |
| 5 | `TripSummaryResponse` (public discovery feed) stays byte-for-byte unchanged — D-08 (02-03) | ✓ VERIFIED | Read `TripSummaryResponse.java` directly: still exactly 8 components (`id,title,visibility,status,createdAt,updatedAt,stopCount,coverPhotoUrl`). `findSummariesByVisibility` and `searchPublicTrips` still target it exclusively; only `findSummariesByUserId`/`searchOwnedTrips` fork to `TripOwnerSummaryResponse`. |
| 6 | EDGE: a trip whose every stop is SKIPPED reports `completionPercentage` 0 with a non-zero stop count (D-06) | ✓ VERIFIED | `TripMapperTest` and `TripCompletionTest` both include an all-SKIPPED case asserting `0.0` with non-zero denominator; passed locally. |
| 7 | WR-01 fix: `ionViewWillEnter` reload no longer silently drops active filters | ✓ VERIFIED | `dashboard.page.ts:134-138` `loadTrips()` now calls `listTrips(0, 20, this.filters)`. Regression spec `dashboard.page.spec.ts:111` ("loadTrips passes the active filters...") present and passing (355/355 full frontend suite green). |
| 8 | WR-03 fix: `%`/`_`/`\` in search text are escaped before becoming an `ILIKE` pattern | ✓ VERIFIED | `TripSearchRepositoryImpl.likePattern()` escapes all three, `TEXT_MATCH_SQL`'s three `ILIKE` clauses use `ESCAPE '\'`. `TripServiceTest#searchOwnedTrips_searchContainingWildcardChars_escapesThemInThePattern` passes. |
| 9 | Every value in the owner search/filter native queries is bound, never concatenated (D-09 prohibition) | ✓ VERIFIED | Read `TripSearchRepositoryImpl.java` directly: `bindOwnedParameters` binds `userId`, `pattern`, `status`, `visibility`, both dates, `durationDays` via `.setParameter(...)` in both the id and count queries; query text is a fixed string literal, never assembled conditionally. |
| 10 | Owner scope (`t.user_id = :userId`) sits in the WHERE clause of both the id and count queries — a stranger's trip is never returned or counted (D-09) | ✓ VERIFIED (code) / pending IT | Confirmed present in both `matchingOwnedIds` and `countOwnedMatches`, sourced from `principal.userId()` in the controller, never request input. Real-DB proof (`TripSearchRepositoryIT#searchOwnedTrips_anotherUsersPublicTripMatching_returnsNothing`) needs the Testcontainers run. |
| 11 | Place-name/tag match uses `EXISTS`, not a top-level join, so a trip with 3 matching stops appears exactly once | ✓ VERIFIED (code) / pending IT | `TEXT_MATCH_SQL` uses `EXISTS (SELECT 1 FROM stops s JOIN places p ...)` and `EXISTS (SELECT 1 FROM unnest(t.tags) tag ...)` — no top-level join in the outer query. Dedup behavior itself needs `TripSearchRepositoryIT`'s adjacency cases run against real Postgres. |
| 12 | Duration filter: a zero-stop/never-optimized trip reports duration 0 via `COALESCE(MAX(day_number), 0)`, not silently excluded (D-14) | ✓ VERIFIED (code) / pending IT | Present verbatim in both `matchingOwnedIds` and `countOwnedMatches`. Actual SQL-NULL-vs-COALESCE behavior needs the IT run to close. |
| 13 | Frontend `TripStatus` corrected to `DRAFT/PLANNED/ACTIVE/COMPLETED`, no stale `IN_PROGRESS` | ✓ VERIFIED | `grep -c "'ACTIVE'"`/`'PLANNED'"` present in `trip.model.ts`; `IN_PROGRESS` absent from both `trip.model.ts` and `dashboard.page.ts`. `dashboard.page.spec.ts` statusLabel/statusColor specs cover all four, passing. |
| 14 | `docs/api-contracts.md` documents PDF endpoint, completion fields, and all new search/filter params as shipped | ✓ VERIFIED | Read the file directly: `GET /api/trips/{id}/export/pdf` section present (line 537), `completionPercentage` documented on both list and detail responses, `startDateFrom`/`durationDays` documented, discovery-feed asymmetry explicitly called out. |

**Note:** the remaining ~15 plan truths not itemized above (mostly parameterized `<behavior>` bullets already covered by the passing unit-test runs listed in "Behavioral Spot-Checks" below) were spot-checked by re-running their owning test classes rather than individually tabulated.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/.../service/PdfExportService.java` | header + stops table + notes + map | ✓ VERIFIED | Exists, substantive, wired into `TripExportController`, unit-tested (12/12). |
| `backend/.../client/mapbox/{MapboxClient,MapboxClientConfig,MapboxProperties}.java` | Mapbox Static Images client triple | ✓ VERIFIED | Exist, mirror `client/ors/` shape, wired into `PdfExportService`, unit-tested (8/8). |
| `backend/.../exception/MapboxClientException.java` | 502 translation | ✓ VERIFIED | Exists; `GlobalExceptionHandler` has `@ExceptionHandler(MapboxClientException.class)` returning `BAD_GATEWAY` (confirmed by grep in review + SUMMARY). |
| `backend/.../dto/TripCompletion.java` | shared percentage helper | ✓ VERIFIED | Exists, final class, private constructor, one static method; unit-tested (6/6). |
| `backend/.../dto/TripOwnerSummaryResponse.java` | owner-only projection | ✓ VERIFIED | Exists, 9 components, `@JsonProperty` derived accessor present. |
| `backend/.../dto/TripSearchFilters.java` | filter record | ✓ VERIFIED | Exists (`TripSearchRepositoryImpl` imports and consumes it in `bindOwnedParameters`). |
| `backend/.../repository/TripSearchRepositoryImpl.java` (extended) | `searchOwnedTrips` + shared `TEXT_MATCH_SQL` | ✓ VERIFIED | Read directly — shared fragment used by both public and owned queries, filter chain present, fully parameterized. |
| `backend/.../test/.../TripCompletionTest.java`, `MapboxClientTest.java`, `PdfExportServiceTest.java` | unit coverage | ✓ VERIFIED | All exist and pass (ran locally: 6, 8, 12 respectively). |
| `backend/.../test/.../TripRepositoryIT.java`, `TripSearchRepositoryIT.java`, `TripControllerIT.java`, `TripExportControllerIT.java` (extended) | real-Postgres coverage | ⚠️ COMPILES, NOT EXECUTED | `mvnw test-compile -Pci` succeeds; no Docker daemon locally (`docker info` server connection fails) and branch not yet pushed to trigger CI. See human_verification. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `PdfExportService.exportPdf` | `TripService.getTrip` | first statement, no reimplemented ownership check | ✓ WIRED | Confirmed at `PdfExportService.java:50`. |
| `TripExportController.exportPdf` | `sanitizeFilename` (existing static) | `Content-Disposition` header | ✓ WIRED | Same package-private static reused, per D-05 (confirmed present in controller). |
| `PdfExportService` | `MapboxClient` | try/catch degrading to map-less PDF | ✓ WIRED | `addMapSnapshot` catches `MapboxClientException` + `IOException`/`BadElementException`. |
| `TripRepository.findSummariesByUserId` | `TripOwnerSummaryResponse` | JPQL `SELECT new ...` | ✓ WIRED | Confirmed at `TripRepository.java:50-57`; `findSummariesByVisibility` still targets `TripSummaryResponse` (line 65-73), untouched. |
| `TripOwnerSummaryResponse`/`TripResponse` | `TripCompletion.percentage` | derived `@JsonProperty` accessor | ✓ WIRED | Both DTOs call the shared helper — confirmed by direct read. |
| `TripController.listTrips` | `TripService.searchOwnedTrips` / `listTrips` | presence of `search`/filter params | ✓ WIRED | Branching logic present at `TripController.java:78-85`. |
| dashboard search input | debounced RxJS stream | `TripService.listTrips` | ✓ WIRED | `dashboard.page.ts` `filterChanges` Subject → `debounceTime`/`distinctUntilChanged`/`switchMap` → `listTrips(0,20,filters)`; frontend specs pass. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend unit suite for this phase's new classes | `./mvnw test -Dtest=PdfExportServiceTest,MapboxClientTest,TripCompletionTest,TripMapperTest,TripServiceTest` | 57/57 pass | ✓ PASS |
| Full backend unit suite (no Docker) | `./mvnw verify` | 309/309 tests, BUILD SUCCESS | ✓ PASS |
| `*IT` files compile under CI profile | `./mvnw test-compile -Pci` | BUILD SUCCESS | ✓ PASS (existence proof only — not executed) |
| Full frontend suite | `npm run test:ci` | 355/355 pass, 94.4% statement coverage | ✓ PASS |
| Docker availability for Testcontainers | `docker info` | client reachable, server pipe `dockerDesktopLinuxEngine` not found | ✗ UNAVAILABLE — routes IT execution to CI (human_verification) |
| Branch push/CI status | `git status`, `gh run list` | 16 commits ahead of `origin/docs/SCRUM-478-phase-2-planning-docs`, unpushed; no CI run exists for this phase's commits | ? SKIP — not yet pushed |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| EXPORT-01 | (none — pre-GSD) | `.ics` export | ✓ SATISFIED | Confirmed present, unmodified by this phase. |
| EXPORT-02 | 02-02-PLAN.md | PDF export w/ map snapshot | ✓ SATISFIED | Full endpoint, service, client triple, tests present and green. |
| EXPORT-03 | 02-03-PLAN.md | completion percentage | ✓ SATISFIED | Shared helper, both DTOs, D-08 boundary intact, tests green. |
| SEARCH-01 | 02-04-PLAN.md | search/filter | ✓ SATISFIED | Full param set, owner-scoped, parameterized, tests green; native-SQL edge cases pending IT. |

No orphaned requirements — `.planning/REQUIREMENTS.md` lines 21-27/98-101 map all four IDs to Phase 2 and none are missing from the three PLAN frontmatter `requirements:` fields.

**Documentation note (not a code gap):** `.planning/REQUIREMENTS.md`'s checkboxes/traceability table (lines 22-23, 27, 99-101) still show EXPORT-02/EXPORT-03/SEARCH-01 as unchecked/"Pending" — this is stale relative to the shipped code and should be flipped once this verification is accepted, consistent with how EXPORT-01 was updated after its own confirmation.

### Anti-Patterns Found

None. Scanned all newly-created/modified phase files (`PdfExportService`, `MapboxClient`/`MapboxClientConfig`/`MapboxProperties`, `TripCompletion`, `TripOwnerSummaryResponse`, `TripResponse`, `TripSearchRepositoryImpl`, `TripController`, `TripExportController`, `dashboard.page.ts`, `dashboard.page.html`) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`/"not yet implemented" — zero hits outside legitimate HTML `placeholder="..."` input attributes and a `.thumbnail-placeholder` CSS class name (both benign UI terms, not debt markers).

### Human Verification Required

1. **CI Testcontainers run for the four extended `*IT` classes**
   **Test:** Push the branch and let `mvn -B verify -Pci` run `TripExportControllerIT`, `TripRepositoryIT`, `TripSearchRepositoryIT`, `TripControllerIT`.
   **Expected:** All new/extended methods pass against real Postgres.
   **Why human:** No Docker daemon locally; branch unpushed; this is the only way to prove native-SQL correctness (EXISTS dedup, COALESCE duration, correlated-subquery counts, ordering tiebreak) that unit tests and code inspection cannot fully close.

2. **Visual check of the embedded Mapbox route-map snapshot**
   **Test:** With a real `MAPBOX_TOKEN` provisioned, export the PDF of an optimized trip and open it.
   **Expected:** A legible map image with the correct route/pins, matching the in-app map's style.
   **Why human:** Requires a provisioned third-party secret and visual inspection of rendered PDF image content.

### Gaps Summary

No FAILED truths. All four ROADMAP.md Phase 2 success criteria are observably true in the codebase, backed by 309/309 backend and 355/355 frontend passing tests run in this verification session (not just SUMMARY claims). The 3 code-review warnings (WR-01 dashboard filter-loss, WR-02 Mapbox double-encoding, WR-03 unescaped ILIKE wildcards) were independently confirmed fixed with their own regression tests, all passing.

The one real open item is environmental, not a code defect: the native-SQL-dependent behaviors (D-08 privacy tripwire against real Postgres, exact-once search matching, filter intersection, ordering stability, owner-scoping under search) are implemented correctly by code inspection and mirror an already-proven pattern (`searchPublicTrips`), but have never actually executed against Postgres for this phase's commits — no Docker daemon on this machine, and the branch (16 commits ahead) has not been pushed, so CI hasn't run either. This is the same "no team machine runs Docker, CI is the real IT gate" constraint the project has operated under since Phase 1, not a new problem introduced here. Routes to `human_needed` rather than `gaps_found` — nothing failed, something still needs to run.

---

*Verified: 2026-08-21*
*Verifier: Claude (gsd-verifier)*
