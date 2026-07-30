import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['hasValidToken', 'logout']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });
  });

  function runGuard(): boolean {
    return TestBed.runInInjectionContext(
      () => authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as boolean,
    );
  }

  it('allows navigation when the token is valid, checked fresh each time', () => {
    authServiceSpy.hasValidToken.and.returnValue(true);

    expect(runGuard()).toBeTrue();
    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('logs out and blocks navigation when the token is missing or expired', () => {
    authServiceSpy.hasValidToken.and.returnValue(false);

    expect(runGuard()).toBeFalse();
    expect(authServiceSpy.logout).toHaveBeenCalled();
  });
});
