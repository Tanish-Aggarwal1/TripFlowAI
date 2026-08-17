import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { RefreshResponse } from '../models/auth.model';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const refreshResponse: RefreshResponse = {
    token: 'new-token',
    tokenType: 'Bearer',
    expiresAt: '2026-12-31T23:59:59Z',
  };

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['hasValidToken', 'logout', 'refresh']);

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceSpy }, provideRouter([])],
    });
  });

  function runGuard(): boolean | UrlTree | Observable<boolean | UrlTree> {
    return TestBed.runInInjectionContext(
      () => authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree | Observable<boolean | UrlTree>;
  }

  it('allows navigation when the token is valid, checked fresh each time', () => {
    authServiceSpy.hasValidToken.and.returnValue(true);

    expect(runGuard()).toBeTrue();
    expect(authServiceSpy.refresh).not.toHaveBeenCalled();
    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('redeems the refresh cookie and allows navigation when only the access token has expired', (done) => {
    authServiceSpy.hasValidToken.and.returnValue(false);
    authServiceSpy.refresh.and.returnValue(of(refreshResponse));

    (runGuard() as Observable<boolean | UrlTree>).subscribe((result) => {
      expect(result).toBeTrue();
      // The whole point: a lapsed access token must not revoke the 30-day refresh session.
      expect(authServiceSpy.logout).not.toHaveBeenCalled();
      done();
    });
  });

  it('redirects to /login without revoking when the refresh itself fails', (done) => {
    authServiceSpy.hasValidToken.and.returnValue(false);
    authServiceSpy.refresh.and.returnValue(throwError(() => new Error('refresh failed')));
    const router = TestBed.inject(Router);

    (runGuard() as Observable<boolean | UrlTree>).subscribe((result) => {
      expect(result).toEqual(router.createUrlTree(['/login']));
      expect(authServiceSpy.logout).not.toHaveBeenCalled();
      done();
    });
  });
});
