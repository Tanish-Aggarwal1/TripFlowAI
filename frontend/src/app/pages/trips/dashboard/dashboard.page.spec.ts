import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import { DashboardPage } from './dashboard.page';
import { TripService } from '../../../core/services/trip.service';
import { PagedResponse, TripOwnerSummaryResponse } from '../../../core/models/trip.model';

describe('DashboardPage', () => {
  let component: DashboardPage;
  let fixture: ComponentFixture<DashboardPage>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let alertCtrlSpy: jasmine.SpyObj<AlertController>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const summary: TripOwnerSummaryResponse = {
    id: 1,
    title: 'Trip A',
    visibility: 'PRIVATE',
    status: 'DRAFT',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    stopCount: 2,
    coverPhotoUrl: null,
    visitedStopCount: 1,
    completionPercentage: 0.5,
  };

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['listTrips', 'deleteTrip']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    alertCtrlSpy = jasmine.createSpyObj('AlertController', ['create']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));

    tripServiceSpy.listTrips.and.returnValue(
      of({
        content: [summary],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      } as PagedResponse<TripOwnerSummaryResponse>),
    );

    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        { provide: TripService, useValue: tripServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: AlertController, useValue: alertCtrlSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPage);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the empty-state icon when there are no trips', () => {
    component.loading = false;
    component.trips = [];

    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('.empty-state ion-icon');
    expect(icon).toBeTruthy();
    expect(icon.getAttribute('name')).toBe('map-outline');
  });

  it('renders a trip card with title and stop count once trips load', () => {
    component.loadTrips();

    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('.trip-card');
    expect(cards.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Trip A');
    expect(fixture.nativeElement.textContent).toContain('2 stops');
  });

  describe('loadTrips', () => {
    it('populates trips and clears loading on success', () => {
      component.loadTrips();

      expect(component.trips).toEqual([summary]);
      expect(component.loading).toBeFalse();
      expect(component.error).toBeNull();
    });

    it('sets an error message and clears loading on failure', () => {
      tripServiceSpy.listTrips.and.returnValue(throwError(() => new Error('Network error.')));

      component.loadTrips();

      expect(component.error).toBe('Network error.');
      expect(component.loading).toBeFalse();
    });
  });

  it('ionViewWillEnter loads trips', () => {
    spyOn(component, 'loadTrips');

    component.ionViewWillEnter();

    expect(component.loadTrips).toHaveBeenCalled();
  });

  it('openTrip navigates to the trip detail route', () => {
    component.openTrip(summary);

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/trips', summary.id]);
  });

  it('editTrip stops event propagation and navigates to the edit route', () => {
    const event = jasmine.createSpyObj<Event>('Event', ['stopPropagation']);

    component.editTrip(summary, event);

    expect(event.stopPropagation).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/trips', summary.id, 'edit']);
  });

  it('createTrip navigates to the new-trip route', () => {
    component.createTrip();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/trips/new']);
  });

  describe('AI trip creation modal', () => {
    it('openAiCreate opens the modal', () => {
      component.openAiCreate();

      expect(component.aiModalOpen).toBeTrue();
    });

    it('closeAiModal closes the modal', () => {
      component.aiModalOpen = true;

      component.closeAiModal();

      expect(component.aiModalOpen).toBeFalse();
    });

    it('onAiTripCreated closes the modal but does not navigate yet', () => {
      component.aiModalOpen = true;
      const created = { ...summary, id: 42 } as any;

      component.onAiTripCreated(created);

      expect(component.aiModalOpen).toBeFalse();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
    });

    it('onAiModalDismissed navigates to the trip created just before dismissal', () => {
      const created = { ...summary, id: 42 } as any;
      component.onAiTripCreated(created);

      component.onAiModalDismissed();

      expect(routerSpy.navigate).toHaveBeenCalledWith(['/trips', 42]);
    });

    it('onAiModalDismissed does not navigate on a plain cancel/swipe dismiss', () => {
      component.aiModalOpen = true;

      component.onAiModalDismissed();

      expect(component.aiModalOpen).toBeFalse();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
    });
  });

  describe('completionPercent', () => {
    it('rounds a 3-of-5 trip to 60%', () => {
      const trip = { ...summary, stopCount: 5, visitedStopCount: 3, completionPercentage: 0.6 };

      expect(component.completionPercent(trip)).toBe(60);
    });

    it('returns 0 for a zero-stop trip', () => {
      const trip = { ...summary, stopCount: 0, visitedStopCount: 0, completionPercentage: 0 };

      expect(component.completionPercent(trip)).toBe(0);
    });
  });

  describe('statusColor', () => {
    it('maps each of the four real backend statuses to a distinct color', () => {
      expect(component.statusColor('DRAFT')).toBe('medium');
      expect(component.statusColor('PLANNED')).toBe('tertiary');
      expect(component.statusColor('ACTIVE')).toBe('warning');
      expect(component.statusColor('COMPLETED')).toBe('success');
    });

    it('never returns a color for the removed IN_PROGRESS value', () => {
      expect(component.statusColor('IN_PROGRESS')).toBe('medium');
    });
  });

  describe('statusLabel', () => {
    it('maps each of the four real backend statuses to a distinct, non-fallback label', () => {
      const labels = ['DRAFT', 'PLANNED', 'ACTIVE', 'COMPLETED'].map((s) => component.statusLabel(s));

      expect(labels).toEqual(['Draft', 'Planned', 'Active', 'Completed']);
      expect(new Set(labels).size).toBe(4);
    });

    it('falls back to the raw value for an unrecognized status', () => {
      expect(component.statusLabel('UNKNOWN')).toBe('UNKNOWN');
    });
  });

  describe('search and filters', () => {
    beforeEach(() => {
      fixture.detectChanges();
      tripServiceSpy.listTrips.calls.reset();
      tripServiceSpy.listTrips.and.returnValue(
        of({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        } as PagedResponse<TripOwnerSummaryResponse>),
      );
    });

    it('debounces rapid keystrokes into exactly one HTTP call carrying the final term', fakeAsync(() => {
      component.onSearchChange('p');
      tick(100);
      component.onSearchChange('pa');
      tick(100);
      component.onSearchChange('par');
      tick(350);

      expect(tripServiceSpy.listTrips).toHaveBeenCalledTimes(1);
      expect(tripServiceSpy.listTrips).toHaveBeenCalledWith(0, 20, { search: 'par' });
    }));

    it('an identical term repeated back-to-back does not issue a second request', fakeAsync(() => {
      component.onSearchChange('paris');
      tick(350);
      component.onSearchChange('paris');
      tick(350);

      expect(tripServiceSpy.listTrips).toHaveBeenCalledTimes(1);
    }));

    it('changing a filter issues exactly one debounced request carrying that filter', fakeAsync(() => {
      component.onStatusChange('ACTIVE');
      tick(350);

      expect(tripServiceSpy.listTrips).toHaveBeenCalledTimes(1);
      expect(tripServiceSpy.listTrips).toHaveBeenCalledWith(0, 20, { status: 'ACTIVE' });
    }));

    it('combines a search term and a filter into one request', fakeAsync(() => {
      component.onSearchChange('paris');
      tick(350);
      component.onVisibilityChange('PUBLIC');
      tick(350);

      expect(tripServiceSpy.listTrips).toHaveBeenCalledTimes(2);
      expect(tripServiceSpy.listTrips).toHaveBeenCalledWith(0, 20, {
        search: 'paris',
        visibility: 'PUBLIC',
      });
    }));

    it('clearFilters empties every filter and re-requests', fakeAsync(() => {
      component.onSearchChange('paris');
      tick(350);
      tripServiceSpy.listTrips.calls.reset();

      component.clearFilters();
      tick(350);

      expect(component.filters).toEqual({});
      expect(tripServiceSpy.listTrips).toHaveBeenCalledTimes(1);
      expect(tripServiceSpy.listTrips).toHaveBeenCalledWith(0, 20, {});
    }));
  });

  describe('confirmDelete', () => {
    function stubAlertWithDeleteHandler(): jasmine.Spy {
      let deleteHandler: () => void = () => {};
      alertCtrlSpy.create.and.callFake((opts: any) => {
        deleteHandler = opts.buttons[1].handler;
        return Promise.resolve({ present: () => Promise.resolve() } as any);
      });
      return jasmine.createSpy('invokeDeleteHandler').and.callFake(() => deleteHandler());
    }

    it('deletes the trip and removes it from the list when confirmed', async () => {
      component.trips = [summary];
      const invokeDeleteHandler = stubAlertWithDeleteHandler();
      tripServiceSpy.deleteTrip.and.returnValue(of(undefined));
      const event = jasmine.createSpyObj<Event>('Event', ['stopPropagation']);

      await component.confirmDelete(summary, event);
      invokeDeleteHandler();

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(tripServiceSpy.deleteTrip).toHaveBeenCalledWith(summary.id);
      expect(component.trips).toEqual([]);
    });

    it('shows a toast and keeps the trip list intact when delete fails', async () => {
      component.trips = [summary];
      const invokeDeleteHandler = stubAlertWithDeleteHandler();
      tripServiceSpy.deleteTrip.and.returnValue(throwError(() => new Error('Trip not found.')));
      const event = jasmine.createSpyObj<Event>('Event', ['stopPropagation']);

      await component.confirmDelete(summary, event);
      invokeDeleteHandler();

      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Trip not found.', color: 'danger' }),
      );
      expect(component.trips).toEqual([summary]);
      expect(component.error).toBeNull();
    });
  });
});
