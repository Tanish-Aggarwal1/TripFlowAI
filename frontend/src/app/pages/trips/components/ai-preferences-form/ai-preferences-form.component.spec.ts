import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { AiPreferencesFormComponent } from './ai-preferences-form.component';
import { TripService } from '../../../../core/services/trip.service';
import { SuggestedItineraryResponse } from '../../../../core/models/trip.model';
import { expectNoA11yViolations, expectAllFormControlsLabeled } from '../../../../../testing/a11y';

describe('AiPreferencesFormComponent', () => {
  let component: AiPreferencesFormComponent;
  let fixture: ComponentFixture<AiPreferencesFormComponent>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const suggestion: SuggestedItineraryResponse = {
    tripId: 1,
    summary: 'A relaxed food-focused day',
    stops: [],
  };

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['suggestItinerary']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(
      Promise.resolve({ present: () => Promise.resolve() } as any),
    );

    await TestBed.configureTestingModule({
      imports: [AiPreferencesFormComponent],
      providers: [
        { provide: TripService, useValue: tripServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AiPreferencesFormComponent);
    component = fixture.componentInstance;
    component.tripId = 1;
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

  describe('submit', () => {
    it('blocks submission with zero interests', () => {
      component.interests = [];

      component.submit();

      expect(component.formError).toBe(
        'Add at least one interest so Gemini has something to work with.',
      );
      expect(tripServiceSpy.suggestItinerary).not.toHaveBeenCalled();
    });

    it('calls TripService.suggestItinerary and emits suggested on success', () => {
      component.interests = ['food'];
      component.budget = 'high';
      component.pace = 'relaxed';
      tripServiceSpy.suggestItinerary.and.returnValue(of(suggestion));
      spyOn(component.suggested, 'emit');

      component.submit();

      expect(tripServiceSpy.suggestItinerary).toHaveBeenCalledWith(1, {
        interests: ['food'],
        budget: 'high',
        pace: 'relaxed',
      });
      expect(component.suggested.emit).toHaveBeenCalledWith(suggestion);
      expect(component.submitting).toBeFalse();
    });

    it('shows an error toast on failure', async () => {
      component.interests = ['food'];
      tripServiceSpy.suggestItinerary.and.returnValue(
        throwError(() => ({ message: 'Rate limit exceeded.' })),
      );

      component.submit();
      await fixture.whenStable();

      expect(component.submitting).toBeFalse();
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Rate limit exceeded.', color: 'danger' }),
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