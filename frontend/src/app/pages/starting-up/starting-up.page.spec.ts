import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { StartingUpPage } from './starting-up.page';
import { environment } from '../../../environments/environment';

describe('StartingUpPage', () => {
  let component: StartingUpPage;
  let fixture: ComponentFixture<StartingUpPage>;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  const healthUrl = `${environment.apiBaseUrl.replace(/\/api\/?$/, '')}/actuator/health`;

  function setUp(redirectParam: string | null): void {
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);

    TestBed.configureTestingModule({
      imports: [StartingUpPage],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => redirectParam } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StartingUpPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('checks health immediately on init', () => {
    setUp(null);
    fixture.detectChanges();

    const req = httpMock.expectOne(healthUrl);
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'UP' });
  });

  it('navigates to the redirect query param once the backend responds', () => {
    setUp('/trips/5');
    fixture.detectChanges();

    httpMock.expectOne(healthUrl).flush({ status: 'UP' });

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/trips/5');
  });

  it('navigates to /login when there is no redirect query param', () => {
    setUp(null);
    fixture.detectChanges();

    httpMock.expectOne(healthUrl).flush({ status: 'UP' });

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('falls back to /login for a scheme-relative redirect target', () => {
    setUp('//evil.example.com/steal');
    fixture.detectChanges();

    httpMock.expectOne(healthUrl).flush({ status: 'UP' });

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('falls back to /login for a redirect target that is not a relative path', () => {
    setUp('https://evil.example.com');
    fixture.detectChanges();

    httpMock.expectOne(healthUrl).flush({ status: 'UP' });

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('keeps polling on failure and stops once the backend responds', fakeAsync(() => {
    setUp(null);
    fixture.detectChanges();

    httpMock.expectOne(healthUrl).error(new ProgressEvent('error'), { status: 0, statusText: 'Down' });
    expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();

    tick(4000);
    httpMock.expectOne(healthUrl).flush({ status: 'UP' });
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/login');

    tick(4000);
    httpMock.expectNone(healthUrl);

    component.ngOnDestroy();
  }));

  it('stops polling on destroy', fakeAsync(() => {
    setUp(null);
    fixture.detectChanges();

    httpMock.expectOne(healthUrl).error(new ProgressEvent('error'), { status: 0, statusText: 'Down' });

    component.ngOnDestroy();
    tick(8000);

    httpMock.expectNone(healthUrl);
    expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
  }));

  it('gives up after the max attempt cap and shows the unavailable state', fakeAsync(() => {
    setUp(null);
    fixture.detectChanges();

    // Initial check + 29 more polls = 30 attempts total (MAX_POLL_ATTEMPTS).
    httpMock.expectOne(healthUrl).error(new ProgressEvent('error'), { status: 0, statusText: 'Down' });
    for (let i = 0; i < 28; i++) {
      tick(4000);
      httpMock.expectOne(healthUrl).error(new ProgressEvent('error'), { status: 0, statusText: 'Down' });
      expect(component.unavailable()).toBeFalse();
    }

    tick(4000);
    httpMock.expectOne(healthUrl).error(new ProgressEvent('error'), { status: 0, statusText: 'Down' });
    expect(component.unavailable()).toBeTrue();
    expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();

    // No further polling once the cap is hit.
    tick(8000);
    httpMock.expectNone(healthUrl);

    component.ngOnDestroy();
  }));

  it('resets and resumes polling on retry', fakeAsync(() => {
    setUp(null);
    fixture.detectChanges();

    for (let i = 0; i < 30; i++) {
      httpMock.expectOne(healthUrl).error(new ProgressEvent('error'), { status: 0, statusText: 'Down' });
      if (i < 29) {
        tick(4000);
      }
    }
    expect(component.unavailable()).toBeTrue();

    component.retry();
    expect(component.unavailable()).toBeFalse();

    httpMock.expectOne(healthUrl).flush({ status: 'UP' });
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/login');

    component.ngOnDestroy();
  }));
});
