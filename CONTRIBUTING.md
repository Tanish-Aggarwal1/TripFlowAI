# Contributing to TripFlowAI

This is a 4-person Sheridan College capstone team project — not open to external contributions. This document exists to formalize the conventions the team already follows, for onboarding and for grading evidence of process maturity (SPM course).

## Before you start

Read `SDP.md` first — it's the source of truth for branching, commits, PR process, and testing strategy. This file is a quick-reference summary; `SDP.md` is authoritative.

## Workflow

1. Pick up a ticket from the Jira board (`SCRUM` project, "Wanderlust").
2. Branch from `main`: `type/SCRUM-XXX-slug` (see `SDP.md` §5 for the type prefixes).
3. Commit using Conventional Commits + Jira Smart Commits: `[SCRUM-XXX] type(scope): message #transition #time <duration>`
4. Push and open a PR. Title must match `[SCRUM-XXX] type(scope): message` — a GitHub Action checks this automatically.
5. Fill out the PR template completely, including the serialize-point checklist.
6. Get at least 1 review. Neel reviews any DTO/API contract change; Pratham reviews auth-adjacent changes.
7. Merge only after the required CI check passes.

## Code style

- Java 21, Spring Boot 4.1 conventions
- DTOs are records, not classes
- Constructor injection via Lombok
- See `docs/LOGGING_STANDARD.md` for logging conventions — no `System.out`, ever

## Questions

Ask in the team channel, or check `SDP.md` and the `docs/` folder first — most process questions are already answered there.