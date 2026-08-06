import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError, timeout, TimeoutError } from 'rxjs';
import { environment } from '../../../environments/environment';

const BACKEND_TIMEOUT_MS = 8000;

// AI generation/suggestion calls legitimately take up to 30s (see
// ai-trip-prompt.component.ts's own "This can take up to 30 seconds" hint) -
// exempt from the timeout so mid-generation isn't misread as a dead backend.
const EXEMPT_URL_PATTERNS = ['/ai-generate', '/ai-suggest'];

// SCRUM-273: Render's free-tier backend spins down on idle, and a cold-start
// request can just hang with no feedback. Any other API call that times out
// or fails to connect at all (status 0) gets redirected to /starting-up,
// which polls the health endpoint and sends the user back on once it's live.
export const backendAvailabilityInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  const isApiRequest = req.url.startsWith(environment.apiBaseUrl);
  const isExempt = EXEMPT_URL_PATTERNS.some((pattern) => req.url.includes(pattern));

  if (!isApiRequest || isExempt) {
    return next(req);
  }

  return next(req).pipe(
    timeout(BACKEND_TIMEOUT_MS),
    catchError((err) => {
      const looksLikeBackendDown =
        err instanceof TimeoutError || (err instanceof HttpErrorResponse && err.status === 0);

      if (looksLikeBackendDown && !router.url.startsWith('/starting-up')) {
        router.navigate(['/starting-up'], { queryParams: { redirect: router.url } });
      }

      return throwError(() => err);
    }),
  );
};
