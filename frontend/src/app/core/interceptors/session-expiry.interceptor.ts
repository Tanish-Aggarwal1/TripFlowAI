import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { SessionStateService } from '../services/session-state.service';

// D-06: a 401 mid-task must not yank the user off the page. This flips the session status
// and re-throws — nothing else. The banner and the intercepted-next-action dialog in
// AppComponent own what the user sees, and AuthService.logout owns the teardown.
// Login's 401 is the wrong-credentials case, refresh's is already handled by the service
// that issued it, and register's own error handling (SCRUM-486) owns whatever status it
// returns — none of the three are "your existing session is gone".
const SELF_HANDLED_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh'];

export const sessionExpiryInterceptor: HttpInterceptorFn = (req, next) => {
  const sessionState = inject(SessionStateService);

  return next(req).pipe(
    catchError((err) => {
      if (err.status === 401 && !SELF_HANDLED_PATHS.some((path) => req.url.includes(path))) {
        sessionState.markExpired();
      }
      return throwError(() => err);
    })
  );
};
