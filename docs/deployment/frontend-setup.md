# Frontend Deployment (Render Static Site)

Covers the deployed frontend PWA — hosting, build configuration, and verification status. Backend deployment (env vars, deploy process, rollback) is documented separately in `docs/deployment.md`, which is authoritative for the backend.

## Deployed URL

`https://tripflowai-frontend.onrender.com`

Deployed against the live backend at `https://tripflowai.onrender.com` (see `docs/deployment.md`). Render free tier spins the backend down when idle — hit `/actuator/health` a few minutes before a demo to warm it up (same cold-start note as `docs/SDP/SDP.md` §10).

## Hosting

Render Static Site, auto-deploying from `main` on push (same platform/pattern as the backend, configured directly in the Render dashboard rather than checked into the repo).

- Build command: `npm run build` (Angular production build, `frontend/`)
- Publish directory: `frontend/www` (Ionic's default `ng build` output)
- Build-time environment variables (Mapbox token, API base URL) are set directly in Render's dashboard for this service — **not** the same secrets used by `.github/workflows/frontend-ci.yml`, which only builds for CI verification and never ships its artifact. See `docs/frontend-standards.md` §Environment Files for how the two paths differ.

## Verification status

| Check | Status |
|---|---|
| Deployed URL loads without errors | Verified — `docs/qa/prod-regression-auth-crud.md` |
| Signup/login work against the deployed backend | Verified — `docs/qa/prod-regression-auth-crud.md` |
| No CORS errors in browser console | Verified during the same regression pass (login flow completed against the live backend, which requires a clean CORS preflight) |
| PWA install prompt (mobile Safari + Chrome) | Not yet verified — open item |
| Lighthouse score (PWA + Performance + Accessibility) ≥ 90 | Not yet captured — open item |

The two open items above are tracked as follow-up work, not blockers for the current deployment — the app is live and functionally verified, but a formal Lighthouse pass and install-prompt check on physical devices haven't been run yet.

## Redeploying

Push to `main` (frontend changes only trigger `frontend-ci.yml` for CI, per its `paths: frontend/**` filter). Render's own auto-deploy watches `main` independently and rebuilds on every push, using the build-time env vars configured in its dashboard.

## Security headers

**Status: partially in-repo.** `frontend/src/index.html` ships a `<meta http-equiv="Content-Security-Policy">` covering `default-src`, `script-src`, `style-src`, `img-src`, `connect-src`, and `object-src` — this takes effect automatically on every deploy, no manual step needed.

`frame-ancestors`, `X-Frame-Options`, `X-Content-Type-Options`, and `Referrer-Policy` cannot be set via `<meta>` (browsers ignore `frame-ancestors` there, and the other three aren't CSP directives at all). Render Static Sites have no `_headers`-file support (unlike Netlify/Cloudflare Pages) — confirmed against Render's own docs — so these four must be added as a **one-time manual step** in the Render dashboard: this service → **Headers** tab → **Add Header**, one row per rule below (Path / Name / Value):

| Path | Name | Value |
|---|---|---|
| `/*` | `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https://res.cloudinary.com data: blob:; connect-src 'self' https://tripflowai.onrender.com https://api.mapbox.com https://events.mapbox.com https://api.cloudinary.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none';` |
| `/*` | `X-Frame-Options` | `DENY` |
| `/*` | `X-Content-Type-Options` | `nosniff` |
| `/*` | `Referrer-Policy` | `strict-origin-when-cross-origin` |

The header-level CSP row duplicates the meta tag's directives plus `frame-ancestors` — when both are present, browsers enforce the intersection, so this is a strictly stronger, not conflicting, layer. This mirrors how `MAPBOX_TOKEN`/`API_BASE_URL` are already dashboard-only for this service (see Hosting above) — Render gives no code-based mechanism (no `_headers` file; a `render.yaml` Blueprint would work but isn't adopted anywhere in this repo, and introducing one here would change how this manually-created service is managed, which is out of scope for a headers fix).

If `connect-src`'s API origin changes (e.g. a new backend host), update both this table and the `<meta>` tag in `index.html` together.
