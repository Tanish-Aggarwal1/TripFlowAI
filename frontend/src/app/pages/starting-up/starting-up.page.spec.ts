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
});
