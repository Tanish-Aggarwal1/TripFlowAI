import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let httpMock: HttpTestingController;

  const TOKEN_KEY = 'tripflow_token';
  const USER_KEY = 'tripflow_user';
  const LOGIN_URL = 'http://localhost:8080/api/auth/login';
  const REGISTER_URL = 'http://localhost:8080/api/auth/register';
  const REFRESH_URL = 'http://localhost:8080/api/auth/refresh';
  const LOGOUT_URL = 'http://localhost:8080/api/auth/logout';

  function makeJwt(payload: Record<string, unknown>): string {
    const base64url = (obj: unknown) => btoa(JSON.stringify(obj));
    return `${base64url({ alg: 'none' })}.${base64url(payload)}.sig`;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // ---------- hasValidToken: initial isAuthenticated state ----------

  describe('hasValidToken (initial isAuthenticated signal value)', () => {
    it('no stored token -> isAuthenticated starts false', () => {
      const service = TestBed.inject(AuthService);
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('valid non-expired token -> isAuthenticated starts true', () => {
      localStorage.setItem(TOKEN_KEY, makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 }));
      const service = TestBed.inject(AuthService);
      expect(service.isAuthenticated()).toBeTrue();
    });

    it('expired token -> isAuthenticated starts false', () => {
      localStorage.setItem(TOKEN_KEY, makeJwt({ exp: Math.floor(Date.now() / 1000) - 3600 }));
      const service = TestBed.inject(AuthService);
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('malformed token -> isAuthenticated starts false and construction does not throw', () => {
      localStorage.setItem(TOKEN_KEY, 'not-a-valid-jwt');
      expect(() => TestBed.inject(AuthService)).not.toThrow();
      expect(TestBed.inject(AuthService).isAuthenticated()).toBeFalse();
    });
  });

  // ---------- token persistence ----------

  describe('token persistence', () => {
    it('login success stores token/username and flips isAuthenticated', (done) => {
      const service = TestBed.inject(AuthService);
      const token = makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 });
      const response = {
        token,
        tokenType: 'Bearer',
        userId: 1,
        username: 'tanish',
        expiresAt: '2026-12-31T23:59:59Z',
      };

      service.login({ email: 'tanish@example.com', password: 'password123' }).subscribe(() => {
        expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
        expect(JSON.parse(localStorage.getItem(USER_KEY)!)).toEqual({ userId: 1, username: 'tanish' });
        expect(service.isAuthenticated()).toBeTrue();
        done();
      });

      const req = httpMock.expectOne(LOGIN_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      req.flush(response);
    });

    it('register success stores token/username and flips isAuthenticated', (done) => {
      const service = TestBed.inject(AuthService);
      const token = makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 });
      const response = {
        token,
        tokenType: 'Bearer',
        userId: 2,
        username: 'neel',
        expiresAt: '2026-12-31T23:59:59Z',
      };

      service
        .register({ username: 'neel', email: 'neel@example.com', password: 'password123' })
        .subscribe(() => {
          expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
          expect(service.isAuthenticated()).toBeTrue();
          done();
        });

      const req = httpMock.expectOne(REGISTER_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      req.flush(response);
    });

    it('getToken returns the stored token', () => {
      localStorage.setItem(TOKEN_KEY, 'abc-token');
      expect(TestBed.inject(AuthService).getToken()).toBe('abc-token');
    });

    it('getToken returns null when nothing is stored', () => {
      expect(TestBed.inject(AuthService).getToken()).toBeNull();
    });
  });

  // ---------- expiresAt: the schedule the silent-refresh timer arms from ----------

  describe('expiresAt signal', () => {
    it('is null when nothing is stored', () => {
      expect(TestBed.inject(AuthService).expiresAt()).toBeNull();
    });

    it('is populated from the stored token on construction, so a page reload keeps the schedule', () => {
      const exp = Math.floor(Date.now() / 1000) + 3600;
      localStorage.setItem(TOKEN_KEY, makeJwt({ exp }));

      expect(TestBed.inject(AuthService).expiresAt()).toBe(new Date(exp * 1000).toISOString());
    });

    it('is null when the stored token is already expired', () => {
      localStorage.setItem(TOKEN_KEY, makeJwt({ exp: Math.floor(Date.now() / 1000) - 60 }));
      expect(TestBed.inject(AuthService).expiresAt()).toBeNull();
    });

    it('is set from the login response', (done) => {
      const service = TestBed.inject(AuthService);

      service.login({ email: 'tanish@example.com', password: 'password123' }).subscribe(() => {
        expect(service.expiresAt()).toBe('2026-12-31T23:59:59Z');
        done();
      });

      httpMock.expectOne(LOGIN_URL).flush({
        token: makeJwt({ exp: Math.floor(Date.now() / 1000) + 900 }),
        tokenType: 'Bearer',
        userId: 1,
        username: 'tanish',
        expiresAt: '2026-12-31T23:59:59Z',
      });
    });
  });

  // ---------- refresh: the credentialed, CSRF-gated silent refresh call ----------

  describe('refresh', () => {
    it('POSTs to the refresh endpoint with credentials and the CSRF gate header', (done) => {
      const service = TestBed.inject(AuthService);

      service.refresh().subscribe(() => done());

      const req = httpMock.expectOne(REFRESH_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      expect(req.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
      req.flush({ token: 'new-token', tokenType: 'Bearer', expiresAt: '2026-12-31T23:59:59Z' });
    });

    it('stores the new access token and leaves the stored user record untouched', (done) => {
      const storedUser = JSON.stringify({ userId: 1, username: 'tanish' });
      localStorage.setItem(TOKEN_KEY, 'old-token');
      localStorage.setItem(USER_KEY, storedUser);
      const service = TestBed.inject(AuthService);

      service.refresh().subscribe(() => {
        expect(localStorage.getItem(TOKEN_KEY)).toBe('new-token');
        expect(localStorage.getItem(USER_KEY)).toBe(storedUser);
        done();
      });

      httpMock
        .expectOne(REFRESH_URL)
        .flush({ token: 'new-token', tokenType: 'Bearer', expiresAt: '2026-12-31T23:59:59Z' });
    });

    it('updates expiresAt and isAuthenticated from the response', (done) => {
      const service = TestBed.inject(AuthService);

      service.refresh().subscribe(() => {
        expect(service.expiresAt()).toBe('2026-12-31T23:59:59Z');
        expect(service.isAuthenticated()).toBeTrue();
        done();
      });

      httpMock
        .expectOne(REFRESH_URL)
        .flush({ token: 'new-token', tokenType: 'Bearer', expiresAt: '2026-12-31T23:59:59Z' });
    });

    it('propagates a 401 to the caller without clearing storage or navigating', (done) => {
      localStorage.setItem(TOKEN_KEY, 'old-token');
      localStorage.setItem(USER_KEY, JSON.stringify({ userId: 1, username: 'tanish' }));
      const service = TestBed.inject(AuthService);
      const router = TestBed.inject(Router);
      spyOn(router, 'navigate');

      service.refresh().subscribe({
        error: (err) => {
          expect(err.status).toBe(401);
          expect(localStorage.getItem(TOKEN_KEY)).toBe('old-token');
          expect(localStorage.getItem(USER_KEY)).not.toBeNull();
          expect(router.navigate).not.toHaveBeenCalled();
          done();
        },
      });

      httpMock.expectOne(REFRESH_URL).flush({}, { status: 401, statusText: 'Unauthorized' });
    });
  });

  // ---------- logout: revoke server-side first, then tear down locally ----------

  describe('logout', () => {
    function seedSession(): AuthService {
      localStorage.setItem(TOKEN_KEY, 'some-token');
      localStorage.setItem(USER_KEY, JSON.stringify({ userId: 1, username: 'tanish' }));
      return TestBed.inject(AuthService);
    }

    it('POSTs to the logout endpoint with credentials and the CSRF gate header', () => {
      seedSession().logout();

      const req = httpMock.expectOne(LOGOUT_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.withCredentials).toBeTrue();
      expect(req.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('clears storage, resets state, and navigates to /login once the call settles', () => {
      const service = seedSession();
      const router = TestBed.inject(Router);
      spyOn(router, 'navigate');

      service.logout();
      httpMock.expectOne(LOGOUT_URL).flush(null, { status: 204, statusText: 'No Content' });

      expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(localStorage.getItem(USER_KEY)).toBeNull();
      expect(service.isAuthenticated()).toBeFalse();
      expect(service.expiresAt()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('still clears storage and navigates when the logout call fails', () => {
      const service = seedSession();
      const router = TestBed.inject(Router);
      spyOn(router, 'navigate');

      service.logout();
      httpMock.expectOne(LOGOUT_URL).error(new ProgressEvent('error'), { status: 0 });

      expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(localStorage.getItem(USER_KEY)).toBeNull();
      expect(service.isAuthenticated()).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  // ---------- handleAuthError: status-code-to-message mapping ----------

  describe('handleAuthError (status-code mapping)', () => {
    it('401 maps to a fixed, non-revealing message regardless of what the backend sent', (done) => {
      const service = TestBed.inject(AuthService);

      service.login({ email: 'x@example.com', password: 'wrong' }).subscribe({
        error: (err) => {
          expect(err.message).toBe('Invalid credentials.');
          done();
        },
      });

      const req = httpMock.expectOne(LOGIN_URL);
      req.flush(
        { message: 'Some backend detail that must never leak to the client' },
        { status: 401, statusText: 'Unauthorized' },
      );
    });

    it('409 prefers the backend message when present', (done) => {
      const service = TestBed.inject(AuthService);

      service
        .register({ username: 'dup', email: 'dup@example.com', password: 'password123' })
        .subscribe({
          error: (err) => {
            expect(err.message).toBe('Email already registered: dup@example.com');
            done();
          },
        });

      const req = httpMock.expectOne(REGISTER_URL);
      req.flush({ message: 'Email already registered: dup@example.com' }, { status: 409, statusText: 'Conflict' });
    });

    it('409 falls back to a generic message when the backend sends none', (done) => {
      const service = TestBed.inject(AuthService);

      service
        .register({ username: 'dup', email: 'dup@example.com', password: 'password123' })
        .subscribe({
          error: (err) => {
            expect(err.message).toBe('Email already registered.');
            done();
          },
        });

      const req = httpMock.expectOne(REGISTER_URL);
      req.flush({}, { status: 409, statusText: 'Conflict' });
    });

    it('400 with fieldErrors shows the generic validation message and attaches fieldErrors', (done) => {
      const service = TestBed.inject(AuthService);

      service.register({ username: '', email: 'bad', password: 'short' }).subscribe({
        error: (err) => {
          expect(err.message).toBe('Please fix the errors below.');
          expect(err.fieldErrors).toEqual([{ field: 'username', message: 'must not be blank' }]);
          done();
        },
      });

      const req = httpMock.expectOne(REGISTER_URL);
      req.flush(
        { fieldErrors: [{ field: 'username', message: 'must not be blank' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    });

    it('400 with no fieldErrors falls back to the generic default message', (done) => {
      const service = TestBed.inject(AuthService);

      service.login({ email: 'x@example.com', password: 'y' }).subscribe({
        error: (err) => {
          expect(err.message).toBe('Something went wrong, please try again.');
          done();
        },
      });

      const req = httpMock.expectOne(LOGIN_URL);
      req.flush({}, { status: 400, statusText: 'Bad Request' });
    });

    it('network error (status 0) shows the auth-specific network message', (done) => {
      const service = TestBed.inject(AuthService);

      service.login({ email: 'x@example.com', password: 'y' }).subscribe({
        error: (err) => {
          expect(err.message).toBe('Network error. Please check your connection and try again.');
          done();
        },
      });

      const req = httpMock.expectOne(LOGIN_URL);
      req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    });
  });
});
