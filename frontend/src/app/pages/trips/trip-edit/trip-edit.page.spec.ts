import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import { TripEditPage } from './trip-edit.page';
import { TripService } from '../../../core/services/trip.service';
import { TripResponse } from '../../../core/models/trip.model';

describe('TripEditPage', () => {
  let component: TripEditPage;
  let fixture: ComponentFixture<TripEditPage>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let router: Router;
  let alertCtrlSpy: jasmine.SpyObj<AlertController>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

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
    ],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    routeGeometry: null,
    startDate: null,
  };

  function configure(id: string | null): void {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['getTrip', 'createTrip', 'updateTrip']);
    alertCtrlSpy = jasmine.createSpyObj('AlertController', ['create']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));

    TestBed.configureTestingModule({
      imports: [TripEditPage],
      providers: [
        provideRouter([]),
        { provide: TripService, useValue: tripServiceSpy },
        { provide: AlertController, useValue: alertCtrlSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => id } } },
        },
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

    it('save() rejects a blank title without calling the service', async () => {
      component.ngOnInit();
      component.title = '   ';
      component.stops = [{ name: 'A', latitude: 1, longitude: 1 }];

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
      component.stops = [{ name: 'A', latitude: 1, longitude: 1 }];
      tripServiceSpy.createTrip.and.returnValue(of(existingTrip));

      await component.save();

      expect(tripServiceSpy.createTrip).toHaveBeenCalledWith(
        jasmine.objectContaining({ title: 'New Trip', tags: ['a', 'b'] }),
      );
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
      expect(component.saving).toBeFalse();
    });

    it('save() surfaces the error message and stops saving on failure', async () => {
      component.ngOnInit();
      component.title = 'New Trip';
      component.stops = [{ name: 'A', latitude: 1, longitude: 1 }];
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
      expect(component.stops.length).toBe(1);
      expect(component.stops[0].name).toBe('Stop A');
      expect(component.loading).toBeFalse();
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

    it('viewOnMap navigates to the trip detail route', () => {
      tripServiceSpy.getTrip.and.returnValue(of(existingTrip));
      component.ngOnInit();

      component.viewOnMap();

      expect(router.navigate).toHaveBeenCalledWith(['/trips', 5]);
    });
  });

  describe('onStopsChanged', () => {
    beforeEach(() => configure(null));

    it('updates the stops array', () => {
      component.ngOnInit();
      const newStops = [{ name: 'X', latitude: 1, longitude: 1 }];

      component.onStopsChanged(newStops);

      expect(component.stops).toBe(newStops);
    });
  });

  describe('onBackButtonClick', () => {
    beforeEach(() => configure(null));

    it('prevents default back-button navigation and opens the confirm dialog', () => {
      component.ngOnInit();
      const presentSpy = jasmine.createSpy('present').and.returnValue(Promise.resolve());
      alertCtrlSpy.create.and.returnValue(Promise.resolve({ present: presentSpy } as any));
      const event = jasmine.createSpyObj<Event>('Event', ['preventDefault']);

      component.onBackButtonClick(event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(alertCtrlSpy.create).toHaveBeenCalled();
    });
  });
});
