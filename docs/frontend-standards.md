# Frontend Coding Standards (Ionic / Angular)

Owner: Neel. Applies to all code under `frontend/src/`.

**Last updated:** 2026-07-23

## Framework & Version

- Ionic 8 + Angular 20, standalone components (no `NgModule`).
- TypeScript strict mode enabled.

## Component Pattern

- All components are **standalone** (`standalone: true` in `@Component` decorator).
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

- `environment.ts` and `environment.prod.ts` are committed with **placeholder values** (e.g. `__MAPBOX_TOKEN__`).
- Real tokens injected at CI build time via GitHub Actions secrets (`sed` substitution in `frontend-ci.yml`).
- For local dev, each developer replaces placeholders with their own tokens locally (gitignored by the placeholder pattern, since the committed file has no real secret).

## Testing

- Spec files: `*.spec.ts`, co-located with the file they test.
- Test runner: Karma + Jasmine.
- Coverage: Istanbul via `karma-coverage`, reported as `json-summary` for CI PR comments.
- Use `provideHttpClient()` + `provideHttpClientTesting()` (not `HttpClientTestingModule`) in standalone component tests.
- Include `provideIonicAngular()` for components that inject Ionic services.
- Include `provideRouter([])` for components that use `Router` or `RouterLink`.

## Linting

- ESLint with `@angular-eslint` recommended preset.
- `npm run lint` must pass with zero errors before opening a PR.
- Dependabot major-version bumps for `@angular/*`, `@angular-devkit/*`, `@angular-eslint/*`, and `jasmine-core` are ignored via `dependabot.yml` rules — accept only patch/minor updates for these packages.

## What NOT to Commit

- `node_modules/`, `www/`, `dist/`, `.angular/` — all gitignored.
- Real API tokens in `environment.ts` — use placeholders.