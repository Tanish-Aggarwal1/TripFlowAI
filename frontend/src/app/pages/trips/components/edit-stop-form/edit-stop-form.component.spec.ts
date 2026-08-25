import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { EditStopFormComponent } from './edit-stop-form.component';
import { TripService } from '../../../../core/services/trip.service';
import { StopResponse } from '../../../../core/models/trip.model';
import { expectNoA11yViolations, expectAllFormControlsLabeled } from '../../../../../testing/a11y';

describe('EditStopFormComponent', () => {
  let component: EditStopFormComponent;
  let fixture: ComponentFixture<EditStopFormComponent>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const stop: StopResponse = {
    id: 9,
    name: 'St. Lawrence Market',
    latitude: 43.6487,
    longitude: -79.3715,
    address: '93 Front St E',
    stopOrder: 1,
    status: 'PLANNED',
    notes: 'Go early.',
    dayNumber: null,
    plannedTime: null,
    stopType: 'SIGHTSEEING',
  };

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['updateStop']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(
      Promise.resolve({ present: () => Promise.resolve() } as any),
    );

    await TestBed.configureTestingModule({
      imports: [EditStopFormComponent],
      providers: [
        { provide: TripService, useValue: tripServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditStopFormComponent);
    component = fixture.componentInstance;
    component.tripId = 50;
    component.stop = stop;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('has no accessibility violations', async () => {
    await expectNoA11yViolations(fixture.nativeElement);
  });

  it('labels every form control', () => {
    expectAllFormControlsLabeled(fixture.nativeElement);
  });

  it('initializes form fields from the input stop', () => {
    expect(component.name).toBe('St. Lawrence Market');
    expect(component.address).toBe('93 Front St E');
    expect(component.notes).toBe('Go early.');
    expect(component.status).toBe('PLANNED');
  });

  it('defaults address/notes to empty strings when the stop has none', () => {
    component.stop = { ...stop, address: null, notes: null };

    component.ngOnInit();

    expect(component.address).toBe('');
    expect(component.notes).toBe('');
  });

  describe('submit', () => {
    it('blocks submission with a blank name', () => {
      component.name = '   ';

      component.submit();

      expect(component.formError).toBe('Name is required.');
      expect(tripServiceSpy.updateStop).not.toHaveBeenCalled();
    });

    it('does nothing if a submission is already in flight', () => {
      component.submitting = true;

      component.submit();

      expect(tripServiceSpy.updateStop).not.toHaveBeenCalled();
    });

    it('omits address/notes from the request when they trim to empty', () => {
      component.address = '   ';
      component.notes = '   ';
      tripServiceSpy.updateStop.and.returnValue(of({ ...stop, address: null, notes: null }));

      component.submit();

      expect(tripServiceSpy.updateStop).toHaveBeenCalledWith(50, 9, {
        name: 'St. Lawrence Market',
        latitude: 43.6487,
        longitude: -79.3715,
        address: undefined,
        notes: undefined,
        status: 'PLANNED',
      });
    });

    it('calls TripService.updateStop with edited fields, preserving lat/lng, and emits updated on success', () => {
      const updatedStop: StopResponse = { ...stop, name: 'New Name', status: 'VISITED' };
      component.name = 'New Name';
      component.status = 'VISITED';
      tripServiceSpy.updateStop.and.returnValue(of(updatedStop));
      spyOn(component.updated, 'emit');

      component.submit();

      expect(tripServiceSpy.updateStop).toHaveBeenCalledWith(50, 9, {
        name: 'New Name',
        latitude: 43.6487,
        longitude: -79.3715,
        address: '93 Front St E',
        notes: 'Go early.',
        status: 'VISITED',
      });
      expect(component.updated.emit).toHaveBeenCalledWith(updatedStop);
      expect(component.submitting).toBeFalse();
    });

    it('shows an error toast on failure', async () => {
      tripServiceSpy.updateStop.and.returnValue(
        throwError(() => ({ message: 'You do not have permission to do that.' })),
      );

      component.submit();
      await fixture.whenStable();

      expect(component.submitting).toBeFalse();
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({
          message: 'You do not have permission to do that.',
          color: 'danger',
        }),
      );
    });

    it('shows a fallback message when the error has none', async () => {
      tripServiceSpy.updateStop.and.returnValue(throwError(() => ({})));

      component.submit();
      await fixture.whenStable();

      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Could not update stop.', color: 'danger' }),
      );
    });
  });

  describe('cancel', () => {
    it('emits cancelled', () => {
      spyOn(component.cancelled, 'emit');

      component.cancel();

      expect(component.cancelled.emit).toHaveBeenCalled();
    });
  });
});
