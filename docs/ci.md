# CI/CD Pipeline

## Overview
GitHub Actions runs on every PR targeting main. A failing pipeline blocks merge (required status check).

## Workflow File
`.github/workflows/backend-ci.yml`

## Triggers
- Pull request opened/updated targeting `main`
- [Add: does it also run on push to main? Fill in once written]

## Stages
1. Checkout code
2. Set up JDK 21
3. Cache Maven dependencies
4. Run `mvn -B verify` (compiles + runs all tests)
5. Generate JaCoCo coverage report

## What Blocks a Merge
- Any failing test
- Build/compile failure
- [Add if you enable it: minimum coverage threshold]

## How to Read a Failure
1. Open the failed check on the PR
2. Click "Details" to view the Actions log
3. Look for the first red ✗ step — that's where it failed
4. Common causes: [fill in as you hit real ones this sprint]

## Required Status Check
Enabled in Settings → Branches → main → branch protection. PR cannot merge until this check is green.

## Screenshot Evidence
[Attach: green pipeline run, and one red pipeline run showing a blocked merge]