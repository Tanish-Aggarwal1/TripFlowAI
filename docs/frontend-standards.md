# Frontend Coding Standards (Ionic / Angular)

Owner: Neel. Applies to all code under `frontend/src/`.

**Last updated:** 2026-08-13

## Framework & Version

- Ionic 8 + Angular 20, standalone components (no `NgModule`).
- TypeScript strict mode enabled.

## Component Pattern

- All components are **standalone** (the Angular 20 default) — omit the redundant `standalone: true` flag in `@Component` rather than declaring it.
- Use Angular 20 built-in control flow (`@if`, `@for`, `@switch`) — never `*ngIf`, `*ngFor`, `*ngSwitch`.
- Use the `inject()` function for dependency injection, not constructor parameters — enforced by `@angular-eslint/prefer-inject`.

```typescript
// correct
private readonly router = inject(Router);
private readonly tripService = inject(TripService);

// incorrect — lint error
constructor(private router: Router, private tripService: TripService) {}
```

## File Naming

- Pages: `feature-name.page.ts`, `feature-name.page.html`, `feature-name.page.scss`
- Components: `component-name.component.ts`, `.html`, `.scss`
- Services: `feature.service.ts`
- Models/interfaces: `feature.model.ts`

## Routing

- Routes defined in `app.routes.ts` using `loadComponent` for lazy loading.
- Guard functions (not class-based guards) for route protection.

## Styling

- Component-scoped SCSS (Angular view encapsulation).
- Global styles that must escape encapsulation (e.g. Mapbox marker/popup CSS) go in `global.scss` with a comment explaining why.

## HTTP / API Communication

- All backend calls go through a dedicated service (e.g. `TripService`, `AuthService`).
- Services use `HttpClient` with typed response generics.
- Auth token attached via an HTTP interceptor or manual `Authorization: Bearer` header.
- Base URL read from `environment.ts` (`environment.apiBaseUrl`).

## Environment Files

- `environment.ts` and `environment.prod.ts` are **committed, tracked files** with placeholder values (e.g. `__MAPBOX_TOKEN__`, `__API_BASE_URL__` in `environment.prod.ts`). Never put a real token in either — anything written there is in git history permanently.
- Prod values are injected at CI build time via GitHub Actions secrets (`sed` substitution in `frontend-ci.yml`).
- **Local dev never touches `environment.ts`.** Copy `src/environments/environment.local.ts.template` to `src/environments/environment.local.ts` and fill in a real `mapboxToken` there instead (added SCRUM-283). `environment.local.ts` is gitignored (`frontend/.gitignore`: `src/environments/*` ignored, `!src/environments/*.template` excepted — the template is tracked, the real file never is). `ng serve`/`ionic serve` use the `development` build configuration, which swaps it in via `angular.json`'s `fileReplacements` (`environment.ts` → `environment.local.ts`) — the committed `environment.ts` is never edited or read at runtime for local dev.
- `environment.prod.ts`'s `apiBaseUrl` (`__API_BASE_URL__`) is injected from the `API_BASE_URL` repo secret when set. SCRUM-73's backend is live (`https://tripflowai.onrender.com`), but the actual production frontend build runs through Render's own build-time env vars (see `docs/deployment/frontend-setup.md`), not this repo secret — so `API_BASE_URL` is currently unset here and `frontend-ci.yml` logs a warning and leaves the placeholder in place. Harmless for CI verification builds (`ng build` there is never deployed); set the secret here too if `frontend-ci.yml`'s build output is ever wired up to ship directly.

## Testing

- Spec files: `*.spec.ts`, co-located with the file they test.
- Test runner: Karma + Jasmine.
- Coverage: Istanbul via `karma-coverage`, reported as `json-summary` for CI PR comments.
- Use `provideHttpClient()` + `provideHttpClientTesting()` (not `HttpClientTestingModule`) in standalone component tests.
- Components that inject an Ionic controller service (`ToastController`, `AlertController`, etc.) mock that service directly in the TestBed rather than providing `provideIonicAngular()` — this isolates the unit under test and is what every current spec does.
- Include `provideRouter([])` for components that use `Router` or `RouterLink`.

## Linting

- ESLint with `@angular-eslint` recommended preset.
- `npm run lint` must pass with zero errors before opening a PR.
- Dependabot major-version bumps for `@angular/*`, `@angular-devkit/*`, `@angular-eslint/*`, and `jasmine-core` are ignored via `dependabot.yml` rules — accept only patch/minor updates for these packages.

## What NOT to Commit

- `node_modules/`, `www/`, `dist/`, `.angular/` — all gitignored.
- Real API tokens in `environment.ts` — use placeholders.