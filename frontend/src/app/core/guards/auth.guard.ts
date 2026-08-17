import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

// Checks token expiry fresh on every navigation, rather than trusting the
// isAuthenticated signal, which only updates on explicit login()/logout()
// calls and would otherwise let an already-expired session through.
//
// A lapsed access token is the ordinary case — 15 minutes of idling is enough — so it must
// redeem the refresh cookie before giving up. It must never call logout(), which revokes the
// 30-day refresh session server-side; refresh() already tears down locally when it fails.
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.hasValidToken()) {
    return true;
  }

  return authService.refresh().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
