# Phase 6: Community & Social - Context

**Gathered:** 2026-08-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 6 delivers TripFlowAI's social layer: a "For You" discovery feed of other users' PUBLIC trips, engagement actions (like, save, clone), trip ratings, and — per this discussion — a minimal user profile page and lightweight interest-based feed ranking. Everything here operates on PUBLIC trips only; PRIVATE trips remain invisible to non-owners (default-deny `SecurityConfig` still applies — the feed requires authentication, it is not a public unauthenticated surface).

</domain>

<decisions>
## Implementation Decisions

### Feed interaction model
- **D-01:** The "For You" feed is a TikTok/Reels-style **full-screen, one-trip-at-a-time vertical swipe** — not an Instagram-style scrolling list of post-cards. Swiping up/down moves between different trips (different "posts"); swiping left/right within a trip's card moves between that trip's stop images. — **Reversibility:** costly — this is a foundational frontend layout choice (full-screen takeover component vs. a scrollable list); switching later means rebuilding the feed component, not just restyling it.
- **D-02:** Card layout, fixed regardless of scroll/swipe position within a trip: trip name + major location at the top, owner username also at the top, description fixed at the bottom. The middle area is the horizontally-swipeable stop content (images, or text fallback — see D-04).
- **D-03:** Trips with zero stop photos fall back to a **text-based card** (stop name/description) in the swipeable middle area instead of breaking the layout or being excluded from the feed. Exact visual treatment of the text-card fallback is deferred — revisit during Phase 6 planning/UI design, not blocking.

### Engagement actions
- **D-04:** Like, save/bookmark, and clone are all available directly on the feed card via an **on-card action rail** (TikTok's side-rail pattern) — the user never has to leave the full-screen feed to like/save/clone a trip. — **Reversibility:** reversible — this is additive UI on top of the existing like/save/clone endpoints (FB-20/21/24); the endpoints themselves don't change based on where the button lives.

### Personalization / ranking
- **D-05:** The feed is not purely chronological — it applies **lightweight interest-based ranking**: PUBLIC trips whose tags match the viewer's stored profile interests are ordered first, with the remainder falling back to recency (or another simple, non-personalized order). This is explicitly a small ranking pass, not a recommendation-engine build. — **Reversibility:** reversible — ranking logic is a query-ordering concern; can be simplified back to pure chronological without touching the feed's data model or UI.
- **D-06:** The source of "interests" for ranking is the new **stored profile interests field** from D-07 (the user profile page), not an inferred signal from the viewer's own trip history. This was the user's explicit choice when asked to pick between the two.

### User profile page (new scope, added this discussion)
- **D-07:** A minimal user profile page ships as part of **this phase** (not deferred to a later phase): username, join date, and stored interests. This was an explicit scope decision — the team has a long fall break (~4.5 months) and wants to front-load functionality, and the profile page is small and directly required as the data source for D-05/D-06's ranking and for the feed's owner-username display. — **Reversibility:** one-way-ish for the schema piece — adding a `user_interests`-shaped field/table is cheap to add now; retrofitting it after ranking logic and feed UI already assume its absence would mean revisiting both.

### Milestone-level sequencing (not phase-specific, but decided during this discussion)
- **D-08:** All of Phases 1-7 (all fall-break feature work, including this phase's expanded scope) target the fall-break window; Phase 8 (winter) stays hardening/regression/sign-off only, not net-new features. This was already the roadmap's structure but the user explicitly confirmed and reinforced it — see `.planning/ROADMAP.md` Overview and per-phase `(Fall)`/`(Winter)` tags, updated 2026-08-06.

### Claude's Discretion
- Exact text-card fallback visuals for no-photo trips (D-03) — left open, resolve during planning/UI design.
- Exact interest-tag taxonomy (free text vs. fixed category list) for the profile interests field (D-07) — left open, resolve during planning; whichever is chosen must be queryable for D-05's ranking match.
- Whether ranking (D-05) is computed at query time or precomputed/cached — an implementation detail for the planner/researcher, not a user-facing decision.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 6 planning docs (from ROADMAP.md / milestone init)
- `.planning/ROADMAP.md` §Phase 6 — phase goal, requirements, success criteria, 6-plan task breakdown
- `.planning/REQUIREMENTS.md` §Community/Social (SOCIAL) — SOCIAL-01 through SOCIAL-06
- `.planning/RISKS.md` — RISK-J1 (stale epic mapping for new tickets), RISK-J3 (SCRUM-274 404-hiding overlap)

### Existing Jira tickets to reconcile against (not duplicate)
- `SCRUM-71` (parent, In Progress) and subtasks `SCRUM-159`/160/161/162/163 (71a-71e) — visibility, discovery feed, likes, clone, search. Note `SCRUM-160`'s real endpoint path is `GET /api/discovery/trips`, differing from this doc's `/api/trips/discover` — reconcile before implementing either.
- `SCRUM-274` — standardize 404 existence-hiding across owner-gated trip mutations; resolve as part of this phase's like/clone/rate work, not separately.

### Source docs this phase's original scope was drawn from
- `docs/TripFlow_fall_Break_Plan.md` — FB-19, FB-19a/b/c/d, FB-20, FB-20a/b, FB-21, FB-21a/b, FB-24
- `docs/social-features-traceability-audit.md` — original gap analysis between planned social features and shipped code; source of the 404-vs-403 existence-hiding question and the "Copy of {title}" clone-rename convention question

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TripOwnershipService` — visibility/ownership check pattern (PUBLIC vs PRIVATE) to reuse for feed filtering and action-rail authorization
- `PlaceResolutionService.resolvePlace()` dedup pattern — relevant if clone (D-04-adjacent, FB-21) needs to reuse Place rows rather than duplicate them
- `StopPhotoService` / `StopPhoto` entity — existing per-stop photo storage; the feed's swipeable middle area consumes this directly (no new photo storage needed, only new read/aggregation queries)

### Established Patterns
- Existing `trip_likes`-shaped join-table pattern (from FB-20's own design notes) should be mirrored for save/bookmark (FB-24) — same shape, different table
- `GET /api/trips` already uses the paged-response convention (REF-21/SCRUM-110, confirmed Done) — the new discovery/feed endpoint should reuse this convention per FB-19's own design notes, adapted for "one full trip payload per feed item" rather than a summary list (see D-01: full-screen feed needs full trip+stops+photos per item, not just `TripSummaryResponse`)

### Integration Points
- No existing swipeable/carousel component exists in the frontend (checked: `stop-photo-gallery` is a static grid, not swipeable) — the horizontal stop-swipe (D-01/D-02) and vertical trip-swipe are net-new frontend components, not a restyle of anything existing
- No existing user-profile page or interests field anywhere in the schema — D-07 is genuinely new domain, will need a new migration and likely a new `ProfileController`/`ProfileService` or an extension of `AuthController`/`UserService`

</code_context>

<specifics>
## Specific Ideas

- "It should be like Insta with the trip name and major location at the top along with the owner username, the description at the bottom and then the stops/images in the middle that the user then swipes to see the next stops" — user's original framing (Instagram-post visual layout), refined during discussion to TikTok's full-screen *scroll-between-posts* interaction model rather than Instagram's list-of-cards model. Both the Instagram-style header/footer/middle-carousel layout AND the TikTok-style full-screen-per-trip scroll behavior are locked decisions (D-01, D-02) — they're not in tension, they describe different axes (card content layout vs. feed navigation gesture).
- "A little bit of personalization for now, trips including you general interests ranking first" — explicit, deliberately scoped-down ranking ask (D-05), not a recommendation engine.
- "No-photo trips can just show the stop description and other stuff which can be suitable and we can think about later" — explicit deferral of the fallback's exact design (D-03).

</specifics>

<deferred>
## Deferred Ideas

None beyond what's captured as "Claude's Discretion" above — this discussion stayed within an expanded-but-bounded Phase 6 scope. No ideas were pushed out to future phases; the two scope additions (profile page, ranking) were explicitly pulled *into* this phase rather than deferred.

### Reviewed Todos (not folded)
None — no pending todos existed to cross-reference at time of this discussion.

</deferred>

---

*Phase: 6-Community & Social*
*Context gathered: 2026-08-06*
