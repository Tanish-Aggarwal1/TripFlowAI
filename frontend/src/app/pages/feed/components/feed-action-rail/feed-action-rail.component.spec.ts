import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { FeedActionRailComponent } from './feed-action-rail.component';
import { TripService } from '../../../../core/services/trip.service';
import { FeedTrip } from '../../../../core/models/feed.model';
import { TripResponse } from '../../../../core/models/trip.model';

describe('FeedActionRailComponent', () => {
  let component: FeedActionRailComponent;
  let fixture: ComponentFixture<FeedActionRailComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const trip: FeedTrip = {
    id: 42,
    title: 'Coastal Road Trip',
    description: 'A scenic drive.',
    tags: [],
    ownerUsername: 'alice',
    likeCount: 3,
    createdAt: '2026-01-01T00:00:00Z',
    stops: [],
  };

  function tripResponse(id: number): TripResponse {
    return {
      id,
      title: 'Copy of Coastal Road Trip',
      description: null,
      tags: [],
      visibility: 'PRIVATE',
      status: 'DRAFT',
      ownerId: 1,
      stops: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      routeGeometry: null,
      startDate: null,
      visitedStopCount: 0,
      completionPercentage: 0,
    };
  }

  // ion-button is a Stencil web component: on hydration it moves aria-label off its
  // own host element and onto the native <button> inside its shadow root (Ionic's
  // inheritAttributes mechanism), so neither the accessible-name assertion nor the
  // click target can query the host element directly by [aria-label] — both need to
  // reach into the shadow root for the real interactive element.
  function nativeButton(cssClass: string): HTMLButtonElement {
    const host: HTMLElement = fixture.nativeElement.querySelector(`ion-button.${cssClass}`);
    return host.shadowRoot!.querySelector('button')!;
  }

  beforeEach(async () => {
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));

    await TestBed.configureTestingModule({
      imports: [FeedActionRailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        TripService,
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedActionRailComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('trip', trip);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders exactly three action controls, each with an accessible label', () => {
    const buttons: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('ion-button'));
    expect(buttons.length).toBe(3);

    const labels = buttons.map((btn) => btn.shadowRoot!.querySelector('button')!.getAttribute('aria-label'));
    expect(labels).toEqual(['Like trip', 'Save trip', 'Clone trip']);
  });

  it('tapping like calls TripService.likeTrip and flips to active state; tapping again unlikes', () => {
    expect(component.liked()).toBeFalse();

    nativeButton('like-button').click();

    const likeReq = httpMock.expectOne('http://localhost:8080/api/trips/42/like');
    expect(likeReq.request.method).toBe('POST');
    likeReq.flush(null);
    fixture.detectChanges();

    expect(component.liked()).toBeTrue();
    expect(component.likeCount()).toBe(4);

    nativeButton('like-button').click();

    const unlikeReq = httpMock.expectOne('http://localhost:8080/api/trips/42/like');
    expect(unlikeReq.request.method).toBe('DELETE');
    unlikeReq.flush(null);
    fixture.detectChanges();

    expect(component.liked()).toBeFalse();
    expect(component.likeCount()).toBe(3);
  });

  it('increments the displayed like count optimistically before the request resolves', () => {
    nativeButton('like-button').click();

    expect(component.likeCount()).toBe(4);

    httpMock.expectOne('http://localhost:8080/api/trips/42/like').flush(null);
  });

  it('reverts like state and count when the like request fails, and surfaces a toast', () => {
    nativeButton('like-button').click();

    expect(component.likeCount()).toBe(4);

    const req = httpMock.expectOne('http://localhost:8080/api/trips/42/like');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.liked()).toBeFalse();
    expect(component.likeCount()).toBe(3);
    expect(toastCtrlSpy.create).toHaveBeenCalled();
  });

  it('a rapid double-tap on like issues exactly one outstanding HTTP request', () => {
    const likeButton = nativeButton('like-button');
    likeButton.click();
    likeButton.click();

    const outstanding = httpMock.match('http://localhost:8080/api/trips/42/like');
    expect(outstanding.length).toBe(1);
    outstanding[0].flush(null);
  });

  it('tapping save calls TripService.saveTrip and flips to saved; tapping again unsaves', () => {
    nativeButton('save-button').click();

    const saveReq = httpMock.expectOne('http://localhost:8080/api/trips/42/save');
    expect(saveReq.request.method).toBe('POST');
    saveReq.flush(null);
    fixture.detectChanges();

    expect(component.saved()).toBeTrue();

    nativeButton('save-button').click();

    const unsaveReq = httpMock.expectOne('http://localhost:8080/api/trips/42/save');
    expect(unsaveReq.request.method).toBe('DELETE');
    unsaveReq.flush(null);
    fixture.detectChanges();

    expect(component.saved()).toBeFalse();
  });

  it('reverts save state when the save request fails, and surfaces a toast', () => {
    nativeButton('save-button').click();

    const req = httpMock.expectOne('http://localhost:8080/api/trips/42/save');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.saved()).toBeFalse();
    expect(toastCtrlSpy.create).toHaveBeenCalled();
  });

  it('tapping clone calls TripService.cloneTrip and navigates to the returned trip edit route on success', () => {
    const navigateSpy = spyOn(router, 'navigate');
    nativeButton('clone-button').click();

    const req = httpMock.expectOne('http://localhost:8080/api/trips/42/clone');
    expect(req.request.method).toBe('POST');
    req.flush(tripResponse(99));

    expect(navigateSpy).toHaveBeenCalledWith(['/trips', 99, 'edit']);
  });

  it('a failed clone does not navigate and surfaces a toast', () => {
    const navigateSpy = spyOn(router, 'navigate');
    nativeButton('clone-button').click();

    const req = httpMock.expectOne('http://localhost:8080/api/trips/42/clone');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });

    expect(navigateSpy).not.toHaveBeenCalled();
    expect(toastCtrlSpy.create).toHaveBeenCalled();
  });

  it('like and save never navigate; clone is the only control that does', () => {
    const navigateSpy = spyOn(router, 'navigate');

    nativeButton('like-button').click();
    httpMock.expectOne('http://localhost:8080/api/trips/42/like').flush(null);

    nativeButton('save-button').click();
    httpMock.expectOne('http://localhost:8080/api/trips/42/save').flush(null);

    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
