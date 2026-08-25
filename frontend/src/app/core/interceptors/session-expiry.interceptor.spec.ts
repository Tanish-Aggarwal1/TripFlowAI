import { HttpClient, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { SessionStateService } from '../services/session-state.service';
import { sessionExpiryInterceptor } from './session-expiry.interceptor';

describe('sessionExpiryInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let sessionStateSpy: jasmine.SpyObj<SessionStateService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    sessionStateSpy = jasmine.createSpyObj('SessionStateService', ['markExpired']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr(), withInterceptors([sessionExpiryInterceptor])),
        provideHttpClientTesting(),
        { provide: SessionStateService, useValue: sessionStateSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flush401(url: string): void {
    httpClient.get(url).subscribe({ error: () => {} });
    httpMock.expectOne(url).flush({}, { status: 401, statusText: 'Unauthorized' });
  }

  it('marks the session expired on a 401 from a protected endpoint', () => {
    flush401('/api/trips');

    expect(sessionStateSpy.markExpired).toHaveBeenCalled();
  });

  it('leaves the user where they are — no navigation on a 401 (D-06)', () => {
    flush401('/api/trips');

    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
  });

  it('does not mark the session expired on a 401 from login (wrong credentials)', () => {
    flush401('/api/auth/login');

    expect(sessionStateSpy.markExpired).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('SCRUM-486: does not mark the session expired on a 401 from register', () => {
    flush401('/api/auth/register');

    expect(sessionStateSpy.markExpired).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('does not double-handle a 401 from refresh — its caller already owns that', () => {
    flush401('/api/auth/refresh');

    expect(sessionStateSpy.markExpired).not.toHaveBeenCalled();
  });

  it('passes through non-401 errors untouched', () => {
    httpClient.get('/api/trips').subscribe({ error: () => {} });
    httpMock.expectOne('/api/trips').flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(sessionStateSpy.markExpired).not.toHaveBeenCalled();
  });

  it('re-throws the original error for the caller to handle', (done) => {
    httpClient.get('/api/trips').subscribe({
      error: (err) => {
        expect(err.status).toBe(401);
        done();
      },
    });

    httpMock.expectOne('/api/trips').flush({}, { status: 401, statusText: 'Unauthorized' });
  });
});
