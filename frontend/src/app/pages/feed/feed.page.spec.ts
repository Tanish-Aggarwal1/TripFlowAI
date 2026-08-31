import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { FeedPage } from './feed.page';
import { DiscoveryService } from '../../core/services/discovery.service';
import { FeedTrip } from '../../core/models/feed.model';
import { PagedResponse } from '../../core/models/trip.model';

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

    const slides = fixture.nativeElement.querySelectorAll('swiper-slide');
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
