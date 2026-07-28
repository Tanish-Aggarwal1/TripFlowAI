import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { ToastController } from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import { TripViewPage } from './trip-view.page';
import { TripService } from '../../../core/services/trip.service';
import { StopResponse, TripResponse } from '../../../core/models/trip.model';

describe('TripViewPage', () => {
  let component: TripViewPage;
  let fixture: ComponentFixture<TripViewPage>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;
  let router: Router;

  function stop(overrides: Partial<StopResponse>): StopResponse {
    return {
      id: 1,
      name: 'Stop',
      latitude: 1,
      longitude: 1,
      address: null,
      stopOrder: 0,
      status: 'PLANNED',
      notes: null,
      dayNumber: null,
      plannedTime: null,
      stopType: 'SIGHTSEEING',
      ...overrides,
    };
  }

  function trip(overrides: Partial<TripResponse>): TripResponse {
    return {
      id: 1,
      title: 'Trip',
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
      ...overrides,
    };
  }

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['getTrip', 'optimizeTrip']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(
      Promise.resolve({ present: () => Promise.resolve() } as any),
    );
    tripServiceSpy.getTrip.and.returnValue(of(trip({ id: 1 })));

    await TestBed.configureTestingModule({
      imports: [TripViewPage],
      providers: [
        provideRouter([]),
        { provide: TripService, useValue: tripServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => '1' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TripViewPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('loadTrip', () => {
    it('sets the trip and clears loading on success', () => {
      const loaded = trip({ id: 1, title: 'Loaded Trip' });
      tripServiceSpy.getTrip.and.returnValue(of(loaded));

      component.ngOnInit();

      expect(component.trip).toEqual(loaded);
      expect(component.loading).toBeFalse();
      expect(component.error).toBeNull();
    });

    it('sets the error message and clears loading on failure', () => {
      tripServiceSpy.getTrip.and.returnValue(throwError(() => new Error('Trip not found.')));

      component.ngOnInit();

      expect(component.error).toBe('Trip not found.');
      expect(component.loading).toBeFalse();
      expect(component.trip).toBeNull();
    });
  });

  describe('editTrip', () => {
    it('navigates to the edit route when a trip is loaded', () => {
      component.ngOnInit();

      component.editTrip();

      expect(router.navigate).toHaveBeenCalledWith(['/trips', 1, 'edit']);
    });

    it('does nothing when no trip is loaded', () => {
      component.trip = null;

      component.editTrip();

      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('onOptimizeRequested', () => {
    it('does nothing when no trip is loaded', () => {
      component.trip = null;

      component.onOptimizeRequested();

      expect(tripServiceSpy.optimizeTrip).not.toHaveBeenCalled();
    });

    it('does nothing when already optimizing', () => {
      component.ngOnInit();
      component.optimizing = true;

      component.onOptimizeRequested();

      expect(tripServiceSpy.optimizeTrip).not.toHaveBeenCalled();
    });

    it('updates the trip and shows a success toast', async () => {
      component.ngOnInit();
      const optimized = trip({ id: 1, routeGeometry: '{"type":"LineString"}' });
      tripServiceSpy.optimizeTrip.and.returnValue(of(optimized));

      component.onOptimizeRequested();
      await Promise.resolve();

      expect(component.trip).toEqual(optimized);
      expect(component.optimizing).toBeFalse();
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Route optimized.', color: 'success' }),
      );
    });

    it('resets optimizing and shows an error toast on failure', async () => {
      component.ngOnInit();
      tripServiceSpy.optimizeTrip.and.returnValue(throwError(() => new Error('ORS unavailable.')));

      component.onOptimizeRequested();
      await Promise.resolve();

      expect(component.optimizing).toBeFalse();
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'ORS unavailable.', color: 'danger' }),
      );
    });
  });

  describe('dayGroups', () => {
    it('returns an empty array when no trip is loaded', () => {
      component.trip = null;

      expect(component.dayGroups).toEqual([]);
    });

    it('puts every stop in one unscheduled group when no dayNumber is set', () => {
      component.trip = trip({
        stops: [stop({ id: 1, dayNumber: null }), stop({ id: 2, dayNumber: null })],
      });

      const groups = component.dayGroups;

      expect(groups.length).toBe(1);
      expect(groups[0].dayNumber).toBeNull();
      expect(groups[0].stops.length).toBe(2);
    });

    it('groups consecutive stops sharing a dayNumber and splits on change', () => {
      component.trip = trip({
        stops: [
          stop({ id: 1, dayNumber: 1 }),
          stop({ id: 2, dayNumber: 1 }),
          stop({ id: 3, dayNumber: 2 }),
        ],
      });

      const groups = component.dayGroups;

      expect(groups.length).toBe(2);
      expect(groups[0].dayNumber).toBe(1);
      expect(groups[0].stops.map((s) => s.id)).toEqual([1, 2]);
      expect(groups[1].dayNumber).toBe(2);
      expect(groups[1].stops.map((s) => s.id)).toEqual([3]);
    });
  });

  describe('formatPlannedTime', () => {
    it('formats a morning time in AM', () => {
      expect(component.formatPlannedTime('09:05:00')).toBe('9:05 AM');
    });

    it('formats an afternoon time in PM', () => {
      expect(component.formatPlannedTime('14:30:00')).toBe('2:30 PM');
    });

    it('formats noon as 12 PM', () => {
      expect(component.formatPlannedTime('12:00:00')).toBe('12:00 PM');
    });

    it('formats midnight as 12 AM', () => {
      expect(component.formatPlannedTime('00:15:00')).toBe('12:15 AM');
    });
  });
});
