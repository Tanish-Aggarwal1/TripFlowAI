import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AlertController } from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import { DashboardPage } from './dashboard.page';
import { TripService } from '../../../core/services/trip.service';
import { PagedResponse, TripSummaryResponse } from '../../../core/models/trip.model';

describe('DashboardPage', () => {
  let component: DashboardPage;
  let fixture: ComponentFixture<DashboardPage>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let alertCtrlSpy: jasmine.SpyObj<AlertController>;

  const summary: TripSummaryResponse = {
    id: 1,
    title: 'Trip A',
    visibility: 'PRIVATE',
    status: 'DRAFT',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    stopCount: 2,
    coverPhotoUrl: null,
  };

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['listTrips', 'deleteTrip']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    alertCtrlSpy = jasmine.createSpyObj('AlertController', ['create']);

    tripServiceSpy.listTrips.and.returnValue(
      of({
        content: [summary],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      } as PagedResponse<TripSummaryResponse>),
    );

    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        { provide: TripService, useValue: tripServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: AlertController, useValue: alertCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPage);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
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

  describe('statusColor', () => {
    it('maps IN_PROGRESS to warning', () => {
      expect(component.statusColor('IN_PROGRESS')).toBe('warning');
    });

    it('maps COMPLETED to success', () => {
      expect(component.statusColor('COMPLETED')).toBe('success');
    });

    it('maps any other status to medium', () => {
      expect(component.statusColor('DRAFT')).toBe('medium');
    });
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

    it('sets an error message when delete fails', async () => {
      component.trips = [summary];
      const invokeDeleteHandler = stubAlertWithDeleteHandler();
      tripServiceSpy.deleteTrip.and.returnValue(throwError(() => new Error('Trip not found.')));
      const event = jasmine.createSpyObj<Event>('Event', ['stopPropagation']);

      await component.confirmDelete(summary, event);
      invokeDeleteHandler();

      expect(component.error).toBe('Trip not found.');
    });
  });
});
