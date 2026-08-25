import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular/standalone';
import { of, throwError, BehaviorSubject } from 'rxjs';
import { TripEditPage } from './trip-edit.page';
import { TripService } from '../../../core/services/trip.service';
import { MAX_STOPS, TripResponse } from '../../../core/models/trip.model';
import { expectNoA11yViolations, expectAllFormControlsLabeled } from '../../../../testing/a11y';

describe('TripEditPage', () => {
  let component: TripEditPage;
  let fixture: ComponentFixture<TripEditPage>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let router: Router;
  let alertCtrlSpy: jasmine.SpyObj<AlertController>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;
  let paramMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const existingTrip: TripResponse = {
    id: 5,
    title: 'Existing Trip',
    description: 'A nice trip',
    tags: ['fun', 'road-trip'],
    visibility: 'PUBLIC',
    status: 'DRAFT',
    ownerId: 1,
    stops: [
      {
        id: 1,
        name: 'Stop A',
        latitude: 1,
        longitude: 1,
        address: null,
        stopOrder: 0,
        status: 'PLANNED',
        notes: null,
        dayNumber: null,
        plannedTime: null,
        stopType: 'SIGHTSEEING',
      },
      {
        id: 2,
        name: 'Stop B',
        latitude: 2,
        longitude: 2,
        address: '2 Main St',
        stopOrder: 1,
        status: 'VISITED',
        notes: 'been here',
        dayNumber: 1,
        plannedTime: '09:00:00',
        stopType: 'MEAL',
      },
    ],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    routeGeometry: null,
    startDate: '2026-06-01',
    visitedStopCount: 1,
    completionPercentage: 0.5,
  };

  function configure(id: string | null): void {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['getTrip', 'createTrip', 'updateTrip']);
    alertCtrlSpy = jasmine.createSpyObj('AlertController', ['create']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));
    paramMap = new BehaviorSubject(convertToParamMap(id ? { id } : {}));

    TestBed.configureTestingModule({
      imports: [TripEditPage],
      providers: [
        provideRouter([]),
        { provide: TripService, useValue: tripServiceSpy },
        { provide: AlertController, useValue: alertCtrlSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
        { provide: ActivatedRoute, useValue: { paramMap } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TripEditPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  }

  describe('create mode (no id)', () => {
    beforeEach(() => configure(null));

    it('starts in create mode without loading a trip', () => {
      component.ngOnInit();

      expect(component.isEditMode).toBeFalse();
      expect(tripServiceSpy.getTrip).not.toHaveBeenCalled();
    });

    it('renders the empty form with a "Create Trip" submit button', () => {
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('ion-input')).toBeTruthy();
      expect(fixture.nativeElement.textContent).toContain('Create Trip');
      expect(fixture.nativeElement.textContent).not.toContain('Update Trip');
    });

    it('save() rejects a blank title without calling the service', async () => {
      component.ngOnInit();
      component.title = '   ';
      component.stops = [{ id: null, name: 'A', latitude: 1, longitude: 1 }];

      await component.save();

      expect(tripServiceSpy.createTrip).not.toHaveBeenCalled();
    });

    it('save() rejects an empty stop list without calling the service', async () => {
      component.ngOnInit();
      component.title = 'New Trip';
      component.stops = [];

      await component.save();

      expect(tripServiceSpy.createTrip).not.toHaveBeenCalled();
    });

    it('save() creates the trip and navigates to the dashboard on success', async () => {
      component.ngOnInit();
      component.title = 'New Trip';
      component.tagsInput = 'a, b';
      component.stops = [{ id: null, name: 'A', latitude: 1, longitude: 1 }];
      tripServiceSpy.createTrip.and.returnValue(of(existingTrip));

      await component.save();

      expect(tripServiceSpy.createTrip).toHaveBeenCalledWith(
        jasmine.objectContaining({ title: 'New Trip', tags: ['a', 'b'] }),
      );
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
      expect(component.saving).toBeFalse();
    });

    // CreateStopRequest has no id by design, so the always-null id must not be sent.
    it('save() strips the null id on create', async () => {
      component.ngOnInit();
      component.title = 'New Trip';
      component.stops = [{ id: null, name: 'A', latitude: 1, longitude: 1 }];
      tripServiceSpy.createTrip.and.returnValue(of(existingTrip));

      await component.save();

      const [request] = tripServiceSpy.createTrip.calls.mostRecent().args;
      expect(Object.keys(request.stops[0])).not.toContain('id');
    });

    it('save() surfaces the error message and stops saving on failure', async () => {
      component.ngOnInit();
      component.title = 'New Trip';
      component.stops = [{ id: null, name: 'A', latitude: 1, longitude: 1 }];
      tripServiceSpy.createTrip.and.returnValue(throwError(() => new Error('Something went wrong.')));

      await component.save();

      expect(component.error).toBe('Something went wrong.');
      expect(component.saving).toBeFalse();
    });
  });

  describe('edit mode (with id)', () => {
    beforeEach(() => configure('5'));

    it('loads the existing trip and populates form fields', () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));

      component.ngOnInit();

      expect(component.isEditMode).toBeTrue();
      expect(component.tripId).toBe(5);
      expect(tripServiceSpy.getTrip).toHaveBeenCalledWith(5);
      expect(component.title).toBe('Existing Trip');
      expect(component.tagsInput).toBe('fun, road-trip');
      expect(component.visibility).toBe('PUBLIC');
      expect(component.stops.length).toBe(2);
      expect(component.stops[0].name).toBe('Stop A');
      expect(component.loading).toBeFalse();
    });

    it('renders the loaded trip title in the input', () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));

      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Update Trip');
      expect(fixture.nativeElement.querySelector('ion-input')).toBeTruthy();
    });

    it('has no accessibility violations', async () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));

      fixture.detectChanges();

      await expectNoA11yViolations(fixture.nativeElement);
    });

    it('labels every form control', () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));

      fixture.detectChanges();

      expectAllFormControlsLabeled(fixture.nativeElement);
    });

    it('sets an error message when loading the existing trip fails', () => {
      tripServiceSpy.getTrip.and.returnValue(throwError(() => new Error('Trip not found.')));

      component.ngOnInit();

      expect(component.error).toBe('Trip not found.');
      expect(component.loading).toBeFalse();
    });

    it('save() updates the trip and navigates to the dashboard on success', async () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();
      tripServiceSpy.updateTrip.and.returnValue(of(existingTrip));

      await component.save();

      expect(tripServiceSpy.updateTrip).toHaveBeenCalledWith(
        5,
        jasmine.objectContaining({ title: 'Existing Trip' }),
      );
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    // The stop ids loaded from the server must reach the outgoing request unchanged.
    // The backend merges by id: a stop whose id arrives is updated in place and keeps its
    // status/dayNumber/plannedTime/stopType and photos, while an existing stop whose id is
    // missing from the payload is DELETED. So a dropped id here is silent data loss, not a
    // cosmetic mapping slip — this asserts the whole load -> state -> request round trip.
    it('save() round-trips every loaded stop id so the backend merges instead of deleting', async () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();
      tripServiceSpy.updateTrip.and.returnValue(of(existingTrip));

      await component.save();

      const [, request] = tripServiceSpy.updateTrip.calls.mostRecent().args;
      expect(request.stops.map((s) => s.id)).toEqual([1, 2]);
      expect(request.stops.map((s) => s.name)).toEqual(['Stop A', 'Stop B']);
    });

    it('save() preserves a startDate it never offered the user a way to edit', async () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();
      tripServiceSpy.updateTrip.and.returnValue(of(existingTrip));

      await component.save();

      const [, request] = tripServiceSpy.updateTrip.calls.mostRecent().args;
      expect(request.startDate).toBe('2026-06-01');
    });

    it('save() sends a null id for a stop added to an existing trip, so it inserts', async () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();
      component.stops = [...component.stops, { id: null, name: 'Stop C', latitude: 3, longitude: 3 }];
      tripServiceSpy.updateTrip.and.returnValue(of(existingTrip));

      await component.save();

      const [, request] = tripServiceSpy.updateTrip.calls.mostRecent().args;
      expect(request.stops.map((s) => s.id)).toEqual([1, 2, null]);
    });

    it('save() blocks past MAX_STOPS instead of letting the backend 400', async () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();
      component.stops = Array.from({ length: MAX_STOPS + 1 }, (_, i) => ({
        id: null,
        name: `Stop ${i}`,
        latitude: 1,
        longitude: 1,
      }));

      await component.save();

      expect(tripServiceSpy.updateTrip).not.toHaveBeenCalled();
    });

    it('viewOnMap navigates to the trip detail route', () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();

      component.viewOnMap();

      expect(router.navigate).toHaveBeenCalledWith(['/trips', 5]);
    });

    it('SCRUM-485: reloads the correct trip when the route param changes without destroying the component', () => {
      // Angular reuses this component instance for navigations matched by the same
      // route config (e.g. one trip's edit page to another's) — a paramMap
      // subscription must re-run loadTrip on every emission, not just once in ngOnInit.
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();
      expect(component.tripId).toBe(5);

      const otherTrip: TripResponse = { ...existingTrip, id: 9, title: 'Other Trip' };
      tripServiceSpy.getTrip.and.returnValue(of(otherTrip));
      paramMap.next(convertToParamMap({ id: '9' }));

      expect(tripServiceSpy.getTrip).toHaveBeenCalledWith(9);
      expect(component.tripId).toBe(9);
      expect(component.title).toBe('Other Trip');
    });
  });

  describe('onStopsChanged', () => {
    beforeEach(() => configure(null));

    it('updates the stops array', () => {
      component.ngOnInit();
      const newStops = [{ id: null, name: 'X', latitude: 1, longitude: 1 }];

      component.onStopsChanged(newStops);

      expect(component.stops).toBe(newStops);
    });
  });

});
