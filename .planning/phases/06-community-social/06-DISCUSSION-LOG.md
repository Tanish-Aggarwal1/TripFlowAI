# Phase 6: Community & Social - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-06
**Phase:** 6-Community & Social
**Areas discussed:** Feed scroll model, "For You" ranking scope, On-card engagement actions, No-photo trips, Profile page scope, Milestone-level fall/winter sequencing

---

## Feed scroll model

| Option | Description | Selected |
|--------|-------------|----------|
| Instagram-style post list | Vertical list of post-cards; each has its own internal horizontal stop-image swipe | |
| TikTok/Reels-style full-screen | Full-screen, one-trip-at-a-time vertical swipe between trips; horizontal swipe between that trip's stop images | ✓ |

**User's choice:** "i think tik-tok full screen sounds better"
**Notes:** User's original description (name/location/username top, description bottom, swipeable stops middle) describes the *card layout*, which applies under either scroll model — the discussion resolved which *navigation gesture* wraps that layout.

---

## "For You" ranking scope

| Option | Description | Selected |
|--------|-------------|----------|
| Pure feed, no ranking | Same feed as REQUIREMENTS.md's original SOCIAL-01 (paginated/searchable PUBLIC trips), just restyled | |
| Lightweight interest-based ranking | Trips matching viewer's general interests ranked first, rest by recency/fallback | ✓ |
| Full personalization/recommendation engine | New, larger scope — not selected | |

**User's choice:** "we can have a little bit of personalization for now, trips including you general interests raking first"
**Notes:** Explicitly scoped down ("a little bit," "for now") — captured as D-05 in CONTEXT.md, deliberately not a recommendation-engine build.

**Follow-up: source of "interests"**

| Option | Description | Selected |
|--------|-------------|----------|
| Infer from viewer's own trip tags | No new storage, weaker signal | |
| Stored profile interests field | Requires new profile page/field | ✓ |

**User's choice:** Implied by requesting a profile page in the same turn — profile page carries the interests field (D-06/D-07).

---

## Profile page scope

| Option | Description | Selected |
|--------|-------------|----------|
| Fold into Phase 6 now | Minimal page: username, join date, interests | ✓ |
| Keep out, bare username only | Defer full profile page to a later phase/backlog | |

**User's choice:** "i would say profile page in phase 6"
**Notes:** Flagged as new scope beyond the original 8-phase roadmap before the user decided; user's rationale (see below) was to front-load functionality given the long fall break.

---

## On-card engagement actions

| Option | Description | Selected |
|--------|-------------|----------|
| On-card action rail (TikTok side-rail pattern) | Like/save/clone tappable directly on the full-screen feed card | ✓ |
| Feed read-only, actions on full trip-view page only | User must tap into the trip to like/save/clone | |

**User's choice:** "yes on card engagement works, like tik tok"

---

## No-photo trips

| Option | Description | Selected |
|--------|-------------|----------|
| Exclude from feed | Skip trips with zero stop photos entirely | |
| Placeholder image | Generic image/icon in place of a photo | |
| Text-based card | Show stop description/text instead of an image | ✓ |

**User's choice:** "no photo trips can just show the stop description and other stuff which can be suitable and we can think about later"
**Notes:** Exact visual treatment explicitly deferred to planning/UI design (see CONTEXT.md D-03).

---

## Milestone-level fall/winter sequencing

Not an original Phase 6 gray area — raised by the user mid-discussion as a milestone-scope note, not a new capability, so handled inline rather than deferred.

**User's statement:** "we have a 4 month long break and then the next semester start in jan and be 3 months as well, but i do want to get most of the functionality done in fall, so refactor the plan to have most of the functionality be done in fall."

**Resolution:** Confirmed the existing roadmap structure already puts all feature work (Phases 1-7) in the fall-break window, with only Phase 8 (winter) as hardening/regression. Made this explicit with `(Fall)`/`(Winter)` tags per phase in ROADMAP.md's phase list and an updated Overview paragraph, rather than restructuring which tasks land in which phase — no functional re-sequencing was needed, only making the existing intent explicit and logging it as a Key Decision in PROJECT.md.

---

## Claude's Discretion

- Exact text-card fallback visuals for no-photo trips
- Exact interest-tag taxonomy (free text vs. fixed categories) for the profile interests field
- Whether feed ranking is computed at query time or precomputed/cached

## Deferred Ideas

None — both scope additions raised during this discussion (profile page, interest ranking) were pulled into Phase 6 rather than deferred to a future phase.
