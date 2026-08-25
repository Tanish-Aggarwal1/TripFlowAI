import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { ToastController } from '@ionic/angular/standalone';
import { of, throwError, Subject, BehaviorSubject } from 'rxjs';
import { TripViewPage, sanitizeFilename } from './trip-view.page';
import { TripService } from '../../../core/services/trip.service';
import { StopResponse, TripResponse } from '../../../core/models/trip.model';
import { TripMapComponent } from '../components/trip-map/trip-map.component';

// Renders in place of the real TripMapComponent, which would otherwise construct a
// live mapboxgl.Map (network calls, WebGL) the moment TripViewPage's DOM is rendered
// — see trip-map.component.spec.ts for why that constructor is normally spied on.
@Component({ selector: 'app-trip-map', template: '', standalone: true })
class TripMapStubComponent {
  @Input() trip: TripResponse | null = null;
  @Input() optimizing = false;
}

describe('TripViewPage', () => {
  let component: TripViewPage;
  let fixture: ComponentFixture<TripViewPage>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;
  let router: Router;
  let paramMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

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
      visitedStopCount: 0,
      completionPercentage: 0,
      ...overrides,
    };
  }

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', [
      'getTrip',
      'optimizeTrip',
      'exportIcs',
      'exportPdf',
      'updateStop',
    ]);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(
      Promise.resolve({ present: () => Promise.resolve() } as any),
    );
    tripServiceSpy.getTrip.and.returnValue(of(trip({ id: 1 })));
    paramMap = new BehaviorSubject(convertToParamMap({ id: '1' }));

    TestBed.overrideComponent(TripViewPage, {
      remove: { imports: [TripMapComponent] },
      add: { imports: [TripMapStubComponent] },
    });

    await TestBed.configureTestingModule({
      imports: [TripViewPage],
      providers: [
        provideRouter([]),
        { provide: TripService, useValue: tripServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
        { provide: ActivatedRoute, useValue: { paramMap } },
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

  it('renders stop cards grouped by day without duplicate track-key errors', () => {
    // Regression guard for C-2: dayGroups() must consume sortedStops, not raw
    // trip.stops — unsorted input interleaving day 1/2/1 would otherwise split
    // into three groups sharing a track key instead of two.
    tripServiceSpy.getTrip.and.returnValue(
      of(
        trip({
          stops: [
            stop({ id: 3, stopOrder: 2, dayNumber: 1, name: 'Stop C' }),
            stop({ id: 1, stopOrder: 0, dayNumber: 2, name: 'Stop A' }),
            stop({ id: 2, stopOrder: 1, dayNumber: 1, name: 'Stop B' }),
          ],
        }),
      ),
    );

    expect(() => fixture.detectChanges()).not.toThrow();

    expect(fixture.nativeElement.querySelectorAll('.stop-card').length).toBe(3);
    expect(fixture.nativeElement.querySelectorAll('.day-divider').length).toBe(2);
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

    it('SCRUM-485: reloads the correct trip when the route param changes without destroying the component', () => {
      // Angular reuses this component instance for navigations matched by the same
      // route config (e.g. one trip's detail page to another's) — a paramMap
      // subscription must re-run loadTrip on every emission, not just once in ngOnInit.
      tripServiceSpy.getTrip.and.returnValue(of(trip({ id: 1, title: 'Trip One' })));
      component.ngOnInit();
      expect(component.trip?.title).toBe('Trip One');

      tripServiceSpy.getTrip.and.returnValue(of(trip({ id: 2, title: 'Trip Two' })));
      paramMap.next(convertToParamMap({ id: '2' }));

      expect(tripServiceSpy.getTrip).toHaveBeenCalledWith(2);
      expect(component.trip?.title).toBe('Trip Two');
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

  describe('exportToCalendar', () => {
    it('does nothing when no trip is loaded', () => {
      component.trip = null;

      component.exportToCalendar();

      expect(tripServiceSpy.exportIcs).not.toHaveBeenCalled();
    });

    it('does nothing when already exporting', () => {
      component.ngOnInit();
      component.exporting = true;

      component.exportToCalendar();

      expect(tripServiceSpy.exportIcs).not.toHaveBeenCalled();
    });

    it('triggers a download named after the trip title and resets exporting on success', fakeAsync(() => {
      component.trip = trip({ id: 1, title: 'Weekend Getaway' });
      const blob = new Blob(['BEGIN:VCALENDAR'], { type: 'text/calendar' });
      tripServiceSpy.exportIcs.and.returnValue(of(blob));
      const createObjectURLSpy = spyOn(window.URL, 'createObjectURL').and.returnValue('blob:mock-url');
      const revokeObjectURLSpy = spyOn(window.URL, 'revokeObjectURL');
      const realCreateElement = document.createElement.bind(document);
      let capturedAnchor: HTMLAnchorElement | undefined;
      spyOn(document, 'createElement').and.callFake((tag: string) => {
        const el = realCreateElement(tag);
        if (tag === 'a') capturedAnchor = el as HTMLAnchorElement;
        return el;
      });
      spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
        // Regression (SCRUM-435): Firefox ignores a programmatic click() on a detached
        // anchor, so the download only works if it's in the document at click time.
        expect(document.body.contains(this)).toBeTrue();
      });

      component.exportToCalendar();

      expect(tripServiceSpy.exportIcs).toHaveBeenCalledWith(1);
      expect(component.exporting).toBeFalse();
      expect(createObjectURLSpy).toHaveBeenCalledWith(blob);
      expect(capturedAnchor?.download).toBe('Weekend Getaway.ics');
      expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
      // Detached again once the click has fired.
      expect(document.body.contains(capturedAnchor!)).toBeFalse();
      // Not revoked synchronously with click() — that races the browser reading the blob.
      expect(revokeObjectURLSpy).not.toHaveBeenCalled();

      tick(0);
      expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock-url');
    }));

    it('falls back to a generic filename when the trip title sanitizes to empty', async () => {
      component.trip = trip({ id: 1, title: '???' });
      tripServiceSpy.exportIcs.and.returnValue(of(new Blob(['x'])));
      spyOn(window.URL, 'createObjectURL').and.returnValue('blob:mock-url');
      spyOn(window.URL, 'revokeObjectURL');
      const realCreateElement = document.createElement.bind(document);
      let capturedAnchor: HTMLAnchorElement | undefined;
      spyOn(document, 'createElement').and.callFake((tag: string) => {
        const el = realCreateElement(tag);
        if (tag === 'a') capturedAnchor = el as HTMLAnchorElement;
        return el;
      });
      spyOn(HTMLAnchorElement.prototype, 'click');

      component.exportToCalendar();
      await Promise.resolve();

      expect(capturedAnchor?.download).toBe('trip.ics');
    });

    it('resets exporting and shows an error toast on failure', async () => {
      component.ngOnInit();
      tripServiceSpy.exportIcs.and.returnValue(throwError(() => new Error('Export failed.')));

      component.exportToCalendar();
      await Promise.resolve();

      expect(component.exporting).toBeFalse();
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Export failed.', color: 'danger' }),
      );
    });
  });

  describe('exportToPdf', () => {
    it('does nothing when no trip is loaded', () => {
      component.trip = null;

      component.exportToPdf();

      expect(tripServiceSpy.exportPdf).not.toHaveBeenCalled();
    });

    it('does nothing when already exporting', () => {
      component.ngOnInit();
      component.exportingPdf = true;

      component.exportToPdf();

      expect(tripServiceSpy.exportPdf).not.toHaveBeenCalled();
    });

    it('calls TripService.exportPdf with the trip id and resets exportingPdf on success', async () => {
      component.trip = trip({ id: 1, title: 'Weekend Getaway' });
      const blob = new Blob(['%PDF-'], { type: 'application/pdf' });
      tripServiceSpy.exportPdf.and.returnValue(of(blob));
      spyOn(window.URL, 'createObjectURL').and.returnValue('blob:mock-url');
      spyOn(window.URL, 'revokeObjectURL');
      spyOn(HTMLAnchorElement.prototype, 'click');

      component.exportToPdf();
      await Promise.resolve();

      expect(tripServiceSpy.exportPdf).toHaveBeenCalledWith(1);
      expect(component.exportingPdf).toBeFalse();
    });

    it('leaves exportingPdf false when the call fails', async () => {
      component.ngOnInit();
      tripServiceSpy.exportPdf.and.returnValue(throwError(() => new Error('Export failed.')));

      component.exportToPdf();
      await Promise.resolve();

      expect(component.exportingPdf).toBeFalse();
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Export failed.', color: 'danger' }),
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

    it('groups by dayNumber correctly even when stops arrive unsorted by stopOrder', () => {
      component.trip = trip({
        stops: [
          stop({ id: 3, dayNumber: 2, stopOrder: 2 }),
          stop({ id: 1, dayNumber: 1, stopOrder: 0 }),
          stop({ id: 2, dayNumber: 1, stopOrder: 1 }),
        ],
      });

      const groups = component.dayGroups;

      expect(groups.length).toBe(2);
      expect(groups[0].dayNumber).toBe(1);
      expect(groups[0].stops.map((s) => s.id)).toEqual([1, 2]);
      expect(groups[1].dayNumber).toBe(2);
      expect(groups[1].stops.map((s) => s.id)).toEqual([3]);
    });

    it('stays correct after onStopAdded appends two unscheduled stops to a partially-scheduled trip', () => {
      component.trip = trip({
        stops: [stop({ id: 1, dayNumber: 1, stopOrder: 0 })],
      });

      component.onStopAdded(stop({ id: 2, dayNumber: null, stopOrder: 1 }));
      component.onStopAdded(stop({ id: 3, dayNumber: null, stopOrder: 2 }));

      const groups = component.dayGroups;

      expect(groups.length).toBe(2);
      expect(groups[0].dayNumber).toBe(1);
      expect(groups[0].stops.map((s) => s.id)).toEqual([1]);
      expect(groups[1].dayNumber).toBeNull();
      expect(groups[1].stops.map((s) => s.id)).toEqual([2, 3]);
    });
  });

  describe('AI suggestion modal (SCRUM-67 wiring)', () => {
    it('openAiSuggest resets suggestions and opens the modal', () => {
      component.aiSuggestions = { tripId: 1, summary: 'old', stops: [] };

      component.openAiSuggest();

      expect(component.aiModalOpen).toBeTrue();
      expect(component.aiSuggestions).toBeNull();
    });

    it('closeAiModal closes the modal and clears suggestions', () => {
      component.aiModalOpen = true;
      component.aiSuggestions = { tripId: 1, summary: 'x', stops: [] };

      component.closeAiModal();

      expect(component.aiModalOpen).toBeFalse();
      expect(component.aiSuggestions).toBeNull();
    });

    it('onSuggested stores the response so the template swaps to the cards view', () => {
      const response = { tripId: 1, summary: 'A food-focused day', stops: [] };

      component.onSuggested(response);

      expect(component.aiSuggestions).toEqual(response);
    });

    it('onStopAdded appends the new stop to the current trip without refetching', () => {
      component.trip = trip({ id: 1, stops: [stop({ id: 1 })] });

      component.onStopAdded(stop({ id: 2, name: 'Casa Loma' }));

      expect(component.trip!.stops.map((s) => s.id)).toEqual([1, 2]);
      // expect(tripServiceSpy.getTrip).toHaveBeenCalledTimes(1); // only the initial ngOnInit load
    });

    it('onStopAdded is a no-op if there is no loaded trip', () => {
      component.trip = null;

      component.onStopAdded(stop({ id: 2 }));

      expect(component.trip).toBeNull();
    });
  });

  describe('Edit stop modal (SCRUM-250 wiring)', () => {
    it('openEditStop sets the stop being edited', () => {
      const target = stop({ id: 2, name: 'Casa Loma' });

      component.openEditStop(target);

      expect(component.editingStop).toEqual(target);
    });

    it('closeEditStop clears the stop being edited', () => {
      component.editingStop = stop({ id: 2 });

      component.closeEditStop();

      expect(component.editingStop).toBeNull();
    });

    it('onStopUpdated replaces the matching stop in place and closes the modal', () => {
      component.trip = trip({ id: 1, stops: [stop({ id: 1, name: 'Old' }), stop({ id: 2 })] });
      component.editingStop = stop({ id: 1, name: 'Old' });

      component.onStopUpdated(stop({ id: 1, name: 'New Name' }));

      expect(component.trip!.stops.map((s) => [s.id, s.name])).toEqual([
        [1, 'New Name'],
        [2, 'Stop'],
      ]);
      expect(component.editingStop).toBeNull();
    });

    it('onStopUpdated is a no-op on the trip if there is no loaded trip', () => {
      component.trip = null;
      component.editingStop = stop({ id: 1 });

      component.onStopUpdated(stop({ id: 1, name: 'New Name' }));

      expect(component.trip).toBeNull();
      expect(component.editingStop).toBeNull();
    });
  });

  describe('toggleVisited (quick "Visited" toggle)', () => {
    it('sends a full echoed payload with status flipped to VISITED', () => {
      const target = stop({
        id: 2,
        name: 'Casa Loma',
        latitude: 43.678,
        longitude: -79.409,
        address: '1 Austin Terrace',
        notes: 'Bring camera',
        status: 'PLANNED',
      });
      component.trip = trip({ id: 1, stops: [target] });
      tripServiceSpy.updateStop.and.returnValue(of({ ...target, status: 'VISITED' }));

      component.toggleVisited(target);

      expect(tripServiceSpy.updateStop).toHaveBeenCalledWith(1, 2, {
        name: 'Casa Loma',
        latitude: 43.678,
        longitude: -79.409,
        address: '1 Austin Terrace',
        notes: 'Bring camera',
        status: 'VISITED',
      });
    });

    it('sends status flipped back to PLANNED when the stop is already VISITED', () => {
      const target = stop({ id: 2, status: 'VISITED' });
      component.trip = trip({ id: 1, stops: [target] });
      tripServiceSpy.updateStop.and.returnValue(of({ ...target, status: 'PLANNED' }));

      component.toggleVisited(target);

      expect(tripServiceSpy.updateStop).toHaveBeenCalledWith(
        1,
        2,
        jasmine.objectContaining({ status: 'PLANNED' }),
      );
    });

    it('patches the stop in place via onStopUpdated on success and clears the busy flag', () => {
      const target = stop({ id: 2, status: 'PLANNED' });
      component.trip = trip({ id: 1, stops: [target] });
      const updated = { ...target, status: 'VISITED' as const };
      tripServiceSpy.updateStop.and.returnValue(of(updated));

      component.toggleVisited(target);

      expect(component.markingVisitedId).toBeNull();
      expect(component.trip!.stops[0].status).toBe('VISITED');
    });

    it('does not issue a second call while one is already in flight for the same stop', () => {
      const target = stop({ id: 2, status: 'PLANNED' });
      component.trip = trip({ id: 1, stops: [target] });
      tripServiceSpy.updateStop.and.returnValue(new Subject<StopResponse>());

      component.toggleVisited(target);
      component.toggleVisited(target);

      expect(tripServiceSpy.updateStop).toHaveBeenCalledTimes(1);
    });

    it('does nothing when no trip is loaded', () => {
      component.trip = null;

      component.toggleVisited(stop({ id: 2 }));

      expect(tripServiceSpy.updateStop).not.toHaveBeenCalled();
    });

    it('shows an error toast and re-enables the button on failure', async () => {
      const target = stop({ id: 2, status: 'PLANNED' });
      component.trip = trip({ id: 1, stops: [target] });
      tripServiceSpy.updateStop.and.returnValue(throwError(() => new Error('Network error.')));

      component.toggleVisited(target);
      await Promise.resolve();

      expect(component.markingVisitedId).toBeNull();
      expect(component.trip!.stops[0].status).toBe('PLANNED');
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Network error.', color: 'danger' }),
      );
    });
  });

  describe('nextStopId', () => {
    it('returns the first PLANNED stop by stopOrder', () => {
      component.trip = trip({
        id: 1,
        stops: [
          stop({ id: 1, stopOrder: 0, status: 'VISITED' }),
          stop({ id: 2, stopOrder: 1, status: 'PLANNED' }),
          stop({ id: 3, stopOrder: 2, status: 'PLANNED' }),
        ],
      });

      expect(component.nextStopId).toBe(2);
    });

    it('skips SKIPPED stops', () => {
      component.trip = trip({
        id: 1,
        stops: [
          stop({ id: 1, stopOrder: 0, status: 'SKIPPED' }),
          stop({ id: 2, stopOrder: 1, status: 'PLANNED' }),
        ],
      });

      expect(component.nextStopId).toBe(2);
    });

    it('returns null when every stop is VISITED or SKIPPED', () => {
      component.trip = trip({
        id: 1,
        stops: [
          stop({ id: 1, stopOrder: 0, status: 'VISITED' }),
          stop({ id: 2, stopOrder: 1, status: 'SKIPPED' }),
        ],
      });

      expect(component.nextStopId).toBeNull();
    });

    it('returns null with no trip loaded', () => {
      component.trip = null;

      expect(component.nextStopId).toBeNull();
    });
  });
});

// SCRUM-261: matched-pair fixture set with TripExportControllerTest (backend) —
// same inputs must produce the same outputs on both sides.
describe('sanitizeFilename', () => {
  const fixtures: [string, string][] = [
    ['Trip: "Fun" / Times?', 'Trip Fun  Times'],
    ['Ottawa Weekend', 'Ottawa Weekend'],
    ['Café Trip', 'Caf Trip'],
    ['  ', 'trip'],
    ['', 'trip'],
  ];

  for (const [input, expected] of fixtures) {
    it(`sanitizes ${JSON.stringify(input)} to ${JSON.stringify(expected)}`, () => {
      expect(sanitizeFilename(input)).toBe(expected);
    });
  }

  it('truncates titles over 100 characters', () => {
    const longTitle = 'a'.repeat(150);

    expect(sanitizeFilename(longTitle)).toBe('a'.repeat(100));
  });
});
