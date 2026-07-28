import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { sessionExpiryInterceptor } from './session-expiry.interceptor';
import { AuthService } from '../services/auth.service';

describe('sessionExpiryInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionExpiryInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('logs out on a 401 from a protected endpoint', () => {
    httpClient.get('/api/trips').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/trips');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalled();
  });

  it('does not log out on a 401 from the login endpoint (expected wrong-credentials case)', () => {
    httpClient.post('/api/auth/login', {}).subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/auth/login');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('passes through non-401 errors without logging out', () => {
    httpClient.get('/api/trips').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/api/trips');
    req.flush({}, { status: 500, statusText: 'Internal Server Error' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('re-throws the original error for the caller to handle', (done) => {
    httpClient.get('/api/trips').subscribe({
      error: (err) => {
        expect(err.status).toBe(401);
        done();
      },
    });

    const req = httpMock.expectOne('/api/trips');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
  });
});
