---
phase: 02-exports-completion-search
reviewed: 2026-08-21T00:00:00Z
depth: standard
files_reviewed: 38
files_reviewed_list:
  - backend/pom.xml
  - backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClient.java
  - backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClientConfig.java
  - backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxProperties.java
  - backend/src/main/java/com/tripflow/backend/controller/TripController.java
  - backend/src/main/java/com/tripflow/backend/controller/TripExportController.java
  - backend/src/main/java/com/tripflow/backend/dto/TripCompletion.java
  - backend/src/main/java/com/tripflow/backend/dto/TripOwnerSummaryResponse.java
  - backend/src/main/java/com/tripflow/backend/dto/TripResponse.java
  - backend/src/main/java/com/tripflow/backend/dto/TripSearchFilters.java
  - backend/src/main/java/com/tripflow/backend/exception/GlobalExceptionHandler.java
  - backend/src/main/java/com/tripflow/backend/exception/MapboxClientException.java
  - backend/src/main/java/com/tripflow/backend/mapper/TripMapper.java
  - backend/src/main/java/com/tripflow/backend/repository/TripRepository.java
  - backend/src/main/java/com/tripflow/backend/repository/TripSearchRepository.java
  - backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java
  - backend/src/main/java/com/tripflow/backend/service/PdfExportService.java
  - backend/src/main/java/com/tripflow/backend/service/TripService.java
  - backend/src/main/resources/application-prod.properties
  - backend/src/main/resources/application.properties
  - backend/src/test/java/com/tripflow/backend/client/mapbox/MapboxClientTest.java
  - backend/src/test/java/com/tripflow/backend/controller/TripControllerIT.java
  - backend/src/test/java/com/tripflow/backend/controller/TripExportControllerIT.java
  - backend/src/test/java/com/tripflow/backend/dto/TripCompletionTest.java
  - backend/src/test/java/com/tripflow/backend/repository/TripSearchRepositoryIT.java
  - backend/src/test/java/com/tripflow/backend/service/PdfExportServiceTest.java
  - backend/src/test/java/com/tripflow/backend/service/TripServiceTest.java
  - docs/api-contracts.md
  - frontend/src/app/core/models/trip.model.ts
  - frontend/src/app/core/services/trip.service.spec.ts
  - frontend/src/app/core/services/trip.service.ts
  - frontend/src/app/pages/trips/dashboard/dashboard.page.html
  - frontend/src/app/pages/trips/dashboard/dashboard.page.scss
  - frontend/src/app/pages/trips/dashboard/dashboard.page.spec.ts
  - frontend/src/app/pages/trips/dashboard/dashboard.page.ts
  - frontend/src/app/pages/trips/trip-view/trip-view.page.html
  - frontend/src/app/pages/trips/trip-view/trip-view.page.spec.ts
  - frontend/src/app/pages/trips/trip-view/trip-view.page.ts
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: fixed
---

**Post-review update (2026-08-21):** All 3 warnings (WR-01, WR-02, WR-03) fixed by the orchestrator and verified green (`.\mvnw verify`, `npm run test:ci` 355/355, `npm run lint` clean). See "Fixes Applied" section at the end of this file. The 2 Info items were left as-is per the review's own "low priority"/"optional" guidance.

# Phase 02: Code Review Report

**Reviewed:** 2026-08-21T00:00:00Z
**Depth:** standard
**Files Reviewed:** 38
**Status:** issues_found

## Summary

Reviewed the PDF export (Mapbox snapshot), trip completion, and owner search/filter feature set across backend and frontend. Ownership/visibility checks, DTO scoping (owner-only vs. public), filename sanitization, and the completion-percentage arithmetic are all sound and well-covered by tests. No SQL injection, IDOR, or auth-bypass issues found — `TripSearchRepositoryImpl` parameterizes every native-query value and scopes owned searches strictly to `principal.userId()`.

Two real gaps stood out: the dashboard's `ionViewWillEnter` reload silently drops active search/filter state on every return-to-list navigation, and `MapboxClient`'s manual `URLEncoder.encode` of a GeoJSON path segment combined with Spring `RestClient`'s own URI-template encoding is a well-known double-encoding footgun that has zero test coverage for the actual success path (only the token-length-fallback path is exercised). Neither is proven to fail at runtime from static review alone, but both are exactly the kind of thing a test should pin down and currently doesn't.

## Warnings

### WR-01: Dashboard reload after `ionViewWillEnter` silently discards active search/filters

**File:** `frontend/src/app/pages/trips/dashboard/dashboard.page.ts:95-97,134-147`
**Issue:** `ionViewWillEnter()` calls `loadTrips()`, which calls `this.tripService.listTrips()` with **no arguments** — it never passes `this.filters`. Every other filter-mutating method (`onSearchChange`, `onStatusChange`, etc.) instead pushes through `filterChanges` → the debounced pipeline that does pass `this.filters`. So: a user applies a search/filter, taps into a trip, then taps Back — Ionic re-fires `ionViewWillEnter`, `loadTrips()` runs unfiltered, and the trip list silently reverts to the full unfiltered set while the searchbar/selects still visually show the previously-applied filter values (`[value]="filters.search ?? ''"` etc. read from the still-populated `this.filters`). The displayed filter controls and the displayed data go out of sync. The existing test (`dashboard.page.spec.ts:103-109`, "ionViewWillEnter loads trips") only asserts `loadTrips` was called, not that it respects `filters`, so this regression path has no coverage.
**Fix:**
```ts
loadTrips(): void {
  this.loading = true;
  this.error = null;
  this.tripService.listTrips(0, 20, this.filters).subscribe({
    next: (page) => { this.trips = page.content; this.loading = false; },
    error: (err) => { this.error = err.message; this.loading = false; },
  });
}
```
Add a regression test that sets `component.filters = { search: 'paris' }` before calling `ionViewWillEnter()`/`loadTrips()` and asserts `listTrips` was called with those filters, not the no-arg defaults.

### WR-02: `MapboxClient`'s pre-encoded GeoJSON overlay risks double-encoding through `RestClient`, and the success path is untested

**File:** `backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClient.java:80,86`
**Issue:** `geojsonOverlay` manually percent-encodes the route JSON with `URLEncoder.encode(..., UTF_8)` and splices the result into a path segment, then hands the whole path string to `mapboxRestClient.get().uri(finalPath)`. Spring's default `RestClient` URI-builder factory (`DefaultUriBuilderFactory`, `EncodingMode.TEMPLATE_AND_VALUES`) itself encodes the template string it's given — components that are not marked as already-encoded get every `%` re-escaped to `%25`. Pre-encoding a path segment and then handing it to a `RestClient`/`RestTemplate` `.uri(String)` call is a well-documented Spring gotcha that produces a double-encoded, malformed request. This is the only client in the codebase that pre-encodes a path segment this way — `OrsClient`/`GeminiClient` instead use `{profile}`/`{model}` URI template variables and let Spring do the (single) encoding pass, which is the safer pattern. If double-encoding does occur here, Mapbox will reject the malformed `geojson(...)` parameter, `PdfExportService.addMapSnapshot` will catch the resulting `MapboxClientException` (or malformed-image `IOException`) and silently degrade to no map at all — meaning the documented "route line" map feature (D-04, `docs/api-contracts.md:555`) would never actually render for any optimized trip, and nothing would surface that failure since it's designed to fail open.
**Fix:** Either stop pre-encoding and build the request with a real `URI`/`UriComponentsBuilder` marked as already-encoded (`UriComponentsBuilder.fromUriString(path).build(true).toUri()`, then `.uri(uri)`), or use Spring's own encoding via a `UriBuilder` callback (`.uri(uriBuilder -> uriBuilder.path(...).build())`) instead of manual `URLEncoder`. Whichever fix lands, add a `MockRestServiceServer` assertion in `MapboxClientTest` for the **success** path (route geometry under the length cap) that checks the actual outgoing request URI decodes back to the original GeoJSON — today only the null-route and over-length-fallback paths are exercised (`MapboxClientTest.java:48-124`), so this exact bug class would ship silently.

### WR-03: `TripSearchRepositoryImpl` doesn't escape `%`/`_` in user-supplied search text before building the ILIKE pattern

**File:** `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java:52-53`, `backend/src/main/java/com/tripflow/backend/service/TripService.java:60-61`
**Issue:** `"%" + query + "%"` is built directly from user input. This isn't a SQL-injection risk (the value is bound as a parameter), but Postgres `ILIKE` treats `%` and `_` in the pattern as wildcards, so a search for e.g. `"50% off"` or `"a_b"` will match unintended substrings rather than the literal text the user typed. Minor, but it's a correctness gap on a newly-added user-facing search feature with no test covering it.
**Fix:** Escape `%`, `_`, and `\` in the raw query before wrapping in `%...%`, e.g. `query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")`, and use `ILIKE ... ESCAPE '\'` in `TEXT_MATCH_SQL`.

## Info

### IN-01: `TripSearchRepositoryImpl` count/id queries duplicate the same filter WHERE-clause text twice per method

**File:** `backend/src/main/java/com/tripflow/backend/repository/TripSearchRepositoryImpl.java:139-178`
**Issue:** `matchingOwnedIds` and `countOwnedMatches` (and their public sibling pair) repeat the identical `WHERE` clause as separate native-query string literals. `bindOwnedParameters` already factors out parameter binding, but the SQL text itself is copy-pasted, so a future filter addition has to be edited in two places or the id/count queries drift.
**Fix:** Low priority given the existing "one flat projection, not a fetch-joined entity query" design rationale documented in the class javadoc — if this file changes again, consider extracting the shared `WHERE` fragment into a second constant alongside `TEXT_MATCH_SQL`.

### IN-02: `MapboxProperties`/`MapboxClient` javadoc says "backend/.env (MAPBOX_TOKEN)" but the actual property key differs between profiles in a way not called out

**File:** `backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxProperties.java:10-12`
**Issue:** Minor documentation nit, not a functional bug — `mapbox.access-token` binds to `${MAPBOX_TOKEN:}` in dev (empty default, degrades gracefully) and `${MAPBOX_TOKEN}` in prod (no default, fails startup loudly if unset), which is intentional and correctly implemented (`application.properties:59`, `application-prod.properties:62`), but the class-level javadoc only mentions the dev `.env` case and doesn't flag the prod fail-loud behavior for a reader who only opens this file.
**Fix:** Optional — add one line to the javadoc noting the prod profile has no default and will fail application startup if `MAPBOX_TOKEN` is unset.

## Fixes Applied

- **WR-01:** `dashboard.page.ts#loadTrips` now calls `listTrips(0, 20, this.filters)` instead of the no-arg overload. Added `dashboard.page.spec.ts` regression test asserting `loadTrips()` passes active filters.
- **WR-02:** `MapboxClient.staticSnapshot` now builds a `java.net.URI` directly (`URI.create(props.baseUrl() + path)`) and calls `.uri(uri)` instead of `.uri(String)`, bypassing `RestClient`'s template-encoding pass entirely — `geojsonOverlay`'s `URLEncoder` pass is now the only encoding pass the payload goes through. Added `MapboxClientTest#staticSnapshot_withRouteGeometry_requestUriIsSinglyEncodedNotDoubleEncoded`, which exact-matches the outgoing request URI (would fail on any re-encoding).
- **WR-03:** Added `TripSearchRepositoryImpl.likePattern(String)` — escapes `\`, `%`, `_` before wrapping in `%...%`; `TEXT_MATCH_SQL`'s three `ILIKE` clauses gained `ESCAPE '\'`. Both `searchPublicTrips` (in `TripSearchRepositoryImpl`) and `TripService#searchOwnedTrips` now route through this one helper. Added `TripServiceTest#searchOwnedTrips_searchContainingWildcardChars_escapesThemInThePattern`.

---

_Reviewed: 2026-08-21T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
