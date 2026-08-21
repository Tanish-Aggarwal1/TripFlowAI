# Phase 2: Exports, Completion & Search - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-20
**Phase:** 2-Exports, Completion & Search
**Areas discussed:** PDF export depth, Completion percentage rules, Search/filter design, Search endpoint shape

---

## PDF export depth

| Option | Description | Selected |
|--------|-------------|----------|
| Header + stops + notes only | Matches ROADMAP wording exactly, text-only layout | |
| + route map snapshot | Static Mapbox image embedded, new integration point | ✓ |
| + stop photos | Cloudinary photos embedded inline, slower/more failure modes | |

**User's choice:** Route map snapshot.
**Notes:** Follow-up surfaced that the backend has no Mapbox token today (frontend-only, build-time). Resolved via a new backend env var, mirroring the `client/{service}/` pattern. Map source: `Trip.routeGeometry` if present, else plain stop pins.

| Option | Description | Selected |
|--------|-------------|----------|
| OpenPDF | LGPL/MPL, high-level API, familiar layout model | ✓ |
| Apache PDFBox | Apache 2.0, lower-level, more manual layout code | |

**User's choice:** OpenPDF.

| Option | Description | Selected |
|--------|-------------|----------|
| Same filename convention as .ics | Reuses `sanitizeFilename`, shared fixture tests | ✓ |
| Different scheme for PDF | Claude's discretion, no strong reason given | |

**User's choice:** Same convention as .ics.

---

## Completion percentage rules

| Option | Description | Selected |
|--------|-------------|----------|
| Only VISITED counts | SKIPPED stays in denominator, not numerator | ✓ |
| VISITED + SKIPPED count | Treats skipped as "resolved" | |

**User's choice:** Only VISITED counts.

| Option | Description | Selected |
|--------|-------------|----------|
| 0 for zero-stop trips | Simplest, no null handling | ✓ |
| null/omitted | Signals "not applicable" | |

**User's choice:** 0.

| Option | Description | Selected |
|--------|-------------|----------|
| TripResponse only | Matches ROADMAP wording literally | |
| Both TripResponse and TripSummaryResponse | Adds completion badge to list view too | ✓ |

**User's choice:** Both.
**Notes:** Follow-up discovery — `TripSummaryResponse` is shared with the public discovery feed (`TripSearchRepositoryImpl.searchPublicTrips`, `findSummariesByVisibility(PUBLIC)`). Adding completion fields as-is would leak a stranger's progress on their PUBLIC trip. Resolved by splitting into two DTOs — see Search endpoint shape section / CONTEXT.md D-08.

---

## Search/filter design

| Option | Description | Selected |
|--------|-------------|----------|
| Title + tags | Same fields discovery search already matches | |
| Also match stop/place names | Requires new Stop→Place join, more useful | ✓ |

**User's choice:** Also match stop/place names.

| Option | Description | Selected |
|--------|-------------|----------|
| Extend TripSearchRepository with owner-scoped method | Shared WHERE-clause helper, one repository | ✓ |
| New separate repository | No shared code, duplicates ILIKE/tags-unnest logic | |

**User's choice:** Asked Claude for a recommendation ("clean code approach, SOLID/DRY"). Claude recommended extending `TripSearchRepository` with a shared private WHERE-clause helper, reasoning that the interface+Impl pair is already the repository's stated pattern for keeping row-shape/query logic in one place, and a second repository would duplicate the same native SQL for no real separation of concerns. User confirmed.

| Option | Description | Selected |
|--------|-------------|----------|
| All filters AND, search separate optional param | Standard filter-list UX | ✓ |
| Search/filters mutually exclusive | Simpler backend, less flexible | |

**User's choice:** All filters AND together.

---

## Search endpoint shape

Folded into "Search/filter design" above (the reuse question) — see D-11.

---

## Loose ends (raised after initial areas)

### Date filter field
**Notes:** User initially raised a privacy concern about exposing `startDate` — Claude clarified `GET /api/trips` is owner-only (not public), so no privacy issue for this endpoint specifically. User then asked for clarification on whether this was for personal or public trips; Claude confirmed personal-only, discovery search is separate and untouched. User then raised a real, but Phase-6-scoped, concern: **PUBLIC trips' future `startDate` should not be exposed on the discovery feed** — captured as a Deferred Idea for Phase 6, not acted on here.

**User's choice:** Keep `startDate` range as a Phase 2 filter (confirmed owner-only, no privacy issue at this scope).

### Duration filter (new, user-initiated)
**User's choice:** Compute as `max(dayNumber)` across stops — no new stored field.

### PDF filename
**User's choice:** Same convention as .ics (see PDF export section above).

### Frontend search UX
| Option | Description | Selected |
|--------|-------------|----------|
| Debounce-as-you-type | ~300-400ms, responsive | ✓ |
| Search on submit only | Simpler, explicit trigger | |

**User's choice:** Debounce-as-you-type.

---

## Additional gray area (Claude-surfaced, post-area-review)

### Completion % visibility on shared DTO
**Notes:** Claude flagged that `TripSummaryResponse` (locked as "both" in Completion percentage rules above) is the same record used by the public discovery feed — adding completion fields to it would expose a stranger's PUBLIC-trip progress.

| Option | Description | Selected |
|--------|-------------|----------|
| Split into two DTOs | Completion fields only on owner-facing DTO | ✓ |
| Keep one shared DTO | Simplest, but leaks completion data publicly | |

**User's choice:** Split into two DTOs.

---

## Claude's Discretion

- Exact Mapbox Static Images API params (dimensions, zoom/padding, marker styling)
- Whether a zero-stop trip is excluded from duration-filter results or reports `durationDays: 0`
- Naming of the new owner-list summary DTO
- Whether `durationDays` is exposed as a response field or stays internal to the filter query

## Deferred Ideas

- Public discovery feed should not expose future `startDate` for other users' PUBLIC trips — Phase 6 concern, not acted on in Phase 2.
