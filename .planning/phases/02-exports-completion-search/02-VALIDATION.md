---
phase: 2
slug: exports-completion-search
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-21
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito (backend unit), JUnit 5 + Testcontainers-Postgres (backend `*IT`), Karma + Jasmine (frontend) |
| **Config file** | `backend/pom.xml` (Surefire excludes `*IT.java`, Failsafe includes it under `-Pci`); `frontend/karma.conf.js` |
| **Quick run command** | `.\mvnw verify` (backend, unit only, no Docker) / `npm run test:ci` (frontend) |
| **Full suite command** | `./mvnw verify -Pci` (backend, requires Docker/Testcontainers — CI-only per CLAUDE.md, "no team machine runs Docker") |
| **Estimated runtime** | ~2-3 min (unit) / CI-only for full `-Pci` suite |

---

## Sampling Rate

- **After every task commit:** Run `.\mvnw verify` (backend) / `npm run test:ci` (frontend)
- **After every plan wave:** Run `./mvnw verify -Pci` — CI-only per CLAUDE.md; treat CI as the actual gate for this phase's `*IT` tests, cannot be run locally
- **Before `/gsd-verify-work`:** Full suite must be green (via CI), plus the 92%/80% coverage floor (`docs/ci.md`)
- **Max feedback latency:** ~180 seconds (unit suite)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-02-01 | 02 | 1 | EXPORT-02 | T-02-01 (V4) | Owner-or-PUBLIC visibility check delegated to `TripService.getTrip`, not reimplemented | unit | `./mvnw test -Dtest=PdfExportServiceTest` | ❌ Wave 0 — new file, mirror `IcsExportServiceTest` | ⬜ pending |
| 02-02-01 | 02 | 1 | EXPORT-02 | T-02-04 | `sanitizeFilename` reuse matches the `.ics` convention exactly (D-05) | unit | `./mvnw test -Dtest=TripExportControllerTest` | ✅ existing file, no new test expected (same static method) | ⬜ pending |
| 02-02-02 | 02 | 1 | EXPORT-02 | T-02-01 | Non-owner GET of a PRIVATE trip's PDF returns 404; PDF body carries title + ordered stops + notes | unit + `*IT` | `./mvnw test -Dtest=PdfExportServiceTest` / `./mvnw verify -Pci -Dit.test=TripExportControllerIT` | ✅ IT exists, extend; ❌ unit test from 02-02-01 | ⬜ pending |
| 02-02-03 | 02 | 1 | EXPORT-02 | T-02-02, T-02-05 | Mapbox 4xx/timeout → `MapboxClientException`; blank token = zero HTTP calls; oversized URL degrades to marker-only; token masked in `toString()` | unit | `./mvnw test -Dtest=MapboxClientTest` | ❌ Wave 0 — mirror `OrsClientTest`'s `MockRestServiceServer` harness | ⬜ pending |
| 02-03-01 | 03 | 2 | EXPORT-03 | T-02-06 (V4) | Owner-list DTO forks; `TripSummaryResponse` byte-for-byte unchanged for the discovery feed (D-08) | unit | `./mvnw test -Dtest=TripCompletionTest,TripServiceTest` | ❌ Wave 0 — `TripCompletionTest` is new | ⬜ pending |
| 02-03-02 | 03 | 2 | EXPORT-03 | — | `completionPercentage`/`visitedStopCount` correct on `TripResponse`; VISITED-only numerator, zero-stops = 0 | unit | `./mvnw test -Dtest=TripMapperTest` | ✅ existing file, extend | ⬜ pending |
| 02-03-03 | 03 | 2 | EXPORT-03 | T-02-06 | Counts correct against real Postgres; record-component guard fires if the shared DTO ever gains a completion field | `*IT` | `./mvnw verify -Pci -Dit.test=TripRepositoryIT,TripControllerIT` | ✅ existing files, extend | ⬜ pending |
| 02-04-01 | 04 | 3 | SEARCH-01 | T-02-10 (V4) | `searchOwnedTrips` scoped to `t.user_id = :userId` in both the id query and the count query — never a post-filter | unit + `*IT` | `./mvnw test -Dtest=TripServiceTest` | ✅ existing file, extend | ⬜ pending |
| 02-04-02 | 04 | 3 | SEARCH-01 | T-02-09, T-02-10 | Bound params only, no concatenation; no cross-owner result or count; no duplicate rows from the places join; stable tie-broken ordering | `*IT` | `./mvnw verify -Pci -Dit.test=TripSearchRepositoryIT` | ✅ existing file, extend with ≥12 `searchOwnedTrips_*` methods | ⬜ pending |
| 02-04-02 | 04 | 3 | SEARCH-01 | T-02-13 (V5) | Typed `@RequestParam` binding rejects out-of-enum/malformed values → 400 `ApiError`, never 500 | `*IT` | `./mvnw verify -Pci -Dit.test=TripControllerIT` | ✅ existing file, extend | ⬜ pending |
| 02-04-03 | 04 | 3 | SEARCH-01 | — | Status filter offers the backend's real four constants; debounce yields one request per settled keystroke burst | unit | `npm run test:ci` | ✅ existing specs, extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*EXPORT-01 has no row: the `.ics` export shipped pre-GSD and is not re-implemented by any plan in this phase.*
*`*IT` rows are CI-only — per CLAUDE.md no team machine runs Docker, so `mvn -B verify -Pci` in CI is the actual gate for every Testcontainers row above.*

---

## Wave 0 Requirements

- [ ] `openpdf` (`com.github.librepdf:openpdf:2.2.2`) dependency addition to `pom.xml` — prerequisite before any PDF test can compile; created in task 02-02-01 alongside the code it tests
- [ ] `backend/src/test/java/com/tripflow/backend/service/PdfExportServiceTest.java` — new (task 02-02-01)
- [ ] `backend/src/test/java/com/tripflow/backend/client/mapbox/MapboxClientTest.java` — new (task 02-02-03); error translation, blank-token short-circuit, URL-length fallback, secret masking
- [ ] `backend/src/test/java/com/tripflow/backend/dto/TripCompletionTest.java` — new (task 02-03-01)
- [ ] `backend/src/test/java/com/tripflow/backend/controller/TripExportControllerIT.java` — existing file, extended with PDF methods (task 02-02-02); confirmed a `TripExportControllerIT` already exists, so new methods rather than a new class
- [ ] `backend/src/test/java/com/tripflow/backend/repository/TripSearchRepositoryIT.java` — existing file, extended (task 02-04-02); confirmed present

---

## Manual-Only Verifications

*None blocking.* Every phase behavior (PDF generation, completion %, search/filter) has automated verification via unit + `*IT` tests, including the zero-stop, null-route, Mapbox-failure and unprovisioned-token paths.

One non-blocking human check is worth doing once `MAPBOX_TOKEN` is provisioned: open an exported PDF for an optimized trip and confirm the embedded map snapshot is legible and framed sensibly. Image aesthetics are not assertable, and every failure mode around it (missing token, service down, over-length URL, never-optimized trip) already degrades to a valid map-less PDF under test. Do not block phase sign-off on this.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (PdfExportServiceTest, TripExportControllerIT extension, MapboxClientTest, pom.xml openpdf dep)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s (unit suite)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
