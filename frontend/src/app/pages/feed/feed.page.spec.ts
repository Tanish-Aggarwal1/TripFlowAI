import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { FeedPage } from './feed.page';
import { DiscoveryService } from '../../core/services/discovery.service';
import { FeedTrip } from '../../core/models/feed.model';
import { PagedResponse } from '../../core/models/trip.model';

function makeTrips(count: number, idOffset = 0): FeedTrip[] {
  return Array.from({ length: count }, (_, i) => ({
    id: idOffset + i + 1,
    title: `Trip ${idOffset + i + 1}`,
    description: null,
    tags: [],
    ownerUsername: 'alice',
    likeCount: 0,
    createdAt: '2026-01-01T00:00:00Z',
    stops: [{ id: idOffset + i + 100, name: 'Stop', address: null, stopOrder: 0, notes: null, photoUrls: [] }],
  }));
}

describe('FeedPage', () => {
  let component: FeedPage;
  let fixture: ComponentFixture<FeedPage>;
  let discoveryServiceSpy: jasmine.SpyObj<DiscoveryService>;

  const trip: FeedTrip = {
    id: 1,
    title: 'Road Trip',
    description: 'A trip.',
    tags: [],
    ownerUsername: 'alice',
    likeCount: 0,
    createdAt: '2026-01-01T00:00:00Z',
    stops: [
      { id: 10, name: 'First stop', address: null, stopOrder: 0, notes: null, photoUrls: [] },
    ],
  };

  function pageOf(trips: FeedTrip[]): PagedResponse<FeedTrip> {
    return { content: trips, page: { size: 20, number: 0, totalElements: trips.length, totalPages: 1 } };
  }

  beforeEach(async () => {
    discoveryServiceSpy = jasmine.createSpyObj('DiscoveryService', ['getFeed']);
    discoveryServiceSpy.getFeed.and.returnValue(of(pageOf([trip])));

    await TestBed.configureTestingModule({
      imports: [FeedPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DiscoveryService, useValue: discoveryServiceSpy },
      ],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedPage);
    component = fixture.componentInstance;
  });

  it('calls DiscoveryService.getFeed(0, 20) on init', () => {
    fixture.detectChanges();

    expect(discoveryServiceSpy.getFeed).toHaveBeenCalledWith(0, 20);
  });

  it('renders exactly one swiper-slide for one loaded trip', () => {
    fixture.detectChanges();

    const slides = fixture.nativeElement.querySelectorAll('.outer-slide');
    expect(slides.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Road Trip');
  });

  it('renders an empty-state message when zero trips are returned', () => {
    discoveryServiceSpy.getFeed.and.returnValue(of(pageOf([])));

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-state')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('swiper-slide').length).toBe(0);
  });

  it('renders an error state and does not throw when getFeed errors', () => {
    discoveryServiceSpy.getFeed.and.returnValue(throwError(() => new Error('Network error.')));

    expect(() => fixture.detectChanges()).not.toThrow();

    expect(fixture.nativeElement.querySelector('.error')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Network error.');
  });

  it('the /feed route requires authGuard', async () => {
    const { routes } = await import('../../app.routes');
    const feedRoute = routes.find((r) => r.path === 'feed');

    expect(feedRoute).toBeTruthy();
    expect(feedRoute?.canActivate?.length).toBeGreaterThan(0);
  });
});

// Task 4: paging behavior needs real outstanding-request assertions, so this
// suite wires the real DiscoveryService through HttpTestingController rather
// than the jasmine spy the suite above uses.
describe('FeedPage paging', () => {
  let component: FeedPage;
  let fixture: ComponentFixture<FeedPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeedPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        DiscoveryService,
      ],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitialPage(trips: FeedTrip[], number: number, totalPages: number): void {
    fixture.detectChanges();
    const req = httpMock.expectOne('http://localhost:8080/api/discovery/feed?page=0&size=20');
    req.flush({ content: trips, page: { size: 20, number, totalElements: trips.length, totalPages } });
  }

  function crossThreshold(): void {
    component.onSlideChange(new CustomEvent('swiperslidechange', { detail: [{ activeIndex: component.trips().length - 3 }] }));
  }

  it('requests and appends the next page once the active index reaches trips().length - 3', () => {
    flushInitialPage(makeTrips(5), 0, 2);

    crossThreshold();

    const req = httpMock.expectOne('http://localhost:8080/api/discovery/feed?page=1&size=20');
    req.flush({ content: makeTrips(5, 5), page: { size: 20, number: 1, totalElements: 10, totalPages: 2 } });

    expect(component.trips().length).toBe(10);
    expect(component.loadingMore()).toBeFalse();
  });

  it('issues no further request once the loaded page is the last one', () => {
    flushInitialPage(makeTrips(5), 0, 1);

    crossThreshold();

    const outstanding = httpMock.match('http://localhost:8080/api/discovery/feed?page=1&size=20');
    expect(outstanding.length).toBe(0);
  });

  it('collapses two rapid threshold-crossing slide-change events into one outstanding request', () => {
    flushInitialPage(makeTrips(5), 0, 2);

    crossThreshold();
    crossThreshold();

    const outstanding = httpMock.match('http://localhost:8080/api/discovery/feed?page=1&size=20');
    expect(outstanding.length).toBe(1);
    outstanding[0].flush({ content: [], page: { size: 20, number: 1, totalElements: 5, totalPages: 2 } });
  });

  it('leaves already-loaded trips intact and surfaces an error when the next-page request fails', () => {
    flushInitialPage(makeTrips(5), 0, 2);

    crossThreshold();

    const req = httpMock.expectOne('http://localhost:8080/api/discovery/feed?page=1&size=20');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });

    expect(component.trips().length).toBe(5);
    expect(component.loadingMore()).toBeFalse();
    expect(component.error()).toBeTruthy();
  });
});
