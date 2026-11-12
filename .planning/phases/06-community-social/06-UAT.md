---
status: testing
phase: 06-community-social
source: [06-01-SUMMARY.md, 06-02-SUMMARY.md, 06-03-SUMMARY.md, 06-04-SUMMARY.md, 06-05-SUMMARY.md, 06-06-SUMMARY.md]
started: 2026-09-01T00:00:00Z
updated: 2026-09-01T00:00:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

number: 1
name: Cold Start Smoke Test
expected: |
  Kill any running server/service. Clear ephemeral state (temp DBs, caches, lock files). Start the application from scratch. Server boots without errors, any seed/migration completes, and a primary query (health check, homepage load, or basic API call) returns live data.
awaiting: user response

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running server/service. Clear ephemeral state (temp DBs, caches, lock files). Start the application from scratch. Server boots without errors, any seed/migration completes, and a primary query (health check, homepage load, or basic API call) returns live data.
result: [pending]

### 2. RISK-R2: discovery endpoints reject requests with no Authorization header (real HTTP client)
expected: Using a real HTTP client (Postman, curl, or browser — not MockMvc) against a running backend instance, GET /api/discovery/trips and GET /api/discovery/feed with no Authorization header both return 401.
result: [pending]

### 3. Feed swipe navigates between trips
expected: On /feed as an authenticated user, one PUBLIC trip fills the viewport. Swiping up moves to the next trip, one trip at a time.
result: [pending]

### 4. Feed swipe navigates between stops within a trip
expected: Swiping left/right inside a trip card moves between that trip's stops without moving to another trip in the outer feed — vertical vs horizontal gestures are correctly disambiguated.
result: [pending]

### 5. Action rail works without leaving the feed
expected: Tapping like, save, clone, or a star rating on the feed action rail performs the action in place — the full-screen feed is never exited, and rail taps are never misread as a swipe by the surrounding gesture surfaces.
result: [pending]

### 6. GET /api/discovery/feed requires a valid JWT and returns paged PUBLIC trips with ownerUsername, description, tags, and an ordered stops array
expected: GET /api/discovery/feed requires a valid JWT and returns paged PUBLIC trips with ownerUsername, description, tags, and an ordered stops array
result: pass
source: automated
coverage_id: 06-01-D1

### 7. The whole discovery surface (/feed, /trips, /search) requires authentication — the pre-existing permitAll hole is closed
expected: The whole discovery surface (/feed, /trips, /search) requires authentication — the pre-existing permitAll hole is closed
result: pass
source: automated
coverage_id: 06-01-D2

### 8. A feed page load issues exactly one stop-photo query regardless of stop count, and zero-photo trips render with empty photoUrls
expected: A feed page load issues exactly one stop-photo query regardless of stop count, and zero-photo trips render with empty photoUrls
result: pass
source: automated
coverage_id: 06-01-D3

### 9. Angular DiscoveryService.getFeed(page, size) calls GET /api/discovery/feed and maps 401 to a rejected observable
expected: Angular DiscoveryService.getFeed(page, size) calls GET /api/discovery/feed and maps 401 to a rejected observable
result: pass
source: automated
coverage_id: 06-01-D4

### 10. Trip name, major location and owner username stay pinned at the top and the description stays pinned at the bottom regardless of which stop is showing
expected: Trip name, major location and owner username stay pinned at the top and the description stays pinned at the bottom regardless of which stop is showing
result: pass
source: automated
coverage_id: 06-02-D3

### 11. A trip whose stops have no photos renders a readable text card instead of a blank or broken slide
expected: A trip whose stops have no photos renders a readable text card of stop name and notes instead of a blank or broken slide
result: pass
source: automated
coverage_id: 06-02-D4

### 12. Reaching the end of the loaded trips appends the next page, stops requesting once exhausted, and collapses rapid double-fires into one request
expected: Reaching the end of the loaded trips appends the next page rather than dead-ending, stops requesting once exhausted, and collapses rapid double-fires into one request
result: pass
source: automated
coverage_id: 06-02-D5

### 13. An unauthenticated visit to /feed is redirected by authGuard, never rendering feed content
expected: An unauthenticated visit to /feed is redirected by authGuard, never rendering feed content
result: pass
source: automated
coverage_id: 06-02-D6

### 14. The feed is reachable from the dashboard
expected: The feed is reachable from the dashboard
result: pass
source: automated
coverage_id: 06-02-D7

### 15. Saving an already-saved trip is a no-op that still returns success — no duplicate row, no error
expected: Saving an already-saved trip is a no-op that still returns success — no duplicate row, no error
result: pass
source: automated
coverage_id: 06-03-D2

### 16. Saving, unsaving or liking a PRIVATE trip owned by someone else returns 404, not 403
expected: Saving, unsaving or liking a PRIVATE trip owned by someone else returns 404, not 403 (SCRUM-274)
result: pass
source: automated
coverage_id: 06-03-D3

### 17. A user can retrieve their own saved-trips list; it contains exactly the trips they saved and none saved by anyone else
expected: A user can retrieve their own saved-trips list; it contains exactly the trips they saved and none saved by anyone else
result: pass
source: automated
coverage_id: 06-03-D4

### 18. Cloning from the rail creates a new PRIVATE trip owned by the actor and navigates them to it
expected: Cloning from the rail creates a new PRIVATE trip owned by the actor and navigates them to it
result: pass
source: automated
coverage_id: 06-03-D5

### 19. A user can give a PUBLIC (or their own PRIVATE) trip a 1-5 star rating; re-rating replaces the value without duplicate row or double-counted average
expected: A user can give a PUBLIC (or their own PRIVATE) trip a 1-5 star rating; re-rating replaces the value without creating a duplicate row or double-counting the average
result: pass
source: automated
coverage_id: 06-04-D1

### 20. Out-of-range ratings are rejected at the API (400 with fieldErrors) and independently at the database (CHECK constraint)
expected: Out-of-range ratings are rejected at the API (400 with fieldErrors) and independently at the database (CHECK constraint)
result: pass
source: automated
coverage_id: 06-04-D2

### 21. Rating or reading the rating summary of a foreign PRIVATE trip returns 404, not 403; own average/count/myRating retrievable in one request
expected: Rating or reading the rating summary of a foreign PRIVATE trip returns 404, not 403; the caller's own average/count/myRating are retrievable in one request
result: pass
source: automated
coverage_id: 06-04-D3

### 22. A star rating control on the feed action rail: fetches per active card only, taps set/replace the rating optimistically, revert-on-failure, one request per double-tap
expected: A star rating control on the feed action rail: fetches per active card only, taps set/replace the rating optimistically, revert-on-failure, one request per double-tap
result: pass
source: automated
coverage_id: 06-04-D4

### 23. A signed-in user can open a profile page showing their username, join date, and stored interests
expected: A signed-in user can open a profile page showing their username, join date, and stored interests
result: pass
source: automated
coverage_id: 06-05-D1

### 24. A user can edit and persist their interests, and reloading the page shows the saved values
expected: A user can edit and persist their interests, and reloading the page shows the saved values
result: pass
source: automated
coverage_id: 06-05-D2

### 25. An interests payload with more than 20 entries, or any entry longer than 50 characters, is rejected with 400 carrying fieldErrors
expected: An interests payload with more than 20 entries, or any entry longer than 50 characters, is rejected with 400 carrying fieldErrors
result: pass
source: automated
coverage_id: 06-05-D3

### 26. Profile reads and writes always resolve the caller from the authenticated principal — no request-supplied user id can retarget them
expected: Profile reads and writes always resolve the caller from the authenticated principal — no request-supplied user id can retarget them
result: pass
source: automated
coverage_id: 06-05-D4

### 27. The stored interests column is queryable by Postgres array overlap for plan 06-06's ranking, with no schema change needed
expected: The stored interests column is queryable by Postgres array overlap for plan 06-06's ranking, with no schema change needed
result: pass
source: automated
coverage_id: 06-05-D5

### 28. Interest-matching PUBLIC trips rank first, recency second; PRIVATE trips never surface; ranking never reads the viewer's own trip tags
expected: PUBLIC trips whose tags overlap the viewer's stored profile interests are ordered first, recency second, both within groups and as the whole-feed fallback for a viewer with no stored interests; PRIVATE trips never surface under ranking; ranking never reads the viewer's own trip tags
result: pass
source: automated
coverage_id: 06-06-D1

### 29. Paging the ranked feed never duplicates or skips a trip; total count excludes PRIVATE trips; client-supplied sort is rejected; out-of-range page returns empty content
expected: Paging the ranked feed never duplicates or skips a trip across pages, the reported total-element count excludes PRIVATE trips, a client-supplied sort parameter is rejected rather than silently honoured, and an out-of-range page returns 200 with empty content
result: pass
source: automated
coverage_id: 06-06-D2

### 30. docs/api-contracts.md and docs/auth.md document phase 6's endpoints and corrected discovery auth requirement
expected: docs/api-contracts.md documents every endpoint phase 6 added (feed with ranking rules, corrected discovery auth requirement, save/unsave/saved-list, rate/rating-summary, profile get/update, User.interests field limit), and docs/auth.md's permitAll table no longer lists the discovery surface as public
result: pass
source: automated
coverage_id: 06-06-D3

## Summary

total: 30
passed: 25
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps

[none yet]
