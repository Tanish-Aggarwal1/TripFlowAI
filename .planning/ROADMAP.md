# Roadmap: TripFlowAI

## Overview

This milestone covers the semester-5→semester-6 break (fall, ~4.5 months: 2026-08-17 to early Jan 2027) and winter term (~3 months). Explicit team decision (2026-08-06, Phase 6 discussion): front-load as much functionality as possible into fall break, so winter term is mostly hardening/regression/polish rather than net-new feature work. Phases 1-7 (all feature work, including the expanded Phase 6 scope) target the fall-break window and mirror the sequencing already worked out in `docs/TripFlow_fall_Break_Plan.md` Section 4; Phase 8 is the winter hardening pass from `docs/TripFlow_Winter_Plan.md` and stays a groomable backlog — not a fixed schedule — until the team knows what fall break actually finished.

## Phases

- [x] **Phase 1: Auth Seam Hardening** (Fall) - Typed 401/403, `UserPrincipal` seam, refresh tokens, closed gap tests — 4/4 plans executed, 4/4 success criteria verified, UAT passed, 27/27 threats closed (completed 2026-08-17)
- [x] **Phase 2: Exports, Completion & Search** (Fall) - .ics/PDF export, completion percentage, trip list search/filter (completed 2026-08-22)
- [ ] **Phase 3: AI Quality & Scheduling** (Fall) - Gemini prompt pass, day/time/meal-aware itineraries, alternative suggestions
- [ ] **Phase 4: Frontend & Map Polish** (Fall) - Route polyline + clustering, dark mode, Mapbox place search
- [ ] **Phase 5: Notifications, Observability & PWA** (Fall) - Email notifications, Sentry, offline caching, native build spike
- [x] **Phase 6: Community & Social** (Fall) - TikTok-style For You feed, like/save/clone, ratings, user profile page, interest-based ranking — 6/6 plans executed, verification passed (completed 2026-08-31)
- [ ] **Phase 7: Trip Tracking** (Fall) - Foreground stop-arrival detection (+ blocked push-notification stretch)
- [ ] **Phase 8: Trip Collaboration** (Fall) - Multi-owner trips: invite/accept, collaborator permissions, shared editing
- [ ] **Phase 9: Winter Hardening & Sign-off** (Winter) - Production hardening, deploy automation, load test, regression, docs, README

## Phase Details

### Phase 1: Auth Seam Hardening

**Goal**: Auth boundary is type-safe, correctly coded (401 vs 403), covered by real integration tests, and supports persistent login via refresh tokens
**Depends on**: Nothing (first phase)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04
**Jira epic**: `SCRUM-275` ("AUTH v2") for FB-01/02/16; `SCRUM-282` ("TESTING v2") for FB-03 — both created 2026-08-06; `SCRUM-83` is not the AUTH epic (see RISK-J1, resolved), `SCRUM-84` closes 2026-08-17
**Success Criteria** (what must be TRUE):

  1. Unauthenticated requests to protected endpoints return 401 with a JSON `ApiError`; forbidden-but-authenticated returns 403
  2. Controllers resolve the current user via typed `UserPrincipal`, not string-parsed principal
  3. The four SCRUM-55 gap scenarios have dedicated passing tests
  4. A user's session survives normal access-token expiry via silent refresh, and logout revokes the refresh token server-side

**Plans**: 4/4 plans executed (`/gsd-plan-phase 1` run 2026-08-14 — see the PLAN.md list below; the FB-01/02/03/16 task breakdown it was derived from is retained beneath it as audit history)

Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Auth-seam cleanup: filter-layer `ApiError` `fieldErrors` shape consistency, ApiError-body assertions on the two shallow SCRUM-55 gap tests, and a runnable gate on the typed-`UserPrincipal` seam (AUTH-01/02/03 — verification and cleanup only, no new feature work) · wave 1
- [x] 01-02-PLAN.md — Refresh-token tracer: `V12__create_refresh_tokens.sql`, `RefreshToken`/`RefreshTokenRepository`/`RefreshTokenService`, `POST /api/auth/refresh`, httpOnly cross-site cookie delivery, credentialed CORS, 15-minute access tokens (AUTH-04) · wave 1

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-03-PLAN.md — Reuse detection (D-03 user-wide revoke, behind a one-way decision checkpoint), `POST /api/auth/logout` single-device revocation (D-04), refresh rate limit, unit + IT suite, `docs/auth.md`/`docs/api-contracts.md` (AUTH-04) · wave 2

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-04-PLAN.md — Frontend proactive silent-refresh timer (D-05), server-revoking logout, and the two-stage session-expiry banner/dialog experience (D-06) (AUTH-04) · wave 3 — **outstanding: the task-3 `<human-check>` (banner-then-dialog manual QA with a running backend) was not run; Success Criterion 4 is met in code but not in verified fact until it is**

Source task breakdown (FB-01/02/03/16) and 2026-08-14 audit evidence:

- [x] FB-01: Custom AuthenticationEntryPoint + AccessDeniedHandler (401 vs 403 JSON ApiError) — **confirmed done** (2026-08-14 audit): `security/JsonAuthenticationEntryPoint.java`, `security/JsonAccessDeniedHandler.java`
- [x] FB-02: Introduce typed `UserPrincipal`, retire `AuthUtils`/`CurrentUserService` string-principal seam — **confirmed done** (2026-08-14 audit): `security/UserPrincipal.java` used via `@AuthenticationPrincipal` throughout controllers, no string-principal seam remains
- [x] FB-03: Close the four SCRUM-55 gap tests (unauth 401, real-JWT-path, delete-by-non-owner 403, GET nonexistent 404) — **confirmed done** (2026-08-14 audit): all four scenarios present verbatim in `TripControllerIT.java` (`listTrips_noAuthentication_returns401ViaJsonEntryPoint`, `createTrip_withRealJwt_authenticatesThroughFilterAndPersists`, `deleteTrip_nonOwner_returns403`, `getTrip_nonExistentId_returns404`)
- [ ] FB-16: Refresh token flow — `refresh_tokens` migration, issuance/rotation/revocation, reuse-detection, `/api/auth/refresh` + `/api/auth/logout`, frontend silent refresh (now covered by plans 01-02/01-03/01-04) — **backend complete as of 2026-08-14 (plans 01-02 + 01-03)**: `V12__create_refresh_tokens.sql`, `RefreshToken`/`RefreshTokenRepository`/`RefreshTokenService`, both endpoints, reuse detection with the D-03 user-wide revoke, D-04 logout, refresh rate limit, unit + IT coverage, docs. Remaining: the frontend half only (silent-refresh timer, logout wiring, session-expiry UX) — plan 01-04. The 2026-08-14 audit's "confirmed NOT started" finding is superseded.

### Phase 2: Exports, Completion & Search

**Goal**: Users can export their itinerary, see trip completion progress, and search/filter their trip list
**Depends on**: Phase 1 (sequencing convenience, not a hard blocker)
**Requirements**: EXPORT-01, EXPORT-02, EXPORT-03, SEARCH-01
**Jira epic**: `SCRUM-276` ("TRIP v2") — created 2026-08-06; `SCRUM-6` (TRIP) is Done/closed (see RISK-J2, resolved)
**Unblocked (verified via live Jira, 2026-08-06)**: `SCRUM-110`/REF-21 (pagination convention) is Done — SEARCH-01's prior "do not start until REF-21 merged" note no longer applies
**Success Criteria** (what must be TRUE):

  1. A trip's stops export as a valid `.ics` file that imports cleanly into a major calendar app
  2. A trip exports as a formatted PDF with header, ordered stops, and notes
  3. Trip responses expose enough data to compute completion percentage without a divide-by-zero on empty trips
  4. Users can search and filter their trip list using the shared paged-response convention

**Plans**: 4 plan items — `02-01` shipped pre-GSD (no PLAN.md exists or is needed); `/gsd-plan-phase 2` (2026-08-21) produced 3 PLAN.md files for the remaining work, numbered to match these items 1:1

Plans:
**Wave 1**

- [x] 02-01: Backend .ics generation endpoint + frontend export button — FB-04a/04b (EXPORT-01) — **confirmed done** (2026-08-14 audit, re-verified): `GET /api/trips/{id}/calendar.ics` (`TripExportController`/`IcsExportService`) + frontend download button in `trip-view.page.ts`, tracked Done as SCRUM-175/SCRUM-176 under legacy SOCIAL epic SCRUM-9 (not TRIP v2 SCRUM-276 — reparent or leave, team call). **Pre-existing code, never GSD-planned — there is deliberately no `02-01-PLAN.md`.**
- [x] 02-02-PLAN.md — PDF export: `com.github.librepdf:openpdf` dependency, `PdfExportService`, `GET /api/trips/{id}/export/pdf`, a new `client/mapbox/` triple for the server-side route-map snapshot (D-01/D-02/D-03/D-04), `sanitizeFilename` reuse (D-05), frontend download button — FB-05a/05b (EXPORT-02) · wave 1 · **needs a new backend `MAPBOX_TOKEN`** (non-blocking: absent token degrades to a map-less PDF)

**Wave 2** *(blocked on Wave 1 — file ownership of `trip.service.ts`, not a logical dependency)*

- [x] 02-03-PLAN.md — Trip completion percentage: shared `TripCompletion.percentage` helper (D-06/D-07), new `TripOwnerSummaryResponse` for the owner list with `TripSummaryResponse` left untouched for the discovery feed (D-08), `visitedStopCount`/`completionPercentage` on `TripResponse`, dashboard completion badge — FB-06 (EXPORT-03) · wave 2

**Wave 3** *(blocked on Wave 2 — `searchOwnedTrips` re-fetches through the `TripOwnerSummaryResponse` projection 02-03 creates)*

- [x] 02-04-PLAN.md — Trip list search + filter on `GET /api/trips`: `searchOwnedTrips` on the existing `TripSearchRepository` (D-09/D-11) matching title, tags and stop place-names (D-10), status/visibility/start-date-range/duration filters ANDed (D-12/D-13/D-14), debounced dashboard search + filter bar (D-15), stale frontend `TripStatus` union fixed, and the phase's `docs/api-contracts.md` pass — FB-07 (SEARCH-01) · wave 3. (Search exists only for the public discovery feed at `/api/discovery/search`, not the owner's trip list — do not confuse the two)

### Phase 3: AI Quality & Scheduling

**Goal**: The AI itinerary experience is the headline "AI plans your trip" feature — day-by-day, time-aware, with meals and alternatives
**Depends on**: Phase 2 (sequencing convenience, not a hard blocker)
**Requirements**: AI-01, AI-02, AI-03
**Jira epic**: `SCRUM-278` ("AI v2") — created 2026-08-06; `SCRUM-8` (AI) closes 2026-08-17
**Unblocked (verified via live Jira, 2026-08-06)**: `SCRUM-244`/244a/244b (day/time scheduling foundation) is Done — FB-17's prior dependency note no longer applies
**Success Criteria** (what must be TRUE):

  1. A documented before/after benchmark shows measurably better Gemini itinerary output quality
  2. `suggestItinerary` returns day number, planned time, reasoning, and stop type per suggestion, including meal suggestions
  3. Users can view and select from 2-3 alternative suggestions for any suggested stop or meal slot

**Plans**: 3 plans (task breakdown from FB-08/17/18)

Plans:

- [ ] 03-01: Gemini prompt engineering pass — 10-trip benchmark, prompt/few-shot iteration, before/after doc — FB-08 — **confirmed NOT started** (2026-08-14 audit): no benchmark doc, `ItineraryPromptTemplate.java` unchanged from baseline
- [ ] 03-02: Extend `suggestItinerary`/`ItineraryPromptTemplate` for day/time/reasoning/stopType + meal suggestions — FB-17 (depends on 03-01) — **PARTIAL** (2026-08-14 audit): `SuggestedItinerary.SuggestedStop` has `order, name, latitude, longitude, reason` — reasoning field exists, but no `day`, `time`, `stopType`, or meal-suggestion fields
- [ ] 03-03: Frontend alternative-suggestion popups (2-3 candidates per slot) — FB-18 (depends on 03-02) — **confirmed NOT started** (2026-08-14 audit): `ai-suggestion-cards.component.ts` is single-suggestion only, no alternative/candidate logic

### Phase 4: Frontend & Map Polish

**Goal**: The map and stop-entry experience feel like a real product, not a prototype
**Depends on**: Phase 3
**Requirements**: POLISH-01, POLISH-02, POLISH-03
**Jira epic**: `SCRUM-277` ("ROUTE v2") for FB-10; `SCRUM-276` ("TRIP v2") for FB-15; FB-11 (dark mode) has no natural epic among the current set — file under `SCRUM-281` (DOCS v2) or flag at grooming for a dedicated UX/POLISH epic
**Success Criteria** (what must be TRUE):

  1. The optimized route renders as a polyline; dense stop clusters collapse into aggregate pins at low zoom
  2. Dark mode toggles app-wide and persists across sessions
  3. Adding/editing a stop supports Mapbox place search with a working manual-coordinate fallback

**Plans**: 3 plans (task breakdown from FB-10/11/15)

Plans:

- [ ] 04-01: Route polyline rendering + stop marker clustering — FB-10 — **PARTIAL** (2026-08-14 audit): `trip-map.component.ts` renders route polyline and individual markers; no clustering (no supercluster, no `cluster: true` GeoJSON source)
- [ ] 04-02: App-wide dark mode toggle, persisted preference — FB-11 — **confirmed NOT started** (2026-08-14 audit): no dark-mode/`prefers-color-scheme` code anywhere in `frontend/src`
- [ ] 04-03: Mapbox Search Box place search in stop-add/edit flow, manual-coordinate fallback, editable coordinates in edit-stop-form — FB-15a/15b/15c — **confirmed NOT started** (2026-08-14 audit): no `SearchBox` references anywhere in frontend

### Phase 5: Notifications, Observability & PWA

**Goal**: The app has production-grade signals (email, error tracking) and a documented path to native/offline
**Depends on**: Phase 4
**Requirements**: NOTIFY-01, OBSERVE-01, PWA-01, PWA-02
**Success Criteria** (what must be TRUE):

  1. New users receive a confirmation email; trip owners receive a reminder email before trip start
  2. Unhandled exceptions in production are captured in Sentry with release tagging
  3. Users can view previously-loaded trips while offline, with a clear offline indicator
  4. A Capacitor build attempt produces either a working APK/IPA or a documented blocker list

**Plans**: 4 plans (task breakdown from FB-09/12/13/14)
**Jira epic**: `SCRUM-280` ("DEVOPS v2") — created 2026-08-06; `SCRUM-10` (DEVOPS) closes 2026-08-17. FB-09 optionally splits to `SCRUM-275` (AUTH v2) at grooming, per fall plan.
**Note**: `SCRUM-248` (Dockerize backend + Render/Neon Postgres deploy, live Jira To Do ticket not otherwise referenced in the fall/winter docs — see RISK-J3) may intersect with this phase's deploy-adjacent config work; check its status before starting 05-01.

Plans:

- [ ] 05-01: Signup confirmation + trip reminder emails via free-tier provider, scheduled job — FB-09 — **confirmed NOT started** (2026-08-14 audit): no email/mail-sending code in backend service layer
- [ ] 05-02: Sentry integration (backend + frontend), release tagging — FB-12 — **confirmed NOT started** (2026-08-14 audit): no Sentry SDK in `pom.xml`/`package.json`
- [ ] 05-03: PWA offline caching for saved trips (read-only), `ngsw-config.json` `dataGroups` — FB-13 — **confirmed NOT started** (2026-08-14 audit): `ngsw-config.json` exists with `assetGroups` only (app-shell scaffold), no `dataGroups` for API caching
- [ ] 05-04: Native Capacitor build spike (Android, iOS if available), `docs/native-build-spike.md` writeup — FB-14 — **confirmed NOT started** (2026-08-14 audit): no `docs/native-build-spike.md`, no Capacitor config

### Phase 6: Community & Social

**Goal**: TripFlowAI has a working social layer — a TikTok-style "For You" feed of PUBLIC trips, engagement actions, ratings, and a minimal user profile, with lightweight interest-based ranking. The SCRUM-9 epic goes from reserved-but-empty to functional.
**Depends on**: Phase 5 (soft — independent in practice, sequenced after polish/observability by convention in the fall plan)
**Requirements**: SOCIAL-01, SOCIAL-02, SOCIAL-03, SOCIAL-04, SOCIAL-05, SOCIAL-06, SOCIAL-07
**Jira epic**: `SCRUM-279` ("SOCIAL v2") — created 2026-08-06; `SCRUM-9` (SOCIAL) closes 2026-08-17
**Scope expanded 2026-08-06 (Phase 6 discussion)**: original SOCIAL-01 (paginated/searchable discovery feed) restyled as a full-screen, TikTok-style vertical swipe feed per-trip, with two additions beyond the original milestone scope: a minimal user profile page (SOCIAL-05) and lightweight interest-based feed ranking (SOCIAL-06). Both folded into this phase rather than pushed to a new phase — see `.planning/phases/06-community-social/06-CONTEXT.md` for the full discussion.
**Success Criteria** (what must be TRUE):

  1. Authenticated users can browse a full-screen, vertically-swipeable "For You" feed of other users' PUBLIC trips — trip name + major location + owner username fixed at top, description fixed at bottom, stop images/descriptions swipeable horizontally in the middle
  2. Feed ordering favors trips matching the viewer's stored interests before falling back to recency/all-public trips
  3. Users can like/save/clone a trip directly from the feed via an on-card action rail, without leaving the feed
  4. Trips with no stop photos fall back to a text-based card (stop descriptions) instead of breaking the swipe layout
  5. Users have a profile page showing username, join date, and their stored interests

**Plans**: 6/6 plans executed — `/gsd-plan-phase 6` run 2026-08-31 produced 6 PLAN.md files (`06-01` through `06-06`) mapping 1:1 onto the plan items below. The pre-planning audit findings each item carries are retained as history; the PLAN.md list beneath them is the executable breakdown, with waves assigned for parallel execution.
**Critical — reconcile with existing Jira tickets before implementing (do not duplicate)**: `SCRUM-71` (parent, **Done** as of 2026-08-12 — was stale "In Progress" in this doc, subtasks below confirm Done) already has subtasks `SCRUM-159` (71a, visibility enforcement), `SCRUM-160` (71b, discovery feed), `SCRUM-161` (71c, like endpoints), `SCRUM-162` (71d, clone), `SCRUM-163` (71e, search). **[UPDATED 2026-08-10] SCRUM-160/161/162/163 merged to `main` 2026-08-06 (after this roadmap entry was written) — the backend for discovery feed, search, like, and clone all ship and work today (`DiscoveryController`, `TripController`, documented in `docs/api-contracts.md`); paths are `GET /api/discovery/trips`/`GET /api/discovery/search`, matching this doc's assumption, not the earlier `/api/trips/discover` guess. Only the frontend consumer is outstanding — `trip.service.ts` has no methods calling any of these, and no discovery/feed/clone/like UI exists. Do not re-plan or re-implement the backend for 06-01/06-03; scope those plans to frontend wiring against the endpoints that already exist.** Also resolve `SCRUM-274` (404 existence-hiding standardization across owner-gated mutations) as part of this phase, not separately — it directly determines whether like/clone/rate return 403 or 404 on private trips. See `docs/social-features-traceability-audit.md` and RISK-J1/J3 in `.planning/RISKS.md`.

Plans:

- [x] 06-01: Discovery feed — **backend done, still confirmed** (`GET /api/discovery/trips`/`GET /api/discovery/search`, SCRUM-160/163, merged 2026-08-06); **frontend consumption still NOT started as of 2026-08-14 audit** — `trip.service.ts` has zero references to `/api/discovery`, no wiring added since 08-06 — FB-19a/19b/19c
- [x] 06-02: Frontend TikTok-style full-screen swipeable feed — vertical scroll between trips, horizontal swipe between stop images within a trip, header/footer overlay, no-photo text-card fallback — new, replaces the original FB-19b list-page assumption — **confirmed NOT started** (2026-08-14 audit): no discovery-feed page/component exists
- [x] 06-03: On-card action rail — like/save/clone tappable directly on the feed card — **PARTIAL, re-confirmed 2026-08-14**: like (`TripLikeService`, `POST`/`DELETE /{id}/like`) and clone (`TripCloneService`, `POST /{id}/clone`) backend done and wired in `TripController` (SCRUM-161/162, merged 2026-08-06); save/bookmark backend still missing entirely (no `SavedTrip` entity/service/endpoint); frontend wiring for all three still needed — FB-20a/20b, FB-21a/21b, FB-24 (resolve 404-vs-403 via SCRUM-274 first)
- [x] 06-04: Trip ratings (star, trip-level, join-table pattern) — FB-19d — **confirmed NOT started** (2026-08-14 audit): no `Rating` domain class, no rating controller/endpoint
- [x] 06-05: User profile page — username, join date, stored interests (new field/table) — SOCIAL-05 — **confirmed NOT started** (2026-08-14 audit): no `profile` path under `frontend/src`
- [x] 06-06: Interest-based feed ranking — order feed by interest-tag match against profile interests, fall back to recency — SOCIAL-06 (depends on 06-05) — **confirmed NOT started** (2026-08-14 audit): existing "interest" hits are AI itinerary-preferences fields, unrelated to feed ranking

PLAN.md files (2026-08-31):

**Wave 1**

- [x] 06-01-PLAN.md — Authenticated feed backend + Angular data seam: remove `/api/discovery/**` from `SecurityConfig` permitAll (one-way decision checkpoint), new `GET /api/discovery/feed` returning `FeedTripResponse` (owner username, description, tags, stops with photo URLs and text for the D-03 fallback), batched `findByStopIdIn` photo fetch, `DiscoveryService`/`feed.model.ts` (SOCIAL-01) · wave 1 · not autonomous

**Wave 2** *(blocked on Wave 1)*

- [x] 06-02-PLAN.md — TikTok-style swipe feed UI: `swiper` install behind a blocking package-legitimacy checkpoint, `/feed` route + full-screen outer vertical swiper (D-01), `feed-card` with pinned header/footer chrome (D-02), nested inner horizontal stop swiper, no-photo text card (D-03), paging and dashboard entry point (SOCIAL-01) · wave 2 · not autonomous

**Wave 3** *(blocked on Wave 2 — parallel with each other, zero file overlap)*

- [x] 06-03-PLAN.md — Save/bookmark backend (`V14__create_trip_saves.sql`, `SavedTrip`/`SavedTripId`/`SavedTripRepository`/`TripSaveService`, `POST`/`DELETE /api/trips/{id}/save`, `GET /api/trips/saved`) plus the D-04 on-card action rail wiring like, save and clone (SOCIAL-02/03/04) · wave 3
- [x] 06-05-PLAN.md — User profile: `V15__add_user_interests.sql` + `User.interests TEXT[]` behind a one-way decision checkpoint (D-07), `ProfileController`/`ProfileService`, `GET /api/profile` + `PATCH /api/profile/interests` with 20x50 limits, `/profile` page (SOCIAL-05) · wave 3 · not autonomous

**Wave 4** *(blocked on Wave 3 — parallel with each other, zero file overlap)*

- [ ] 06-04-PLAN.md — Trip ratings: `V16__create_trip_ratings.sql` with a 1-5 CHECK constraint, `TripRating` upsert (`ON CONFLICT DO UPDATE`) so re-rating replaces rather than duplicates, `POST /api/trips/{id}/rate` + `GET /api/trips/{id}/rating`, star control on the action rail (SOCIAL-07) · wave 4
- [ ] 06-06-PLAN.md — Interest-based ranking: `findPublicRankedByInterests` ordering tag-overlap first then recency with empty-interests fallback (D-05/D-06), paging-stability coverage, plus the phase's single `docs/api-contracts.md` + `docs/auth.md` documentation pass (SOCIAL-06) · wave 4

### Phase 7: Trip Tracking

**Goal**: Stop-arrival detection works automatically in the foreground, with the background/push path explicitly gated
**Depends on**: Phase 6 (no hard dependency — sequenced last among fall-break feature phases per the fall plan)
**Requirements**: TRACK-01, TRACK-02
**Success Criteria** (what must be TRUE):

  1. With the trip-view page open and location permission granted, arriving near an unvisited stop prompts arrival confirmation and marks it visited
  2. Push-notification arrival detection is not started until FB-14's native build spike has actually landed a working build

**Plans**: 2 plans (task breakdown from FB-25/26)
**Jira epic**: `SCRUM-276` ("TRIP v2") for FB-25; FB-26 unassigned/stretch, epic TBD if it ever unblocks
**Scope note (RISK-M1)**: TRACK-02/07-02 is conditional — do not schedule or commit to it externally until Phase 5's FB-14 native build spike actually reports a working build.

Plans:

- [ ] 07-01: Foreground geolocation watch, arrival-radius prompt, mark-visited via existing update-stop flow — FB-25 — **confirmed NOT started** (2026-08-14 audit): no `geolocation`/`watchPosition` references anywhere
- [ ] 07-02: Push notification for stop arrival — FB-26 (hard depends on 05-04 landing a working native build; otherwise out of scope for this milestone) — **confirmed NOT started** (2026-08-14 audit): no push-notification library or code; still hard-blocked on 05-04

### Phase 8: Trip Collaboration

**Goal**: A trip can have more than one owner — invite a collaborator, they accept, and both can edit the trip and its stops
**Depends on**: Phase 1 (needs the hardened `UserPrincipal`/auth seam for invite/accept identity checks) — otherwise independent of Phases 2-7
**Requirements**: COLLAB-01, COLLAB-02, COLLAB-03
**Jira**: `SCRUM-316` (parent story, "Multiple people can own a trip") with children `SCRUM-321`/`322`/`323` — real Jira tracking already exists (added 2026-08-11), just wasn't wired into any roadmap phase until now. No v2 epic assigned yet — closest fit is `SCRUM-276` (TRIP v2), flag at grooming to confirm or spin up a dedicated epic.
**Success Criteria** (what must be TRUE):

  1. A trip owner can invite another registered user as a collaborator via an invite/accept flow
  2. An accepted collaborator can edit the trip's details and stops with the same write access as the owner
  3. Only the owner can delete the trip or remove/manage other collaborators
  4. Concurrent edits by two collaborators don't corrupt trip state (last-write-wins is acceptable for v1 — no crash, no silently-dropped data)
  5. Non-owner, non-collaborator users still get 403 on a private trip, consistent with the existing ownership-check convention

**Plans**: 4 plans (task breakdown inferred from SCRUM-316/321/322/323 — verify against their actual descriptions during `/gsd-plan-phase 8`)

Plans:

- [ ] 08-01: Backend collaborator permission model — `trip_collaborators` join table (migration), ownership-check service extended to accept collaborator writes, not just owner — SCRUM-316/321 — **confirmed NOT started** (2026-08-14 audit): no `trip_collaborators` migration, no zero code footprint for collaboration anywhere
- [ ] 08-02: Invite/accept flow — invite endpoint (by email/username), accept endpoint, pending-invite state — SCRUM-322 — **confirmed NOT started** (2026-08-14 audit)
- [ ] 08-03: Frontend shared-edit UI — collaborator list on trip detail, invite UI, permission-aware edit controls — SCRUM-323 — **confirmed NOT started** (2026-08-14 audit)
- [ ] 08-04: Concurrent-edit handling — pick and implement a conflict strategy (last-write-wins for v1; optimistic locking as stretch) — **confirmed NOT started** (2026-08-14 audit)

### Phase 9: Winter Hardening & Sign-off

**Goal**: The deployed app is production-hardened, fully regression-tested, and documented for grading/portfolio use
**Depends on**: Phase 8 (all fall-break feature work, including collaboration, should be substantially complete before this phase's regression pass is meaningful)
**Requirements**: HARDEN-01, HARDEN-02, HARDEN-03, HARDEN-04, HARDEN-05, HARDEN-06
**Success Criteria** (what must be TRUE):

  1. Production hardening checklist passes (no dev-only endpoints, no leaked internals, clean dependency scan, rate limiting verified under concurrent load)
  2. Post-deploy smoke test runs automatically and fails loudly on breakage
  3. Retros are backfilled and docs are verified fresh against shipped code
  4. A full end-to-end regression pass is signed off against the deployed environment
  5. A repeatable demo/seed data script exists
  6. The top-level README accurately reflects final shipped scope with verified setup instructions

**Plans**: 6 plans (task breakdown from WP-01..08)
**Jira epic**: `SCRUM-280` ("DEVOPS v2") for WP-01/02/03/07; `SCRUM-281` ("DOCS v2") for WP-04/05/08 — both created 2026-08-06; `SCRUM-10`/`SCRUM-11` close 2026-08-17
**Note (RISK-M4)**: This phase stays a groomable backlog, not a fixed schedule, until fall-break outcomes are known — do not run `/gsd-plan-phase 9` until Phase 8 is substantially complete. `SCRUM-248` (Dockerize + Neon Postgres) should be cross-checked against WP-01/WP-03's assumptions before hardening begins (see RISK-M5).
**[NEW 2026-08-14]** Skipped detailed audit per RISK-M4 (correctly still not started as a discrete phase). Worth noting: some Phase-9-flavored hardening work is already happening piecemeal via SCRUM tickets outside this phase — recent merged PRs (#268 Mapbox token leak fix, #263/#252/#250 security-low cleanups, #257 CI coverage-floor work) overlap WP-01/WP-04 intent. When this phase is eventually planned, check for already-closed ground rather than assuming zero progress.

Plans:

- [ ] 09-01: Production hardening checklist — no dev-only endpoints/permissive CORS, no leaked internals, dependency scan clean/accepted — WP-01
- [ ] 09-02: Deploy automation smoke test script + load/perf test on rate-limited endpoints — WP-02, WP-03
- [ ] 09-03: Retro backlog catch-up + documentation freshness audit — WP-04, WP-05
- [ ] 09-04: Final end-to-end regression pass + consolidated sign-off checklist (folds in SCRUM-74a/b/c/d if not already done) — WP-06
- [ ] 09-05: Demo/seed data script — WP-07 (fold in the "with app vs without app" presentation-comparison note from the docs/todos backlog)
- [ ] 09-06: Grading/portfolio README pass — WP-08

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9

| Phase | Plans Complete | Status | Completed |
|-------|-----------------|--------|-----------|
| 1. Auth Seam Hardening | 4/4 | Complete    | 2026-08-17 |
| 2. Exports, Completion & Search | 3/3 | Complete    | 2026-08-22 |
| 3. AI Quality & Scheduling | 0/3 (03-02 partial: `reason` field exists) | Not started | - |
| 4. Frontend & Map Polish | 0/3 (04-01 partial: polyline yes, clustering no) | Not started | - |
| 5. Notifications, Observability & PWA | 0/4 | Not started | - |
| 6. Community & Social | 6/6 | In Progress|  |
| 7. Trip Tracking | 0/2 | Not started | - |
| 8. Trip Collaboration | 0/4 | Not started | - |
| 9. Winter Hardening & Sign-off | 0/6 | Not started (groomable backlog, by design) | - |

*Row-level "Plans Complete" counts full plan items only (`[x]` above); PARTIAL items are called out in the phase's Plans list but not counted toward the fraction. Verified against codebase 2026-08-14 — see `.planning/audit/` or plan-item notes above for evidence.*
