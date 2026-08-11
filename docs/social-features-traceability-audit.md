# Social Features Traceability Audit

**Date:** 2026-07-30
**Scope:** Three proposed features — **For You Feed**, **Clone Trip**, **Trip Tracking** — cross-referenced against the GitHub repo (`main@9ef3c20` + open PRs #169/#170/#171), the Jira board (`SCRUM` project), `docs/TripFlow_fall_Break_Plan.md`, `docs/TripFlow_Winter_Plan.md`, and the current MVP/epic roadmap.

> **CORRECTION (2026-08-10):** SCRUM-160/161/162/163 (discovery feed, search, like, clone) merged to `main` 2026-08-06 — after this audit was written. The "Not implemented" verdicts for **For You Feed** and **Clone Trip** below are stale on the **backend** half only. Current reality: **backend shipped, frontend not built** — `DiscoveryController` (`GET /api/discovery/trips`, `GET /api/discovery/search`) and `TripController` (`POST /{id}/clone`, `POST`/`DELETE /{id}/like`) all exist and work, documented live in `docs/api-contracts.md`; but `frontend/src/app/core/services/trip.service.ts` has no methods calling any of them, and no discovery/feed/clone/like UI exists anywhere under `frontend/src/app`. Do not re-implement the backend — only the frontend consumer is outstanding. Rest of this document's gap analysis (Sections 2-5: creator info, ratings, save/bookmark, Trip Tracking scope) is unaffected and still accurate.

**Headline finding:** Two of the three proposed features are **already tracked in Jira** — not missing. **SCRUM-71** ("Public/private toggle + discovery feed endpoints," Pratham, subtasks SCRUM-71b/71c/71d) and **SCRUM-72** ("Photo upload & review UI per stop," Neel, subtask SCRUM-72b) sit under the `SCRUM-9` (SOCIAL) epic, fully specced with acceptance criteria, To Do. `docs/TripFlow_fall_Break_Plan.md` already independently drafted the same scope as FB-19/20/21 — this session briefly (and incorrectly) treated that as a duplicate to remove, on the theory that the tickets' `week-12` label meant "current semester, not fall break." Confirmed with Tanish: all fall-break items are correctly fall-scoped, including these — this semester's feature work is done as of the Aug 6 presentation, and this whole area (SCRUM-71/72 included) rolls into fall/winter. FB-19/20/21 are restored, unchanged in original scope, in the fall plan. The one thing worth keeping from that detour: SCRUM-71/72 already exist as real Jira tickets covering nearly this scope, so when FB-19/20/21 get created for real, they should extend those existing tickets rather than open new duplicate ones (Section 2 of the fall plan now says this explicitly).

**Third feature (Trip Tracking) is genuinely missing** — see the matrix below. All newly-identified gaps across all three features (search on the feed, creator info, ratings, save/bookmark, and Trip Tracking's foreground MVP + blocked push-notification stretch) have been added directly to the fall plan as FB-19c, FB-19d, FB-24, FB-25, and FB-26 — see "Documentation Updates Applied" at the end.

---

## 1. Feature Traceability Matrix

| Proposed Feature | GitHub Status | Jira Status | Fall Plan Status | Winter Plan Status | Overall Recommendation |
|---|---|---|---|---|---|
| **For You Feed** | **[UPDATED 2026-08-10] Backend shipped, frontend not built.** `GET /api/discovery/trips` and `GET /api/discovery/search` (`DiscoveryController`) merged 2026-08-06 (SCRUM-160/163) — the "no `GET /api/discovery/**`" line below is stale, kept for history. No discovery/feed page in `frontend/src/app/pages`, no calling code in `trip.service.ts` — that half is still genuinely unbuilt. `TripSummaryResponse.coverPhotoUrl` exists as a stub field (always `null`); `Trip.tags` exists and is reusable. No creator-name exposure, no rating field, no save/bookmark, no comment, no share. | **Covered as backlog.** SCRUM-71 (feed) + SCRUM-71c (like) + SCRUM-72/72b (photo gallery + per-stop review + feed thumbnails) already scoped with full acceptance criteria, To Do. Search (`?q=`), creator info, numeric ratings, save/bookmark, share, and comments are **not** in any existing ticket's AC. | **Covered.** FB-19 (discovery feed + search), FB-20 (like) — same scope as SCRUM-71/71b/71c, drafted independently. Reconcile the two rather than building both. | Not mentioned (correctly — this is fall scope). | Fall-break work. When creating tickets, extend SCRUM-71/72 rather than opening duplicates. Newly-found gaps (search, creator info, ratings, save) added to the fall plan as FB-19c/19d/24 — see Section 4. |
| **Clone Trip** | **[UPDATED 2026-08-10] Backend shipped, frontend not built.** `POST /api/trips/{id}/clone` (`TripController`) merged 2026-08-06 (SCRUM-162) — the "No `POST /api/trips/{id}/clone`" line below is stale, kept for history. No clone button anywhere in `trip-view`, no calling code in `trip.service.ts` — that half is still genuinely unbuilt. | **Covered as backlog.** SCRUM-71d (subtask of SCRUM-71) fully specs deep-copy semantics, ownership, visibility reset, and the `"Copy of {title}"` rename, with explicit ACs. To Do. | **Covered.** FB-21 — same scope as SCRUM-71d, with a couple of differing details (see Section 3). | Not mentioned (correctly). | Fall-break work. Reconcile FB-21 with SCRUM-71d before implementing (403-vs-404, title rename) rather than building two versions. |
| **Trip Tracking** (auto arrival detection, auto mark-visited, prompt for review/photo/notes) | **Partially implemented.** Manual "mark visited" is fully shipped end-to-end: `Stop.status` (`PLANNED`/`VISITED`/`SKIPPED`) exists in the entity, migration, `CreateStopRequest`/`UpdateStopRequest`/`StopResponse`, `StopController`'s `PUT` endpoint, and the `edit-stop-form` UI component (SCRUM-250, merged 2026-07-30). Automatic GPS-based arrival detection: 0%. Completion percentage: 0% (no field computes it anywhere). Review/rating prompts on arrival: 0%. | **Not tracked anywhere.** No ticket for GPS/geolocation, arrival detection, completion percentage, or arrival-triggered prompts exists in the whole board (searched `geolocation`, `arriv*`, `completion`, `progress`, `visited`, `background`, `push notif*`). | FB-06 existed but was **stale** — it described adding a "new boolean field on Stop," not realizing the field already shipped via SCRUM-250. Corrected in this session (see below) and rescoped to just the completion-percentage piece. | Not mentioned. | The manual half already exists — nothing to build there. The automatic/GPS half is genuinely missing entirely and is the single largest new-infrastructure item across all three proposals (see Section 2). Recommend scoping down to a foreground-only MVP rather than true background tracking — see Section 5. |

---

## 2. Missing Foundations

### For You Feed
- **Backend, in progress per Jira:** `GET /api/discovery/trips` (SCRUM-71b) — depends on the pagination convention from SCRUM-110 (REF-21), which is **already Done**, so no blocker there. Like/unlike + `trip_likes` migration + `like_count` (SCRUM-71c). Photo gallery + per-stop review field + feed-card thumbnail derivation (SCRUM-72b).
- **Backend, missing, no ticket:**
  - Search/`?q=` filter on the feed — SCRUM-71b's AC doesn't include it, yet `docs/qa/prod-regression-photos-social.md` (this session's SCRUM-74c checklist) explicitly calls for "search by title, case-insensitively" as part of the same social surface.
  - Creator info on trip responses — `User` only has `username` (no display name/avatar/bio), and neither `TripResponse` nor `TripSummaryResponse` exposes even `ownerUsername` today (only `ownerId: Long`). A feed card showing "by whom" needs this joined in.
  - Numeric trip rating — SCRUM-72b's "review field" is a single owner-editable text note per **stop**, not a multi-user rating. No star/numeric rating exists anywhere in the domain model.
  - Save/bookmark — distinct from "like" in the proposal; no join table, no endpoint, no ticket.
  - Share — no share-link concept exists. Current export work (FB-04/05 in the fall plan) is file download only (.ics/PDF), not a shareable URL. **Also:** `SecurityConfig` denies all requests by default except `/api/auth/**` and `/actuator/health` — meaning even a `PUBLIC` trip's `GET /api/trips/{id}` currently 401s for a logged-out visitor. A "share this trip" link handed to someone without an account wouldn't work today; sharing implies either an unauthenticated public-read exception or accepting share = "share with other logged-in users only."
  - Comment — no entity, no ticket. (User's proposal already flagged this as optional "if appropriate.")
- **Frontend:** Discover page/tab, navigation entry, trip-card component (thumbnail, tags, creator, like/save buttons) — 0% built, matches SCRUM-71/72's still-To-Do status.
- **Testing:** SCRUM-71b/71c/71d/72b's own ACs call for IT coverage (query-count test, idempotency test, deep-copy invariant test) — none written yet, consistent with "To Do."
- **Documentation:** `docs/api-contracts.md` has no Discovery/Like/Clone/Review sections yet — should be added once SCRUM-71/72 actually ship (same pattern as this session's Photo Upload section fix).

### Clone Trip
- **Backend:** `POST /api/trips/{id}/clone` (SCRUM-71d) — To Do, fully specced, transactional, shared-`Place` deep copy.
- **Frontend:** Clone button + navigate-to-edit-page flow — 0% built.
- **Testing:** IT verifying deep-copy semantics + shared-`Place` invariant — not written (per SCRUM-71d's own AC).

### Trip Tracking
- **Already exists, needs nothing new:** manual visited toggle (`Stop.status`, full stack).
- **Missing — data model:** `Stop` has no `visitedAt` timestamp (only `status`), so "when did they arrive" isn't recorded even for manual toggles today. Would need one nullable `TIMESTAMPTZ` column if arrival time matters for anything (e.g. a trip timeline/history view).
- **Missing — geolocation:** No geolocation code anywhere in the frontend. Capacitor's Geolocation plugin isn't wired in; `FB-14` ("Native Capacitor build spike") is still an unfinished stretch-goal spike, not a working native shell. This matters because:
  - **Foreground-only** geolocation (watch position while the trip-view page is open, check distance-to-stop, no native shell needed) is realistic today — the web Geolocation API works in any modern mobile browser/PWA.
  - **Background** geolocation (detect arrival while the app isn't open) requires either a native wrapper (blocked on FB-14 actually landing) or a service-worker-based approach that's unreliable across iOS Safari/PWA in particular. This is the single biggest infrastructure gap in the whole proposal — treat "automatic" tracking as foreground-only for a capstone timeline, not full background tracking.
- **Missing — notifications:** FB-09 (fall plan) only covers email (welcome + trip-reminder), not push. Prompting "add a review" on arrival needs either an in-app foreground prompt (feasible without new infra) or true push notifications (blocked on the same native-shell gap as background geolocation).
- **Missing — completion percentage:** cheapest gap here — a derived `visitedStopCount`/`stopCount` (or precomputed `completionPercent`) on `TripResponse`. No migration needed, `status` already exists. This is FB-06 as corrected in this session.
- **Missing — rating/review-on-arrival:** depends on the numeric-rating gap already identified under For You Feed.

---

## 3. Documentation Gaps

| Location | Gap | Status |
|---|---|---|
| `docs/api-contracts.md` | Photo Upload section said "not yet implemented" months after SCRUM-152/153 shipped it. | **Fixed** in PR #170 (this session, earlier). |
| `docs/api-contracts.md` | `POST /api/auth/register` example showed a `displayName` field; the real `RegisterRequest` DTO uses `username` (no `displayName` field exists anywhere in the `User` entity). | **Fixed** in this session — see corrections below. |
| `docs/TripFlow_fall_Break_Plan.md` | FB-19/FB-20/FB-21 (fall-scope, correctly) have a few conflicting details vs. the real SCRUM-71/72 specs: endpoint path `/api/trips/discover` vs. real `/api/discovery/trips`; `403` vs. the real `404`-to-avoid-existence-leak on private-trip like/clone attempts; no title rename on clone vs. the real `"Copy of {title}"`. | **Flagged, not reconciled** — cross-reference notes added at each FB-19/20/21 entry pointing at the real Jira spec; whoever implements should reconcile which convention ships. |
| `docs/TripFlow_fall_Break_Plan.md` | FB-06 described adding "a new boolean field on Stop" as if it didn't exist — stale relative to SCRUM-250 (merged same day). | **Fixed** in this session — FB-06 rescoped to just the completion-percentage gap. |
| `docs/ajf-module-b.md` | Doesn't exist. `docs/ajf-module-a.md` is a maintained per-sprint log; SCRUM-9/71/72 are explicitly labeled `ajf-module-b` / "Part of AJF Module B" in their Jira descriptions, with no equivalent doc. | **Not fixed** — flagged as a new work item (Section 4, item I) rather than authored blind; whoever owns Module B should write it. |
| Jira `SCRUM-161` (SCRUM-71c) | Description names the migration file `V7__create_trip_likes.sql`. The repo is already at `V8__create_stop_photos.sql` (`backend/src/main/resources/db/migration/`) — `V7` is taken by `V7__stop_scheduling.sql`. The real filename at implementation time will be `V9__...`. | **Not fixed** — this is a Jira ticket, not a repo file; flagged for correction when SCRUM-71c is picked up (Section 4, item H). |
| Jira `SCRUM-71b` | No search/`?q=` scenario in its AC, despite `docs/qa/prod-regression-photos-social.md` (this session) expecting search on the same discovery surface. | **Not fixed** — proposed as a new subtask (Section 4, item A). |

---

## 4. New Work Items

These are additive to FB-19/20/21 and SCRUM-71/72, not replacements — those already cover the bulk of "For You Feed" and all of "Clone Trip." Only genuinely missing pieces are covered here. Item A (search) was already in FB-19/FB-19a's original scope, so no new item was needed for it. Items B, C, D, F, and G have been added directly to `docs/TripFlow_fall_Break_Plan.md` as **FB-19c, FB-19d, FB-24, FB-25, and FB-26** respectively — reproduced below for reference/traceability, not as separate pending drafts. Items H, I, J are Jira-only corrections/additions and remain to action separately (not fall-plan items).

### A. Already in FB-19/FB-19a's scope — search filter on discovery feed
- **Summary:** Add `?q=` case-insensitive title filter to `GET /api/discovery/trips`
- **Epic:** SCRUM-9 (SOCIAL)
- **Priority:** Medium
- **Labels:** api, social, search
- **Description:** SCRUM-71b's current AC has no search/filter scenario, but `docs/qa/prod-regression-photos-social.md` expects "search by title, case-insensitively" on the same discovery surface (originally SCRUM-74c). Add an optional `?q=` param to `GET /api/discovery/trips`, matching titles case-insensitively, same pattern as any search work in FB-07 (trip-list search) once that lands — don't invent a second implementation of the same case-insensitive-title-match logic.
- **Acceptance Criteria:**
  ```
  Given PUBLIC trips with varying titles
  When GET /api/discovery/trips?q=paris is called
  Then only PUBLIC trips whose title contains "paris" (case-insensitive) are returned
  And omitting q returns the unfiltered feed, unchanged from SCRUM-71b's existing behavior
  ```
- **Dependencies:** SCRUM-71b

### B. Now FB-19c in the fall plan — Expose creator info on trip responses
- **Summary:** Add owner username to TripSummaryResponse/TripResponse
- **Epic:** SCRUM-9 (SOCIAL)
- **Priority:** Medium
- **Labels:** api, social, api-contract-change
- **Description:** Neither `TripSummaryResponse` nor `TripResponse` exposes anything about the trip's owner beyond `ownerId: Long`. A discovery feed showing "creator information" (as proposed) needs at least a display name. Add `ownerUsername` (reusing the existing `User.username` — no new field on `User` needed) to both DTOs, additive/non-breaking.
- **Acceptance Criteria:**
  ```
  Given a trip owned by a user with username "alex"
  When GET /api/discovery/trips or GET /api/trips/{id} is called
  Then the response includes ownerUsername: "alex"
  And existing consumers of these DTOs are unaffected (additive field only)
  ```
- **Dependencies:** none

### C. Now FB-19d in the fall plan — Trip ratings
- **Summary:** Numeric trip rating (1-5 stars)
- **Epic:** SCRUM-9 (SOCIAL)
- **Priority:** Medium
- **Story Points:** 3
- **Labels:** feature, social, database, api-contract-change
- **Description:** SCRUM-72b's "review field" is a single owner-editable text note per stop — not a rating, and not per-trip. Add a separate trip-level numeric rating (1-5) that other users can leave on a PUBLIC trip, plus an average + count on the trip response, following the same join-table pattern as `trip_likes` (SCRUM-71c) so a user can't rate the same trip repeatedly.
- **Technical Notes:** New migration: `trip_ratings(user_id, trip_id, rating SMALLINT, created_at)`, PK `(user_id, trip_id)`. Compute `average_rating`/`rating_count` via aggregate query, same reasoning as SCRUM-71c's "don't hand-maintain a denormalized counter, use `COUNT`/`AVG`" — though re-evaluate if feed-read performance demands a denormalized column later.
- **Acceptance Criteria:**
  ```
  Given a PUBLIC trip
  When a user submits a rating 1-5
  Then their rating is recorded (one rating per user per trip, re-rating updates not duplicates)
  And the trip response includes averageRating and ratingCount

  Given a PRIVATE trip
  When a non-owner attempts to rate it
  Then the response is 404 (matching the existence-hiding convention SCRUM-71c/71d already established for like/clone)
  ```
- **Dependencies:** SCRUM-71 (visibility enforcement pattern), soft dependency on SCRUM-72b landing first for UI consistency

### D. Now FB-24 in the fall plan — Save/bookmark a trip
- **Summary:** Save (bookmark) a public trip without cloning it
- **Epic:** SCRUM-9 (SOCIAL)
- **Priority:** Low
- **Story Points:** 3
- **Labels:** feature, social, database
- **Description:** Distinct from "like" (a public signal) and "clone" (a full copy) — a private list of trips the user wants to revisit, reusing the exact `trip_likes` migration/endpoint pattern (SCRUM-71c) with a `saved_trips` table instead.
- **Acceptance Criteria:**
  ```
  Given a PUBLIC trip
  When a user saves it
  Then it appears in that user's "Saved trips" list
  And saving twice is idempotent (no duplicate rows)
  ```
- **Dependencies:** SCRUM-71c (reuses its pattern)

### E. FB-06 (fall plan) — already corrected in this session
See Section 3 — no new ticket needed, existing fall-plan item rescoped in place.

### F. Now FB-25 in the fall plan — Foreground stop-arrival detection (MVP)
- **Summary:** Detect arrival at a planned stop while the trip is open (foreground only)
- **Epic:** SCRUM-6 (TRIP)
- **Priority:** Medium
- **Story Points:** 5
- **Labels:** feature, location, frontend
- **Description:** While the user has the trip-view page open, watch device location (web Geolocation API — no native plugin/shell required) and compare against each unvisited stop's lat/lng. Within a configurable radius (e.g. 100m), prompt the user to confirm arrival and mark the stop `VISITED` (reusing the existing `PUT /api/trips/{tripId}/stops/{stopId}` endpoint — no backend change needed for the mark-visited part itself). Explicitly **foreground-only** — no background detection, no push notification. See Section 5 for why background/push is out of scope for now.
- **Acceptance Criteria:**
  ```
  Given the trip-view page is open and the user grants location permission
  When the device's location comes within the configured radius of an unvisited stop
  Then the user is prompted to confirm arrival
  And confirming marks the stop VISITED via the existing update-stop flow

  Given location permission is denied
  Then the trip view still functions normally, with no tracking and no error state
  ```
- **Dependencies:** none (does not require FB-14/native build)

### G. Now FB-26 in the fall plan — Push notification for stop arrival (stretch, blocked)
- **Summary:** Push notification on stop arrival (background)
- **Epic:** SCRUM-10 (DEVOPS)
- **Priority:** Low
- **Labels:** stretch-goal, mobile, notifications
- **Description:** True background arrival detection + push notification, as opposed to item F's foreground-only MVP. **Hard dependency:** requires a native shell (Capacitor), which `FB-14` in the fall plan is only a build *spike* for, not a finished deliverable — without it, iOS PWA background geolocation/push is unreliable-to-unsupported. Do not schedule this until FB-14 concludes with a working native build.
- **Dependencies:** FB-14 (native Capacitor build spike) must land successfully first

### H. Jira correction (not a new ticket) — fix SCRUM-161's migration filename
- SCRUM-161 (SCRUM-71c)'s description names `V7__create_trip_likes.sql`. The repo is already past `V8` (`V8__create_stop_photos.sql`). Whoever picks up SCRUM-71c should name it `V9__create_trip_likes.sql` (or whatever's next at actual implementation time) — flagging so it isn't copy-pasted stale.

### I. New Task under SCRUM-11 (DOCS) — Write docs/ajf-module-b.md
- **Summary:** Create AJF Module B log, mirroring ajf-module-a.md
- **Epic:** SCRUM-11 (DOCS)
- **Priority:** Medium
- **Labels:** docs, presentation
- **Description:** SCRUM-9/71/72 (and the SCRUM-10 DEVOPS epic) are explicitly labeled/described as "Part of AJF Module B," but no `docs/ajf-module-b.md` exists — only Module A's log does, current through Sprint 4. Create the Module B equivalent once its owner (whoever's driving SOCIAL/DEVOPS work) is settled, following Module A's format (per-sprint entries + a final Presentation Notes section).
- **Dependencies:** none — can start now with whatever's landed so far, doesn't need to wait for SCRUM-71/72 to ship

### J. Jira correction (not a new ticket) — SCRUM-74c description
- Once SCRUM-71/72 ship, `SCRUM-171` (SCRUM-74c's regression subtask, currently "In Progress") should be updated to actually test the real endpoints instead of the "not implemented — descope" rows currently in `docs/qa/prod-regression-photos-social.md`. Flagged here so it isn't forgotten once the feature exists.

---

## 5. Recommended Additional Features

Kept small, reusing existing architecture, and scoped to what's realistic alongside SCRUM-71/72 rather than competing with them:

1. **Completion percentage** (item E / corrected FB-06) — near-zero-cost given `Stop.status` already ships; pairs naturally with the discovery feed showing "3/5 stops visited" on a trip card.
2. **Save/bookmark** (item D) — literally the same migration/endpoint shape as `trip_likes` (SCRUM-71c), cheap to add right alongside it rather than as a separate later effort.
3. **Trip rating** (item C) — rounds out "For You Feed" with the one piece (numeric rating) SCRUM-72b's text-review field doesn't cover, reusing the exact anti-double-submit pattern SCRUM-71c already established.
4. **Foreground-only stop-arrival detection** (item F) — the realistic version of "Trip Tracking" that fits inside the current architecture (no native shell needed), versus true background tracking, which is blocked on FB-14 (an unfinished stretch-goal spike) and not realistic for a capstone timeline. Recommend explicitly *not* attempting true background/push tracking (item G) unless FB-14 lands first — a half-working background-tracking feature is worse for a demo than a clearly-scoped foreground one that works reliably.

Deliberately **not** recommending: comments (proposal itself flagged as optional, and it's the one item here needing moderation/abuse considerations disproportionate to a capstone timeline), a public unauthenticated share link (would require reworking `SecurityConfig`'s deny-by-default posture specifically for this, a bigger and riskier change than its payoff justifies right now).

---

## Documentation Updates Applied This Session

- `docs/TripFlow_fall_Break_Plan.md`: FB-19/FB-20/FB-21 confirmed correctly fall-scoped (briefly removed, then restored, with cross-reference notes to the real SCRUM-71/72 specs added at each). Rescoped FB-06 to the completion-percentage gap only — was stale, described work SCRUM-250 already shipped. Added FB-19c (creator info), FB-19d (ratings), FB-24 (save/bookmark), FB-25 (foreground stop-arrival MVP), and FB-26 (push notification stretch, blocked on FB-14) for the gaps this audit found. Updated the summary table, total SP (~62 → ~93), Section 2/3/4 sequencing notes, and the ticket-creation instruction list accordingly.
- `docs/api-contracts.md`: fixed the register-endpoint example (`displayName` → `username`, matching the real `RegisterRequest` DTO).
- This file (`docs/social-features-traceability-audit.md`) added as the audit record.
