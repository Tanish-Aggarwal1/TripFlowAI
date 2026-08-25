import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { backendAvailabilityInterceptor } from './backend-availability.interceptor';
import { environment } from '../../../environments/environment';

describe('backendAvailabilityInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    (routerSpy as unknown as { url: string }).url = '/dashboard';

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr(), withInterceptors([backendAvailabilityInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('redirects to starting-up on a connection-level failure (status 0)', () => {
    httpClient.get(`${environment.apiBaseUrl}/trips`).subscribe({ error: () => {} });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/trips`);
    req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/starting-up'], {
      queryParams: { redirect: '/dashboard' },
    });
  });

  it('redirects to starting-up when the request times out', fakeAsync(() => {
    httpClient.get(`${environment.apiBaseUrl}/trips`).subscribe({ error: () => {} });

    httpMock.expectOne(`${environment.apiBaseUrl}/trips`);
    tick(9000);

    // timeout() unsubscribes from the source on firing, which cancels the
    // underlying request - nothing left to flush, that cancellation is itself
    // the expected outcome here.
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/starting-up'], {
      queryParams: { redirect: '/dashboard' },
    });
  }));

  it('does not redirect on a normal HTTP error response (e.g. 404)', () => {
    httpClient.get(`${environment.apiBaseUrl}/trips`).subscribe({ error: () => {} });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/trips`);
    req.flush({}, { status: 404, statusText: 'Not Found' });

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('exempts AI generate calls from the timeout, since they legitimately take up to 30s', fakeAsync(() => {
    httpClient.post(`${environment.apiBaseUrl}/trips/ai-generate`, {}).subscribe({ error: () => {} });

    tick(9000);
    expect(routerSpy.navigate).not.toHaveBeenCalled();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/trips/ai-generate`);
    req.flush({});
  }));

  it('exempts AI suggest calls from the timeout', fakeAsync(() => {
    httpClient.post(`${environment.apiBaseUrl}/trips/1/ai-suggest`, {}).subscribe({ error: () => {} });

    tick(9000);
    expect(routerSpy.navigate).not.toHaveBeenCalled();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/trips/1/ai-suggest`);
    req.flush({});
  }));

  it('exempts the silent refresh, so a cold start is not reported as an expired session', fakeAsync(() => {
    httpClient.post(`${environment.apiBaseUrl}/auth/refresh`, {}).subscribe({ error: () => {} });

    tick(9000);
    expect(routerSpy.navigate).not.toHaveBeenCalled();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/refresh`);
    req.flush({});
  }));

  it('does not redirect again when already on the starting-up page', () => {
    (routerSpy as unknown as { url: string }).url = '/starting-up';

    httpClient.get(`${environment.apiBaseUrl}/trips`).subscribe({ error: () => {} });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/trips`);
    req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('passes through non-API requests unmodified', () => {
    httpClient.get('/assets/some.json').subscribe({ error: () => {} });

    const req = httpMock.expectOne('/assets/some.json');
    req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});
