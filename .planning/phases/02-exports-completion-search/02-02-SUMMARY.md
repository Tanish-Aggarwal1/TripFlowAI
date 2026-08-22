---
phase: 02-exports-completion-search
plan: 02
subsystem: api
tags: [openpdf, mapbox, rest-client]

requires: []
provides:
  - "GET /api/trips/{id}/export/pdf returns a formatted PDF itinerary (header, ordered stops table, notes)"
  - "PDF embeds a best-effort Mapbox route-map snapshot (route overlay when optimized, marker pins otherwise), degrading silently to a map-less PDF on any failure"
affects: [trip-detail-ui]

actuals:
  tokens: unknown
  tasks: 3
  commits: 5

tech-stack:
  added: ["com.github.librepdf:openpdf:2.2.2"]
  patterns: ["client/{service}/ external-integration module (mirrors client/ors/, client/gemini/)"]

key-files:
  created:
    - backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClient.java
    - backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxClientConfig.java
    - backend/src/main/java/com/tripflow/backend/client/mapbox/MapboxProperties.java
    - backend/src/main/java/com/tripflow/backend/exception/MapboxClientException.java
    - backend/src/main/java/com/tripflow/backend/service/PdfExportService.java
    - backend/src/test/java/com/tripflow/backend/client/mapbox/MapboxClientTest.java
    - backend/src/test/java/com/tripflow/backend/service/PdfExportServiceTest.java
  modified:
    - backend/pom.xml
    - backend/src/main/java/com/tripflow/backend/controller/TripExportController.java
    - backend/src/main/java/com/tripflow/backend/exception/GlobalExceptionHandler.java
    - backend/src/main/resources/application.properties
    - backend/src/main/resources/application-prod.properties
    - backend/src/test/java/com/tripflow/backend/controller/TripExportControllerIT.java
    - frontend/src/app/core/services/trip.service.ts
    - frontend/src/app/core/services/trip.service.spec.ts
    - frontend/src/app/pages/trips/trip-view/trip-view.page.ts
    - frontend/src/app/pages/trips/trip-view/trip-view.page.html
    - frontend/src/app/pages/trips/trip-view/trip-view.page.spec.ts

key-decisions:
  - "Mapbox Static Images API called server-side behind a backend-owned secret (D-03) via a new client/mapbox module mirroring client/ors's shape (properties record with masked toString, RestClientConfig bean, execute() translation helper to a 502)."
  - "Map snapshot is always off the critical path: blank token, over-length URL, or any HTTP failure degrades to a smaller overlay or a map-less PDF rather than failing the export (verified: exportPdf_mapboxClientThrows_stillYieldsPdfBytes, exportPdf_zeroStopTrip_neverCallsMapboxClient)."
  - "sanitizeFilename (existing package-private static on TripExportController) reused as-is for the PDF filename — no second sanitizer."

patterns-established:
  - "client/mapbox/ follows the exact client/ors/ shape: MapboxProperties (@ConfigurationProperties record, SecretMask-masked toString), MapboxClientConfig (RestClient bean, query-param auth not header auth), MapboxClient (private execute() translation helper -> MapboxClientException -> 502 via GlobalExceptionHandler)."

requirements-completed: [EXPORT-02]

coverage:
  - id: D1
    description: "Owner or PUBLIC-trip requester can download a PDF itinerary with title, ordered stops table (schedule + notes), and a route-map snapshot when available"
    requirement: "EXPORT-02"
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/service/PdfExportServiceTest.java"
        status: pass
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/client/mapbox/MapboxClientTest.java"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/com/tripflow/backend/controller/TripExportControllerIT.java (CI-only, mvn verify -Pci)"
        status: unknown
    human_judgment: false
  - id: D2
    description: "Non-owner GET of a PRIVATE trip's PDF returns 404 (ownership delegated to TripService.getTrip, not reimplemented)"
    requirement: "EXPORT-02"
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/service/PdfExportServiceTest.java#exportPdf_delegatesOwnershipCheckToTripService"
        status: pass
    human_judgment: false
  - id: D3
    description: "A Mapbox failure, blank token, or oversized request URL never fails the PDF download — it degrades to a map-less or marker-only PDF"
    requirement: "EXPORT-02"
    verification:
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/service/PdfExportServiceTest.java#exportPdf_mapboxClientThrows_stillYieldsPdfBytes"
        status: pass
      - kind: unit
        ref: "backend/src/test/java/com/tripflow/backend/client/mapbox/MapboxClientTest.java"
        status: pass
    human_judgment: false

duration: unknown (spans a session-limit interruption and resume)
completed: 2026-08-21
status: complete
---

# Phase 02 Plan 02: PDF Export (with Mapbox Map Snapshot) Summary

**GET /api/trips/{id}/export/pdf now returns a formatted itinerary PDF — title, ordered stops table, notes, and a best-effort embedded route-map snapshot from Mapbox that never blocks the download on failure.**

## Performance

- **Tasks:** 3 completed
- **Commits:** 5 (`6a38497`, `fabd28d`, `626c712`, `994638c`, `6255a88`)

## Accomplishments
- Task 1 (tracer): end-to-end PDF export returning a valid PDF with just the title, wired through `TripExportController` reusing the existing `sanitizeFilename` convention — `6a38497`.
- Task 2 (TDD): PDF body filled with an ordered stops table (schedule, notes), tolerant of null `dayNumber`/`plannedTime` (pre-optimization trips) and null `notes` — `fabd28d` (RED), `626c712` (GREEN).
- Task 3 (TDD): new `client/mapbox/` module (`MapboxProperties`, `MapboxClientConfig`, `MapboxClient`, `MapboxClientException` wired into `GlobalExceptionHandler` at 502) — `994638c` (RED). `PdfExportService.addMapSnapshot` embeds the snapshot when present, degrading silently on any `MapboxClientException` or malformed image bytes, and never calling Mapbox for a zero-stop trip — `6255a88` (GREEN).

## Task Commits

1. **Task 1: End-to-end PDF export tracer** - `6a38497` (feat)
2. **Task 2a: Failing tests for stops table body (RED)** - `fabd28d` (test)
2. **Task 2b: Fill PDF body with stops table and notes (GREEN)** - `626c712` (feat)
3. **Task 3a: Scaffold client/mapbox module and failing map-snapshot tests (RED)** - `994638c` (test)
3. **Task 3b: Implement MapboxClient and embed map snapshot (GREEN)** - `6255a88` (feat)

## Deviations from Plan

### Session interruption (not a plan deviation, but relevant to this summary's provenance)

Task 3's RED commit (`994638c`) was written by a `gsd-executor` subagent in a prior session that was then terminated by the account's own session limit mid-GREEN — it had wired `PdfExportService`'s constructor/imports for `MapboxClient` and the `addMapSnapshot(doc, trip)` call site, but the method body itself did not exist yet (a compile error). On resume, the orchestrator (not a fresh executor subagent) completed task 3's GREEN step directly: wrote `addMapSnapshot`, fixed a compile error (`Image.getInstance` throws `IOException`/`BadElementException`, not just `DocumentException` — the original in-flight edit had only declared `throws DocumentException`), ran `.\mvnw test -Dtest=PdfExportServiceTest,MapboxClientTest` (green) then full `.\mvnw verify` (green, exit 0), and committed as `6255a88`. No task 1/2 work was affected by the interruption.

**Total deviations:** 0 scope deviations — the interruption was operational (account limit), not a plan or requirements change.

## Issues Encountered
- Docker Desktop installed but daemon not running locally — `TripExportControllerIT`'s PDF-export IT coverage (extended in task 2) compiles but was not run locally; `mvn -B verify -Pci` in CI is the actual gate, per `02-VALIDATION.md`.

## User Setup Required
`MAPBOX_TOKEN` env var must be provisioned in each environment (local `.env`, CI, Render) for the map snapshot to render — a blank/missing token degrades to a map-less PDF by design, so this is not launch-blocking, but the one non-blocking manual check noted in `02-VALIDATION.md` ("open an exported PDF for an optimized trip and confirm the embedded map snapshot is legible") still needs a human once the token exists.

## Next Phase Readiness
- EXPORT-02 fully implemented, backend + frontend (trip-view page export button/link).
- Wave 2 (02-03, completion percentage) and Wave 3 (02-04, search/filter) both depend on this wave only for file-ownership sequencing (both touch `trip.service.ts`), not logically — confirmed complete and independently verified after this plan.

---
*Phase: 02-exports-completion-search*
*Completed: 2026-08-21*
