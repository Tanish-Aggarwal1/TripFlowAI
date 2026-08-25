import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
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

  it('attaches the bearer token to requests targeting the backend API when a token exists', () => {
    authServiceSpy.getToken.and.returnValue('abc-token');

    httpClient.get(`${environment.apiBaseUrl}/trips`).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/trips`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc-token');
    req.flush({});
  });

  it('does not attach an Authorization header when there is no token', () => {
    authServiceSpy.getToken.and.returnValue(null);

    httpClient.get(`${environment.apiBaseUrl}/trips`).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/trips`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not attach a token to /api/auth/** requests even when a token exists', () => {
    authServiceSpy.getToken.and.returnValue('abc-token');

    httpClient.post(`${environment.apiBaseUrl}/auth/login`, {}).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not attach the app JWT to external requests outside the backend API domain', () => {
    authServiceSpy.getToken.and.returnValue('abc-token');

    httpClient.post('https://api.cloudinary.com/v1_1/demo/image/upload', {}).subscribe();

    const req = httpMock.expectOne('https://api.cloudinary.com/v1_1/demo/image/upload');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not attach the app JWT to relative URLs outside the backend API base path', () => {
    authServiceSpy.getToken.and.returnValue('abc-token');

    httpClient.get('/assets/config.json').subscribe();

    const req = httpMock.expectOne('/assets/config.json');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });
});
