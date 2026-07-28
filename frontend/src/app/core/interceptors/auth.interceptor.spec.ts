import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
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

  it('attaches the bearer token to non-auth requests when a token exists', () => {
    authServiceSpy.getToken.and.returnValue('abc-token');

    httpClient.get('/api/trips').subscribe();

    const req = httpMock.expectOne('/api/trips');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc-token');
    req.flush({});
  });

  it('does not attach an Authorization header when there is no token', () => {
    authServiceSpy.getToken.and.returnValue(null);

    httpClient.get('/api/trips').subscribe();

    const req = httpMock.expectOne('/api/trips');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not attach a token to /api/auth/** requests even when a token exists', () => {
    authServiceSpy.getToken.and.returnValue('abc-token');

    httpClient.post('/api/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });
});
