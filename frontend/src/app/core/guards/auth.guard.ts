import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

// Checks token expiry fresh on every navigation, rather than trusting the
// isAuthenticated signal, which only updates on explicit login()/logout()
// calls and would otherwise let an already-expired session through.
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);

  if (authService.hasValidToken()) {
    return true;
  }
  authService.logout();
  return false;
};