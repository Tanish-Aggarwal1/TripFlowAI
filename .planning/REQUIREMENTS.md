# Requirements: TripFlowAI

**Defined:** 2026-08-06
**Core Value:** AI-assisted, route-optimized multi-stop itineraries that feel like a real, usable travel product

Requirement IDs below map directly to the fall-break plan (`docs/TripFlow_fall_Break_Plan.md`, FB-##) and winter plan (`docs/TripFlow_Winter_Plan.md`, WP-##) so traceability back to the source planning docs (and eventually Jira, per each doc's Section 5 ticket-creation instructions) is direct.

## v1 Requirements

Requirements for this milestone (fall break through winter presentation). Each maps to a roadmap phase.

### Auth (AUTH)

- [x] **AUTH-01**: Requests without a valid JWT return 401 (not 403) with a JSON `ApiError` body; authenticated-but-forbidden returns 403 (FB-01) — **Done, confirmed 2026-08-14**
- [x] **AUTH-02**: Controllers resolve the current user via a typed `UserPrincipal` (`@AuthenticationPrincipal`), not string-parsed `authentication.getName()`; `AuthUtils`/`CurrentUserService` removed (FB-02) — **Done, confirmed 2026-08-14**
- [x] **AUTH-03**: The four SCRUM-55 gap-test scenarios (unauth 401, real-JWT-path test, delete-by-non-owner 403, GET nonexistent 404) have dedicated passing integration tests (FB-03) — **Done, confirmed 2026-08-14**
- [x] **AUTH-04**: Users can obtain a refresh token alongside their access token, silently refresh on expiry, and revoke sessions server-side on logout, with reuse-detection revoking the token family (FB-16) — **COMPLETE in code, 2026-08-17.** Issuance and single-use rotation (01-02), logout revocation D-04 + reuse-detection family revoke D-03 + refresh rate limit (01-03), proactive silent-refresh timer D-05 and the two-stage session-expiry experience D-06 (01-04). One manual QA step outstanding: plan 01-04 task 3's banner-then-dialog `<human-check>` against a running backend.

### Export (EXPORT)

- [x] **EXPORT-01**: Users can export a trip's ordered stops as a valid `.ics` calendar file with correct `DTSTART`/`DTEND` from `dayNumber`/`plannedTime` (FB-04) — **Done, confirmed 2026-08-14**
- [x] **EXPORT-02**: Users can export a trip as a formatted PDF itinerary (header, ordered stops, notes) (FB-05)
- [x] **EXPORT-03**: Trip responses expose enough data (visited/total stop counts) to compute a completion percentage, handling the zero-stops case without dividing by zero (FB-06)

### Search (SEARCH)

- [x] **SEARCH-01**: Users can search (by title/destination) and filter (by status, date range, visibility) their trip list, using the paged response convention from REF-21/SCRUM-110 (FB-07)

### AI Quality (AI)

- [ ] **AI-01**: Gemini itinerary suggestions are measurably higher quality against a ~10-trip benchmark after a prompt-engineering pass, with a documented before/after comparison (FB-08)
- [ ] **AI-02**: `suggestItinerary` returns a day-by-day plan — day number, planned time, reasoning, stop type — including proactive meal-stop suggestions (FB-17) — **PARTIAL, confirmed 2026-08-14**: `reason` field already exists on `SuggestedItinerary.SuggestedStop`; day/time/stopType/meal fields do not
- [ ] **AI-03**: Users can tap an AI-suggested stop or meal slot to see 2-3 alternatives and swap one in (FB-18)

### Notifications (NOTIFY)

- [ ] **NOTIFY-01**: New users receive a welcome/confirmation email on registration, and trip owners receive a reminder email before a trip's start date, via a free-tier transactional email provider (FB-09)

### Frontend Polish (POLISH)

- [ ] **POLISH-01**: The optimized route renders as a polyline overlay between stops, and nearby stop markers cluster at low zoom levels (FB-10) — **PARTIAL, confirmed 2026-08-14**: polyline rendering exists in `trip-map.component.ts`; marker clustering does not
- [ ] **POLISH-02**: Users can toggle app-wide dark mode, persisted across sessions (FB-11)
- [ ] **POLISH-03**: Users can search for a place by name (Mapbox Search Box) when adding/editing a stop, with manual lat/lng entry preserved as a fallback, and edit-stop-form supports coordinate correction (FB-15)

### Observability (OBSERVE)

- [ ] **OBSERVE-01**: Unhandled exceptions in production (backend and frontend) are captured in Sentry with stack trace, request context, and release tag, configured via environment variable (FB-12)

### PWA (PWA)

- [ ] **PWA-01**: Users can view their previously-loaded trip list and trip details while offline, with a clear offline indicator (read-only; no offline mutation) (FB-13)
- [ ] **PWA-02**: A Capacitor build attempt for Android (and iOS if available) produces either a working APK/IPA or a documented list of blockers, written up in `docs/native-build-spike.md` (FB-14)

### Community/Social (SOCIAL)

- [ ] **SOCIAL-01**: Authenticated users can browse a full-screen, TikTok-style vertically-swipeable "For You" feed of other users' PUBLIC trips — trip name + major location + owner username fixed at top, description fixed at bottom, stop images/descriptions swipeable horizontally in the middle, text-card fallback for trips with no stop photos (FB-19, restyled 2026-08-06 during Phase 6 discussion — see `.planning/phases/06-community-social/06-CONTEXT.md`). **Backend done, frontend not built — re-confirmed 2026-08-14, unchanged since 2026-08-10**: `GET /api/discovery/trips`/`GET /api/discovery/search` (SCRUM-160/163) merged 2026-08-06, but this requirement is about the TikTok-style swipe UI specifically, which is still unbuilt — remaining work is entirely frontend, plus creator-username on the DTO (still not exposed).
- [ ] **SOCIAL-02**: Users can like/unlike a PUBLIC trip idempotently, directly from an on-card action rail without leaving the feed; like count is derived from a `trip_likes` join table, not a hand-maintained counter (FB-20). **Backend done — re-confirmed 2026-08-14, unchanged since 2026-08-10** (`POST`/`DELETE /api/trips/{id}/like`, SCRUM-161, merged 2026-08-06) — do not re-implement; remaining work is the frontend action-rail button, which still does not exist.
- [ ] **SOCIAL-03**: Users can clone a PUBLIC trip into their own account as a new PRIVATE trip they can edit freely, directly from the on-card action rail (FB-21). **Backend done — re-confirmed 2026-08-14, unchanged since 2026-08-10** (`POST /api/trips/{id}/clone`, SCRUM-162, merged 2026-08-06) — do not re-implement; remaining work is the frontend clone button, which still does not exist.
- [ ] **SOCIAL-04**: Users can save/bookmark a PUBLIC trip to a private "saved trips" list, idempotently, directly from the on-card action rail (FB-24)
- [ ] **SOCIAL-05**: Users have a profile page showing their username, join date, and stored interests (new, added 2026-08-06 — required as the data source for SOCIAL-06 and to support the feed's owner-username display)
- [ ] **SOCIAL-06**: The "For You" feed orders PUBLIC trips with interest-tag matches against the viewer's stored profile interests first, falling back to recency for the rest (new, added 2026-08-06 — lightweight personalization, not a full recommendation algorithm)

### Trip Tracking (TRACK)

- [ ] **TRACK-01**: While the trip-view page is open and location permission is granted, arriving within a configurable radius of an unvisited stop prompts the user to confirm arrival and mark it visited (foreground-only) (FB-25)
- [ ] **TRACK-02**: Background push notification for stop arrival ships only after FB-14 (native Capacitor build) lands successfully (FB-26)

### Winter Hardening (HARDEN)

- [ ] **HARDEN-01**: A production hardening checklist pass confirms no dev-only endpoints/permissive CORS survive in prod, error responses never leak internals, dependency scans are clean or explicitly accepted, and rate limiting behaves correctly under concurrent load (WP-01, WP-03)
- [ ] **HARDEN-02**: Deployment verification (health check + register/login smoke test) runs automatically post-deploy and fails loudly on breakage (WP-02)
- [ ] **HARDEN-03**: Missing sprint retros are backfilled and `docs/` is swept for stale "planned"/"not yet implemented" sections against actual shipped code (WP-04, WP-05)
- [ ] **HARDEN-04**: A full end-to-end regression pass (SCRUM-74a/b/c plus FB-19/20/21 coverage) is re-run against the deployed environment and consolidated into a dated sign-off checklist (WP-06)
- [ ] **HARDEN-05**: A repeatable demo/seed data script populates a scratch environment with realistic trips, PUBLIC trips with likes, and an AI-suggested itinerary (WP-07)
- [ ] **HARDEN-06**: The top-level README reflects final shipped scope, verified setup instructions, screenshots, and a link to the deployed instance (WP-08)

## v2 Requirements

None — fall-break and winter scope are already fully enumerated above; nothing is deliberately deferred beyond this milestone at this time.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Public unauthenticated trip sharing | Would require reworking `SecurityConfig`'s default-deny posture; audit explicitly recommends against it for capstone scope |
| Background/push arrival detection without a native shell | FB-26 hard-blocked on FB-14 (Capacitor build spike) actually landing; iOS PWA background geolocation is unreliable-to-unsupported otherwise |
| Distributed/Redis-backed rate limiting | Current single-instance in-memory Bucket4j is sufficient at current scale; premature for capstone scope |
| Offline edits/sync in the PWA | FB-13 is explicitly read-only offline viewing; offline mutation/sync is future work |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Done |
| AUTH-02 | Phase 1 | Done |
| AUTH-03 | Phase 1 | Done |
| AUTH-04 | Phase 1 | Complete in code (01-02 issuance/rotation, 01-03 revocation, 01-04 frontend) — banner/dialog human-check outstanding |
| EXPORT-01 | Phase 2 | Done |
| EXPORT-02 | Phase 2 | Complete |
| EXPORT-03 | Phase 2 | Complete |
| SEARCH-01 | Phase 2 | Complete |
| AI-01 | Phase 3 | Pending |
| AI-02 | Phase 3 | Partial (reasoning field only) |
| AI-03 | Phase 3 | Pending |
| POLISH-01 | Phase 4 | Partial (polyline only, no clustering) |
| POLISH-02 | Phase 4 | Pending |
| POLISH-03 | Phase 4 | Pending |
| NOTIFY-01 | Phase 5 | Pending |
| OBSERVE-01 | Phase 5 | Pending |
| PWA-01 | Phase 5 | Pending |
| PWA-02 | Phase 5 | Pending |
| SOCIAL-01 | Phase 6 | Partial (backend only) |
| SOCIAL-02 | Phase 6 | Partial (backend only) |
| SOCIAL-03 | Phase 6 | Partial (backend only) |
| SOCIAL-04 | Phase 6 | Pending |
| SOCIAL-05 | Phase 6 | Pending |
| SOCIAL-06 | Phase 6 | Pending |
| TRACK-01 | Phase 7 | Pending |
| TRACK-02 | Phase 7 | Pending |
| HARDEN-01 | Phase 9 | Pending |
| HARDEN-02 | Phase 9 | Pending |
| HARDEN-03 | Phase 9 | Pending |
| HARDEN-04 | Phase 9 | Pending |
| HARDEN-05 | Phase 9 | Pending |
| HARDEN-06 | Phase 9 | Pending |

**Coverage:**

- v1 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0 ✓
- Done: 4 (AUTH-01/02/03, EXPORT-01) · Partial: 5 (AI-02, POLISH-01, SOCIAL-01/02/03) · Pending: 23

**[FIXED 2026-08-14]** HARDEN-01..06 were mismapped to "Phase 8" in this table since 2026-08-06 — ROADMAP.md's Phase 8 is Trip Collaboration, not Winter Hardening (that's Phase 9). Corrected above; this was a stale/incorrect artifact, not a codebase finding.

---
*Requirements defined: 2026-08-06*
*Last updated: 2026-08-14 — full codebase audit corrected Done/Partial/Pending status per requirement and fixed HARDEN-01..06 phase mismapping (see fix note above)*
