# TripFlow AI — Fall Break Work Plan

**Purpose:** Work items to be completed during the semester-5→semester-6 break (Aug 17, 2026 – early Jan 2027) so that Semester 6 (winter term) starts with meaningful features already in place and a lighter sprint load through the final term.

**Ground rules for break:**
- Real commits, real PRs, real merges to `main` allowed.
- Serialize-point rule stays in force: async Slack ping before touching `pom.xml`, `application.properties`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `BaseEntity.java`.
- Neel remains required reviewer on any DTO / API contract change.
- Pratham remains standing reviewer on any auth-adjacent PR.
- Nobody merges their own PR without at least one approval, even over break.
- No dependency bumps or framework upgrades without team review.
- Rotate a monthly 30-min sync (Sept / Oct / Nov / Dec) to catch drift early.

**Instructions for future ticket creation:**
When Tanish provides this file back in January, create Jira issues in project `SCRUM` on `atanish6.atlassian.net`. Use existing epics where noted. Use `contentFormat: markdown` on descriptions. Set `customfield_10016` for story points. Do not assign a sprint — these will be groomed into Fall Sprint 1 or the backlog at that time.

---

## SECTION 1 — Epic mapping reference

| Epic Key | Epic Name | Use for |
|---|---|---|
| SCRUM-6 | TRIP | Trip feature additions |
| SCRUM-7 | ROUTE | Route/optimization additions |
| SCRUM-8 | AI | Gemini / AI itinerary work |
| SCRUM-9 | SOCIAL | Community / discovery / sharing |
| SCRUM-10 | DEVOPS | Deployment / infra / observability |
| SCRUM-11 | DOCS | Documentation / onboarding |
| SCRUM-83 | AUTH | Auth / security work |
| SCRUM-84 | REFACTOR | Tech debt / quality |
| SCRUM-87 | Config & Deploy | Config / profiles / secrets |

If any of the above is inaccurate at ticket-creation time, verify via `getVisibleJiraProjects` + epic search first.

---

## SECTION 2 — Break work items

### FB-01 · Story · Custom auth entry point (401 vs 403) — REF-11 follow-through
- **Epic:** SCRUM-83 (AUTH)
- **Assignee:** Pratham
- **Priority:** High
- **Story Points:** 2
- **Labels:** auth, refactoring, unblocks-tests
- **Components:** security, api
- **Description:**
  ```
  Currently, requests without a valid JWT fall through to Spring Security defaults and return 403 Forbidden with an HTML body. This is semantically wrong (should be 401 Unauthorized for missing/invalid credentials) and produces HTML in a JSON API.

  Implement a custom AuthenticationEntryPoint and AccessDeniedHandler that return JSON ApiError responses with correct HTTP codes:
  - Missing/invalid/expired token → 401 Unauthorized
  - Authenticated but not permitted → 403 Forbidden
  ```
- **Business Value:** Correct HTTP semantics; unblocks integration tests currently asserting 403-with-TODO for the unauthenticated case; enables Neel's frontend to distinguish "log in again" from "you can't do this".
- **Technical Notes:** Serialize-point: `SecurityConfig.java`. Coordinate with anyone touching auth. Follow-up: sweep `TripIntegrationIT` / `StopIntegrationIT` / `AuthControllerIT` to remove the 403-vs-401 TODO comments and assert the correct code.
- **Acceptance Criteria:**
  ```
  Given a request to a protected endpoint without a token
  When the request is processed
  Then the response is 401 with a JSON ApiError body

  Given a request with a valid token but insufficient permissions
  When the request is processed
  Then the response is 403 with a JSON ApiError body

  And all integration tests previously asserting 403 for the no-token case are updated to assert 401
  ```
- **Dependencies:** none
- **Blocks:** FB-02, FB-03

---

### FB-02 · Story · Introduce UserPrincipal, retire string-principal seam — REF-13c follow-through
- **Epic:** SCRUM-83 (AUTH)
- **Assignee:** Pratham
- **Priority:** High
- **Story Points:** 3
- **Labels:** auth, refactoring, breaking-change, unblocks-tests
- **Components:** security, api
- **Description:**
  ```
  Today, JwtAuthFilter sets a String principal and controllers resolve the user via AuthUtils.currentUserId(authentication.getName()). Replace this with a proper typed UserPrincipal (implementing UserDetails) carrying userId and username, populated by JwtAuthFilter, and inject it into controllers via @AuthenticationPrincipal.

  Delete AuthUtils and the orphaned CurrentUserService.
  ```
- **Business Value:** Type-safe principal handling; removes brittle string parsing; matches Spring Security conventions; enables cleaner controller signatures for the community/social feature work in winter.
- **Technical Notes:** Serialize-point: `SecurityConfig.java`, `JwtAuthFilter.java`, all controllers that resolve current user. Coordinate before starting. Breaking change to the internal controller signature but not to the external API contract — Neel needs no frontend change but is a required reviewer for verification.
- **Acceptance Criteria:**
  ```
  Given an authenticated request
  When it reaches a controller method
  Then the current user is resolved via @AuthenticationPrincipal UserPrincipal, not via authentication.getName()

  And AuthUtils.currentUserId(...) is removed
  And CurrentUserService is removed
  And all existing endpoints continue to return the same responses for the same inputs
  ```
- **Dependencies:** FB-01
- **Blocks:** FB-03

---

### FB-03 · Story · Close SCRUM-55 gap tests
- **Epic:** SCRUM-84 (REFACTOR)
- **Assignee:** Pratham
- **Priority:** High
- **Story Points:** 2
- **Labels:** testing, integration-test
- **Components:** testing
- **Description:**
  ```
  Add the four gap tests identified in the original SCRUM-55 review, now that FB-01 (typed 401) and FB-02 (UserPrincipal) have landed and the seam is stable:
  1. Unauthenticated request to /api/trips → 401
  2. Real-JWT-path test hitting a protected endpoint through JwtAuthFilter (not MockMvc principal injection)
  3. Delete-by-non-owner → 403
  4. GET nonexistent trip → 404
  ```
- **Business Value:** Real coverage of the auth boundary and ownership rules; closes a long-open testing gap noted in the Sprint 2 review.
- **Acceptance Criteria:**
  ```
  Given the four scenarios above
  When the integration test suite runs in CI
  Then each scenario has a dedicated passing test asserting the correct status code and error shape
  ```
- **Dependencies:** FB-01, FB-02

---

### FB-04 · Story · Calendar export (.ics) — parent (formerly SCRUM-175)
- **Epic:** SCRUM-6 (TRIP)
- **Assignee:** — (parent, subtasks own the work)
- **Priority:** Medium
- **Story Points:** 3
- **Labels:** feature, export
- **Components:** api, frontend
- **Description:**
  ```
  Generate a standard .ics (iCalendar) file from a trip's ordered stops so users can import their itinerary into any calendar app (Google Calendar, Apple Calendar, Outlook). Two subtasks: backend generation endpoint and frontend export/download button.
  ```
- **Business Value:** Small polish detail that reads well in a demo and future portfolio use.
- **Correction (2026-07-27):** The original "reuses data already modeled" premise was wrong — neither `Trip` nor `Stop` had any date/time field. That gap is now real Jira tickets already in progress (not fall-break markdown): **SCRUM-244** (+ subtasks SCRUM-244a/b) adds `Trip.startDate`/`Stop.dayNumber`/`Stop.plannedTime`/`Stop.stopType` and a heuristic scheduler. SCRUM-175/SCRUM-176 (this story's live Jira counterpart) is linked as blocked-by SCRUM-244. FB-04a below should be read as "build the .ics VEVENT DTSTART/DTEND from `dayNumber`/`plannedTime` once SCRUM-244 lands" rather than inventing its own date source.
- **Subtasks:** FB-04a, FB-04b

---

### FB-04a · Subtask · Backend .ics generation endpoint
- **Parent:** FB-04
- **Assignee:** Tanish
- **Story Points:** — (inherits)
- **Description:**
  ```
  Add GET /api/trips/{id}/export/ics that streams a valid .ics file built from the trip's ordered stops. Each stop becomes a VEVENT with SUMMARY, LOCATION (from Place), DTSTART/DTEND (from Stop.dayNumber + Stop.plannedTime, once SCRUM-244 lands — an all-day event on the export date is the only reasonable fallback for a stop with no scheduling info), and DESCRIPTION. Use ical4j or biweekly (verify Boot 4.1 compatibility). Ownership check must match existing trip read authorization. Requires Neel review (new API endpoint).
  ```
- **Dependencies:** SCRUM-244 (day/time scheduling foundation)
- **Acceptance Criteria:**
  ```
  Given an authenticated owner of a trip
  When they GET /api/trips/{id}/export/ics
  Then the response is a valid .ics file with correct content type (text/calendar)
  And each stop appears as a VEVENT
  And the file imports cleanly into at least one major calendar app (Google Calendar tested)

  Given a non-owner
  When they GET /api/trips/{id}/export/ics for a private trip
  Then the response is 403
  ```

---

### FB-04b · Subtask · Frontend export/download button
- **Parent:** FB-04
- **Assignee:** Neel
- **Story Points:** — (inherits)
- **Description:**
  ```
  Add "Export to Calendar" button in the trip detail view. On click, calls the .ics endpoint and triggers a browser download (or share sheet on mobile PWA). Handle unauthenticated / non-owner error states via the standard toast pattern.
  ```
- **Acceptance Criteria:**
  ```
  Given a user viewing their trip detail
  When they click "Export to Calendar"
  Then the browser downloads the .ics file named after the trip
  And the download works on desktop Chrome/Safari and mobile PWA
  ```

---

### FB-05 · Story · PDF itinerary export — parent
- **Epic:** SCRUM-6 (TRIP)
- **Assignee:** — (parent)
- **Priority:** Medium
- **Story Points:** 5
- **Labels:** feature, export
- **Components:** api, frontend
- **Description:**
  ```
  Generate a formatted PDF of a trip's itinerary (title, dates, per-stop details, notes, optional map thumbnail). Pairs naturally with the .ics export and reuses the same underlying trip data. Users can save/print for offline use during travel — a genuinely useful travel-app feature, not just a demo item.
  ```
- **Business Value:** Real user value for offline travel scenarios; strong demo artifact; leverages existing trip model with zero data changes.
- **Subtasks:** FB-05a, FB-05b

---

### FB-05a · Subtask · Backend PDF generation endpoint
- **Parent:** FB-05
- **Assignee:** Tanish
- **Story Points:** — (inherits)
- **Description:**
  ```
  Add GET /api/trips/{id}/export/pdf. Use OpenPDF or Apache PDFBox (verify Boot 4.1 compatibility, avoid pulling in iText's AGPL license). Layout: trip header, ordered stops with addresses and notes, page break between logical sections. Same ownership check as .ics export. Requires Neel review.
  ```
- **Acceptance Criteria:**
  ```
  Given an authenticated owner of a trip
  When they GET /api/trips/{id}/export/pdf
  Then the response is a valid PDF with correct content type (application/pdf)
  And the PDF renders correctly in Chrome preview and Adobe Reader
  And all stops appear in order with their details
  ```

---

### FB-05b · Subtask · Frontend PDF export button
- **Parent:** FB-05
- **Assignee:** Neel
- **Story Points:** — (inherits)
- **Description:**
  ```
  Add "Download PDF" button alongside the .ics export. Same download/share pattern as FB-04b.
  ```

---

### FB-06 · Story · Progress tracking — mark stops visited
- **Epic:** SCRUM-6 (TRIP)
- **Assignee:** — (parent; Tanish backend, Neel frontend)
- **Priority:** Medium
- **Story Points:** 5
- **Labels:** feature, api-contract-change, needs-frontend-coordination
- **Components:** api, frontend, database
- **Description:**
  ```
  Users can mark individual stops as "visited" during a trip. A completion percentage is calculated per trip. Introduces a new boolean field on Stop (or a separate stop_progress table if per-user progress on shared trips is needed — decide during grooming).
  ```
- **Business Value:** Core "progress tracking" scope item from the original product vision; enables the community-feed "trips I've completed" feature in winter without new backend work.
- **Technical Notes:** Requires a Flyway migration → serialize-point coordination. DTO change → Neel review required. Decide during grooming: field on stops table (simplest, per-owner) vs stop_progress table (per-user, needed for shared/community trips).
- **Acceptance Criteria:**
  ```
  Given a trip owner viewing a stop
  When they toggle "visited"
  Then the state persists across reloads
  And the trip's completion percentage updates in the UI

  Given a trip with all stops marked visited
  When the completion percentage is calculated
  Then it returns 100

  And the migration adds the visited column (or table) cleanly on a fresh DB
  And the DTO change is documented in the API standards section of the SDP
  ```

---

### FB-07 · Story · Search and filter on trip list
- **Epic:** SCRUM-6 (TRIP)
- **Assignee:** Neel (frontend) + Tanish (backend)
- **Priority:** Medium
- **Story Points:** 3
- **Labels:** feature, api
- **Components:** api, frontend
- **Description:**
  ```
  Add search (by title, destination) and filter (by status, date range, visibility) to the trip list. Backend accepts query params on GET /api/trips and returns Page<TripResponse> filtered accordingly.
  ```
- **Business Value:** Necessary UX once users accumulate multiple trips; also the foundation for community-feed search in winter.
- **Technical Notes:** Depends on REF-21 (SCRUM-110) pagination convention landing. Do NOT start until REF-21 is merged, otherwise this will be rebuilt.
- **Acceptance Criteria:**
  ```
  Given a user with several trips
  When they enter search text or apply filters
  Then the trip list updates to show only matching trips
  And the paged response shape from REF-21 is used
  ```
- **Dependencies:** REF-21 / SCRUM-110

---

### FB-08 · Story · Gemini prompt engineering pass
- **Epic:** SCRUM-8 (AI)
- **Assignee:** Tanish
- **Priority:** Medium
- **Story Points:** 3
- **Labels:** ai, quality-improvement
- **Components:** ai, backend
- **Description:**
  ```
  Rework the Gemini itinerary generation prompt for materially better output quality. Build a benchmark of ~10 sample trips (varied regions, durations, group sizes) with current-prompt output captured. Iterate on system prompt, few-shot examples, and output structure enforcement. Document the final prompt + rationale in the AI module design doc.
  ```
- **Business Value:** The AI itinerary is a headline feature for demo and grading — the difference between "AI plans your trip" and "AI generates useful, actionable itineraries" is prompt quality. This work is invisible in the API contract, so it can't break anything and doesn't need frontend coordination.
- **Technical Notes:** No API contract change. No serialize-point files touched. Solo work.
- **Acceptance Criteria:**
  ```
  Given the 10 benchmark trips
  When itineraries are generated with the new prompt
  Then output includes specific times, locations, and reasonable travel-time gaps
  And output is structured (parseable JSON, no free-form prose leaking in)
  And a before/after comparison document is committed to the AI module design docs
  ```

---

### FB-09 · Story · Notification system — email confirmation & trip reminders
- **Epic:** SCRUM-10 (DEVOPS) or SCRUM-83 (AUTH) — decide at grooming
- **Assignee:** Pratham or Tanish
- **Priority:** Medium
- **Story Points:** 5
- **Labels:** feature, notifications
- **Components:** backend
- **Description:**
  ```
  Two flows:
  1. Signup email confirmation (verify email before full account activation, or "welcome" email post-signup)
  2. Trip reminder email X days before a trip's start date

  Use a free-tier transactional email provider (Resend, SendGrid free tier, or MailerSend). Configure via @ConfigurationProperties. Implement as a scheduled job for reminders.
  ```
- **Business Value:** Standard app feature that grading rubrics reward; genuinely useful for the travel use case; showcases scheduled-task / async processing (Spring @Scheduled) as an additional advanced-Java concept.
- **Technical Notes:** Serialize-point: `pom.xml` (mail dependency), `application.properties` (SMTP or provider API key config), possibly `SecurityConfig` if adding a public email-verification endpoint. Coordinate. Secret handling for the email provider API key follows the same pattern as JWT secret.
- **Acceptance Criteria:**
  ```
  Given a new user registers
  When registration completes
  Then a welcome/confirmation email is sent to the registered address

  Given a trip with a start date 3 days out
  When the reminder scheduler runs
  Then the trip owner receives a reminder email

  And email provider credentials are configured via environment variable, not committed
  ```

---

### FB-10 · Story · Advanced Mapbox — route rendering and stop clustering
- **Epic:** SCRUM-7 (ROUTE)
- **Assignee:** Neel
- **Priority:** Medium
- **Story Points:** 5
- **Labels:** frontend, mapbox
- **Components:** frontend
- **Description:**
  ```
  Two enhancements to the existing map view:
  1. Render the optimized route (from ORS/VROOM) as a polyline overlay between stops, not just markers
  2. When many stops are close together (community feed / large trips), cluster markers at low zoom levels

  Both are pure frontend work — the backend already returns route geometry from the ORS client.
  ```
- **Business Value:** The map is the most demo-visible component of the app; polish here has outsized impact on the "does this feel like a real product" evaluation.
- **Technical Notes:** No backend change, no API contract change. Isolated to frontend map components. Zero collision risk.
- **Acceptance Criteria:**
  ```
  Given a trip with an optimized route
  When the map view loads
  Then the route is rendered as a polyline connecting stops in order

  Given a view with 20+ nearby stops
  When zoomed out
  Then markers cluster into aggregate pins showing the count
  ```

---

### FB-11 · Story · Dark mode & theming
- **Epic:** SCRUM-11 (DOCS) — no better epic exists; consider creating a UX/POLISH epic at grooming
- **Assignee:** Neel
- **Priority:** Low
- **Story Points:** 3
- **Labels:** frontend, ux
- **Components:** frontend
- **Description:**
  ```
  Implement app-wide dark mode using Ionic's built-in theming (prefers-color-scheme + manual toggle in settings). Persist user preference in localStorage.
  ```
- **Business Value:** Standard modern-app expectation; costs nothing to demo but adds noticeably to perceived polish. Zero backend coupling.
- **Acceptance Criteria:**
  ```
  Given a user toggles dark mode in settings
  When any page loads
  Then dark theme is applied consistently across all screens
  And the preference persists across sessions
  ```

---

### FB-12 · Story · Sentry error tracking integration
- **Epic:** SCRUM-10 (DEVOPS)
- **Assignee:** Tanish
- **Priority:** Medium
- **Story Points:** 2
- **Labels:** observability, monitoring
- **Components:** backend, frontend
- **Description:**
  ```
  Wire Sentry (free tier) into both backend and frontend to capture unhandled exceptions in production. Configure release tagging so errors are linked to specific deploys. Adds a real observability story on top of the Actuator work from this semester.
  ```
- **Business Value:** Non-functional requirement (reliability / observability) that grading rubrics look for; produces a live dashboard artifact usable in the final demo.
- **Technical Notes:** Serialize-point: `pom.xml` (backend), `package.json` (frontend), `application.properties` (Sentry DSN). Coordinate before starting. Sentry DSN is a secret — treat like the Cloudinary/JWT keys.
- **Acceptance Criteria:**
  ```
  Given an unhandled exception in production
  When it occurs
  Then it is captured in Sentry with stack trace, request context, and release tag

  And the Sentry DSN is configured via environment variable
  And the Sentry dashboard shows at least one test error captured end-to-end
  ```

---

### FB-13 · Story · Offline PWA caching for saved trips
- **Epic:** SCRUM-10 (DEVOPS) or SCRUM-6 (TRIP)
- **Assignee:** Neel
- **Priority:** Low
- **Story Points:** 5
- **Labels:** frontend, pwa, offline
- **Components:** frontend
- **Description:**
  ```
  Extend the existing service worker to cache trip data on first fetch so users can view their saved trips without a connection — genuinely important for a travel app where users may be roaming or offline.

  Scope: read-only offline view of previously loaded trips. Offline edits/sync are out of scope (future work).
  ```
- **Business Value:** Directly addresses the "PWA" story in the product vision; strong differentiator vs generic CRUD apps; excellent demo talking point.
- **Technical Notes:** Frontend-only. No backend changes. Use Ionic/Angular service worker + IndexedDB or Cache API.
- **Acceptance Criteria:**
  ```
  Given a user has previously loaded their trip list while online
  When they open the app offline (airplane mode)
  Then they can still view the trip list and previously-loaded trip details
  And a clear "offline" indicator is shown
  ```

---

### FB-14 · Task · Native Capacitor build spike
- **Epic:** SCRUM-10 (DEVOPS)
- **Assignee:** Neel
- **Priority:** Low
- **Story Points:** 3
- **Labels:** spike, mobile, stretch-goal
- **Components:** frontend
- **Description:**
  ```
  Attempt an actual Capacitor build of the PWA for Android (and iOS if a Mac is available). Document blockers, required plugin configurations, and what the app looks like as a native shell. Result is either a working APK/IPA committed as a release artifact, or a documented list of what would be needed to get there.
  ```
- **Business Value:** Original stretch goal; concrete artifact (working APK) is a strong final-demo differentiator; if it doesn't work, the writeup is still a legitimate deliverable.
- **Technical Notes:** Frontend-config-only. No backend collision risk.
- **Acceptance Criteria:**
  ```
  Given a Capacitor build attempt
  When completed
  Then either:
    - A working Android APK is produced and committed as a release artifact, or
    - A markdown document details the specific blockers and required next steps

  And the outcome is written up in docs/native-build-spike.md
  ```

  ---

### FB-15 · Story · Mapbox place search for stop input — parent
- **Epic:** SCRUM-6 (TRIP)
- **Assignee:** — (parent, subtasks own the work)
- **Priority:** High
- **Story Points:** 5
- **Labels:** feature, ux, api-contract-change, needs-frontend-coordination, mapbox
- **Components:** api, frontend
- **Description:**
```
  Replace the current manual latitude/longitude entry for stops with a Mapbox-powered place search experience. Users type a place name ("Eiffel Tower", "coffee shops near Rome") and pick from a suggestion dropdown; the selected place populates the stop's name, coordinates, address, and external place ID automatically.

  Manual lat/lng entry is preserved as a fallback for edge cases (unnamed locations, precise coordinates from a GPS device, etc.).

  This slots naturally into the existing Place entity, which was designed with external_place_id specifically for this purpose — the Mapbox mapbox_id maps directly onto that field and reuses the existing dedup logic in TripService.resolvePlace(). No schema change required.
```
- **Business Value:** The current lat/lng entry is a demo-day landmine and blocks the app from feeling like a real product. Place search is the baseline expectation for any travel/maps app; without it, the app looks like a prototype regardless of how polished the rest is. High visible-polish return per SP.
- **Technical Notes:**
  - Use Mapbox Search JS SDK's `<mapbox-search-box>` web component rather than raw Geocoding API — significantly less code, matches the Google-Maps-style UX users expect.
  - Session-token handling: Mapbox bills Search Box + Geocoding calls as one event per search session (typing → picking) when a session token is passed. Implement correctly from day one.
  - `CreateStopRequest` / `UpdateStopRequest` DTO changes are additive (new optional fields) — non-breaking to existing manual-entry flow.
  - Serialize-point coordination is minimal (DTO change only, no `SecurityConfig` / `pom.xml` / migration).
  - Requires Neel review (API contract change), but Neel is also the frontend implementer — natural coordination.
- **Subtasks:** FB-15a, FB-15b

---

### FB-15a · Subtask · Frontend Mapbox Search Box integration in trip-edit flow
- **Parent:** FB-15
- **Assignee:** Neel
- **Story Points:** — (inherits, ~3 of the 5)
- **Description:**
```
  Integrate the Mapbox Search JS SDK <mapbox-search-box> component into the stop-add / stop-edit UI. On suggestion select, populate the stop form with the returned name, coordinates, address, and mapbox_id. Preserve the manual lat/lng input path as a "Enter coordinates manually" fallback toggle. Handle session tokens correctly across the search-then-select lifecycle so billing is one event per pick, not one per keystroke.
```
- **Acceptance Criteria:**
```
  Given a user adding a stop to a trip
  When they type a place name into the search field
  Then a dropdown of relevant Mapbox suggestions appears within ~300ms
  And selecting a suggestion populates the stop's name, latitude, longitude, address, and mapboxPlaceId fields
  And the stop can then be saved via the existing POST /api/trips/{id}/stops flow

  Given a user needs to enter a precise or unnamed location
  When they toggle "Enter coordinates manually"
  Then the current lat/lng input UI is shown and works as it does today

  And each search session (typing → pick) results in exactly one billable Mapbox event via correct session-token handling
```

---

### FB-15b · Subtask · Backend DTO + Place resolution updates
- **Parent:** FB-15
- **Assignee:** Tanish
- **Story Points:** — (inherits, ~2 of the 5)
- **Description:**
```
  Extend CreateStopRequest and UpdateStopRequest with two new optional fields: mapboxPlaceId (String) and address (String). Update TripService (and the resolvePlace / stop creation path) so that when mapboxPlaceId is provided, it is used as the external_place_id for Place dedup — falling back to the existing lat/lng-based path when mapboxPlaceId is absent. Update StopMapper / TripMapper as needed. Update or add unit tests covering both paths (with-mapboxPlaceId and without).

  Note interaction with FB-06 (progress tracking) if that's already landed — no conflict expected, both are additive to Stop, but verify migration order at implementation time.

  Note interaction with REF-20 (unique constraint on places.external_place_id) if that's landed — this ticket relies on that constraint working correctly to guarantee dedup under concurrent stop creation.
```
- **Acceptance Criteria:**
```
  Given a stop creation request with mapboxPlaceId set
  When the request is processed
  Then a Place is either resolved to the existing row with that external_place_id or newly created with it
  And the stop is linked to that Place
  And a repeat request with the same mapboxPlaceId does not create a duplicate Place

  Given a stop creation request without mapboxPlaceId (legacy / manual entry path)
  When the request is processed
  Then the existing resolvePlace behavior is preserved unchanged
  And the stop is created successfully

  And unit tests cover both paths
  And the DTO contract change is documented in the SDP API standards section
```

### FB-16 · Story · Refresh token flow for persistent login — parent
- **Epic:** SCRUM-83 (AUTH)
- **Assignee:** — (parent, subtasks own the work)
- **Priority:** Medium
- **Story Points:** 5
- **Labels:** auth, ux, refresh-token, api-contract-change
- **Components:** security, api, frontend
- **Description:**
```
  Today, login/register issue a single short-lived access token with no renewal path — once it expires, the user is fully logged out and must re-enter credentials. Add a refresh token flow: a longer-lived, DB-backed, revocable refresh token issued alongside the access token, with rotation on each use and a silent-refresh interceptor on the frontend so expiry is invisible to the user in normal use.
  Use a DB-backed opaque refresh token (not a second JWT) specifically so a session can be revoked server-side on logout — a second stateless JWT would reintroduce the "can't kill a session" problem this flow exists partly to solve.
```
- **Business Value:** Users stay logged in across normal usage instead of being booted on every token expiry; enables real server-side "log out this session" capability; standard expected behavior for any real-feeling product, and content that reads as genuine security depth for the AJF Module B security domain.
- **Technical Notes:**
  - New Flyway migration: `refresh_tokens` table — `id`, `user_id` (FK), `token_hash` (store a hash, never the raw token), `expires_at`, `revoked_at` (nullable), `created_at`.
  - `POST /api/auth/refresh` — accepts the current refresh token, validates hash + not revoked + not expired, issues a new access token AND rotates the refresh token (old one is revoked, new one issued in the same response). Rotation-on-use is a standard mitigation against replay of a stolen refresh token — if a revoked/already-used refresh token is presented again, treat it as a signal of possible theft and revoke the entire token family for that user.
  - `POST /api/auth/logout` revokes the refresh token server-side (add this endpoint if it doesn't already exist).
  - Login/register responses gain a `refreshToken` field alongside the existing `token` field — additive, non-breaking to the existing contract.
  - New `RefreshTokenService` alongside the existing `JwtService`; `JwtService` itself is unaffected (still issues the short-lived access token exactly as today).
  - Serialize-point coordination: touches `AuthController`, `AuthService`, migrations — not `SecurityConfig.java` directly, since `/api/auth/refresh` and `/api/auth/logout` are already permit-all-adjacent paths.
  - Soft dependency: cleaner if done after FB-02 (typed `UserPrincipal`) lands, since the refresh endpoint's user resolution benefits from the same typed seam — not a hard blocker either way.
- **Subtasks:** FB-16a, FB-16b

---

### FB-16a · Subtask · Backend refresh token issuance, rotation, and revocation
- **Parent:** FB-16
- **Assignee:** Pratham
- **Story Points:** — (inherits, ~3 of the 5)
- **Description:**
```
  Implement the refresh_tokens migration, RefreshTokenService (issue/validate/rotate/revoke), the POST /api/auth/refresh endpoint, and POST /api/auth/logout revocation. Add reuse-detection: presenting an already-rotated (revoked) refresh token revokes the entire token family for that user as a theft-response measure.
```
- **Acceptance Criteria:**
```
  Given a valid, unexpired, unrevoked refresh token
  When POST /api/auth/refresh is called
  Then a new access token and a new refresh token are returned
  And the old refresh token is marked revoked
  Given a refresh token that has already been rotated (reused)
  When POST /api/auth/refresh is called with it
  Then the request is rejected
  And all refresh tokens for that user are revoked
  Given a valid refresh token
  When POST /api/auth/logout is called
  Then that refresh token is revoked and cannot be used again
  And unit tests cover: fresh token success, expired token rejection, revoked token rejection, reuse-detection family revocation
```

---

### FB-16b · Subtask · Frontend silent-refresh interceptor
- **Parent:** FB-16
- **Assignee:** Neel
- **Story Points:** — (inherits, ~2 of the 5)
- **Description:**
```
  Extend the existing auth interceptor: on a 401 response (access token expired), call POST /api/auth/refresh once with the stored refresh token, update stored tokens on success, and retry the original request transparently. On refresh failure (expired/revoked refresh token), fall back to the existing full-logout flow. Guard against infinite retry loops — refresh is attempted at most once per original request.
```

- **Acceptance Criteria:**
```
  Given an access token has expired
  When any API call returns 401
  Then the interceptor silently calls /api/auth/refresh, updates stored tokens, and retries the original request once
  And the user sees no interruption if the refresh succeeds
  Given the refresh token itself is expired or revoked
  When the silent refresh attempt fails
  Then the user is logged out and redirected to /login, same as current behavior
  And no request can trigger more than one refresh attempt (no infinite loop)
```

---

### FB-17 · Story · Gemini-driven itinerary scheduling (day/time/reasoning + meal stops)
- **Epic:** SCRUM-8 (AI)
- **Assignee:** Tanish
- **Priority:** Medium
- **Story Points:** 5
- **Labels:** ai, feature, api-contract-change
- **Components:** ai, backend
- **Description:**
  ```
  Extend suggestItinerary so Gemini returns a full day-by-day plan, not just a flat list of stops: for each suggested stop, include a day number, a planned time, the existing reasoning text, and a stopType — and have Gemini proactively suggest meal stops (lunch/dinner) at appropriate times alongside the sightseeing stops, using the same reasoning field to explain why.

  Builds directly on FB-08 (Gemini prompt engineering pass) — do these together or FB-08 immediately before this, since both touch ItineraryPromptTemplate/SuggestedItinerary. Requires SCRUM-244 (day/time scheduling foundation) to have landed first, so Gemini's suggested day/time has real Stop.dayNumber/plannedTime/stopType fields to map onto when a suggestion is accepted.
  ```
- **Business Value:** This is the actual "AI plans your trip" experience the product pitches — a flat list of stops with no schedule is a much weaker demo than a real day-by-day itinerary with meals included.
- **Technical Notes:**
  - Extend `SuggestedItinerary`/`ItineraryPromptTemplate` (`ai` package) — response schema gains `day`, `time`, `stopType` per suggested stop; prompt instructs Gemini to include 1-2 meal suggestions per day at reasonable times.
  - `PromptTooLargeException`'s 8,000-char backstop (SCRUM-217) may need revisiting if the richer output requires a larger prompt — re-measure, don't just bump the constant blind.
  - No new external integration (still just Gemini) — the meal suggestions come from Gemini's own knowledge, not a separate places/restaurant API call.
- **Dependencies:** SCRUM-244, FB-08
- **Acceptance Criteria:**
  ```
  Given a trip and interests/budget/pace preferences
  When suggestItinerary is called
  Then each suggested stop includes a day number, a planned time, a reason, and a stopType
  And at least one meal-type suggestion appears per day when the day spans typical meal times
  And accepting a suggested stop maps cleanly onto Stop.dayNumber/plannedTime/stopType
  ```

---

### FB-18 · Story · Frontend alternative-suggestion popups for stops and food places
- **Epic:** SCRUM-6 (TRIP)
- **Assignee:** Neel
- **Priority:** Low
- **Story Points:** 3
- **Labels:** frontend, ux, ai
- **Components:** frontend
- **Description:**
  ```
  When viewing AI suggestions (FB-17), let the user tap a suggested stop or meal slot to see 2-3 alternative options in a popup, rather than only ever seeing Gemini's single top pick. Selecting an alternative swaps it into the itinerary in place of the original suggestion.
  ```
- **Business Value:** Makes the AI-suggestion flow feel interactive rather than take-it-or-leave-it — a natural companion to FB-17's richer output.
- **Technical Notes:** Cheapest implementation is having Gemini return an array of 2-3 candidates per slot in FB-17's response schema rather than standing up a separate backend endpoint for alternatives — confirm this is sufficient before reaching for a new API surface.
- **Dependencies:** FB-17
- **Acceptance Criteria:**
  ```
  Given an AI-suggested stop or meal slot
  When the user taps it
  Then a popup shows 2-3 alternative suggestions
  And selecting an alternative replaces the original suggestion in the itinerary being built
  ```

---

## SECTION 3 — Summary table

| ID | Summary | Owner | SP | Priority | Depends on |
|---|---|---|---|---|---|
| FB-01 | Custom auth entry point (401 vs 403) | Pratham | 2 | High | — |
| FB-02 | UserPrincipal typed seam | Pratham | 3 | High | FB-01 |
| FB-03 | SCRUM-55 gap tests | Pratham | 2 | High | FB-01, FB-02 |
| FB-04 | Calendar export (.ics) parent | — | 3 | Medium | — |
| FB-04a | Backend .ics endpoint | Tanish | — | — | — |
| FB-04b | Frontend export button | Neel | — | — | FB-04a |
| FB-05 | PDF itinerary export parent | — | 5 | Medium | — |
| FB-05a | Backend PDF endpoint | Tanish | — | — | — |
| FB-05b | Frontend PDF button | Neel | — | — | FB-05a |
| FB-06 | Progress tracking (stops visited) | Tanish + Neel | 5 | Medium | — |
| FB-07 | Search/filter on trip list | Neel + Tanish | 3 | Medium | REF-21 / SCRUM-110 |
| FB-08 | Gemini prompt engineering pass | Tanish | 3 | Medium | — |
| FB-09 | Notification system (email) | Pratham/Tanish | 5 | Medium | — |
| FB-10 | Advanced Mapbox (route + clustering) | Neel | 5 | Medium | — |
| FB-11 | Dark mode & theming | Neel | 3 | Low | — |
| FB-12 | Sentry error tracking | Tanish | 2 | Medium | — |
| FB-13 | Offline PWA caching | Neel | 5 | Low | — |
| FB-14 | Native Capacitor build spike | Neel | 3 | Low | — |
| FB-15 | Mapbox place search for stop input | — | 5 | High | — |
| FB-15a | Frontend Mapbox Search Box | Neel | — | — | — |
| FB-15b | Backend DTO + Place resolution | Tanish | — | — | REF-20 (soft) |
| FB-16 | Refresh token flow for persistent login | — | 5 | Medium | FB-02 (soft) |
| FB-16a | Backend refresh issuance/rotation/revocation | Pratham | — | — | — |
| FB-16b | Frontend silent-refresh interceptor | Neel | — | — | FB-16a |
| FB-17 | Gemini-driven itinerary scheduling (day/time/reasoning + meals) | Tanish | 5 | Medium | SCRUM-244, FB-08 |
| FB-18 | Frontend alternative-suggestion popups | Neel | 3 | Low | FB-17 |

**Total SP (excluding subtasks):** ~67

**Note (2026-07-27):** SCRUM-244 (+ SCRUM-244a/b) — the day/time scheduling foundation FB-17 depends on — is not part of this fall-break backlog; it's a real Jira ticket already being worked this sprint, created after discovering FB-04/SCRUM-175 assumed scheduling data that didn't exist. See the FB-04 entry above for the full story.

---

## SECTION 4 — Suggested break sequencing

**Phase 1 — first 2 weeks (Aug 17–31):** FB-01, FB-02, FB-16 (Pratham) → the whole auth-seam arc in one pass. FB-08 (Tanish) in parallel — no coupling. FB-11 (Neel) as an easy warm-up.

**Phase 2 — Sept:** FB-03 (Pratham, after Phase 1). FB-04 + FB-04a/b (Tanish+Neel). FB-10 (Neel).

**Phase 3 — Oct:** FB-05 + FB-05a/b. FB-06 (needs migration coordination — best done when at least 2 team members are available for the same week).

**Phase 4 — Nov/Dec:** FB-09, FB-12, FB-13, FB-14 as capacity allows. FB-07 anytime after REF-21 lands. FB-17 (needs SCRUM-244 already landed) + FB-18 as capacity allows, ideally paired with or right after FB-08.

**Left for winter term:** community/discovery feed integration, final regression pass, production hardening, deployment automation, SDP finalization, all documentation deliverables, presentation prep. That's still a substantial semester — the break work de-risks it, doesn't gut it.

---

## SECTION 5 — Ticket creation instructions (for future Claude)

When Tanish provides this file to create tickets:

1. Verify epic keys in section 1 still exist and are accurate — run `searchJiraIssuesUsingJql` with `issuetype = Epic AND project = SCRUM`.
2. Create parent stories/tasks first (FB-01, FB-02, FB-03, FB-04, FB-05, FB-06, FB-07, FB-08, FB-09, FB-10, FB-11, FB-12, FB-13, FB-14).
3. Create subtasks after parents exist, using the parent's newly-created SCRUM key.
4. Use `contentFormat: markdown` on the description field.
5. Do NOT set `customfield_10020` (sprint) — leave in backlog.
6. Set `customfield_10016` (story points) per the table.
7. Set priority and labels as documented.
8. Add blocks/depends links via `addTeamworkGraphContext` with `relationshipType: "jira-work-item-blocks-jira-work-item"` for the dependency chain (FB-01 → FB-02 → FB-03; FB-04 → FB-04b; FB-05 → FB-05b; REF-21 → FB-07).
9. After creation, return a summary table mapping FB-## to the newly assigned SCRUM keys.
