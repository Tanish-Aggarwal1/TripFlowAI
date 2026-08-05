import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import { AiTripPromptComponent } from './ai-trip-prompt.component';
import { TripService } from '../../../../core/services/trip.service';
import { TripResponse } from '../../../../core/models/trip.model';

describe('AiTripPromptComponent', () => {
  let component: AiTripPromptComponent;
  let fixture: ComponentFixture<AiTripPromptComponent>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const trip: TripResponse = {
    id: 1,
    title: 'Kyoto Food Tour',
    description: 'A foodie trip',
    tags: [],
    visibility: 'PRIVATE',
    status: 'DRAFT',
    ownerId: 1,
    stops: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    routeGeometry: null,
    startDate: null,
  };

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['generateTripWithAi']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(
      Promise.resolve({ present: () => Promise.resolve() } as any),
    );

    await TestBed.configureTestingModule({
      imports: [AiTripPromptComponent],
      providers: [
        { provide: TripService, useValue: tripServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AiTripPromptComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('addInterest / removeInterest', () => {
    it('adds a trimmed interest and clears the input', () => {
      component.interestInput = '  food  ';

      component.addInterest();

      expect(component.interests).toEqual(['food']);
      expect(component.interestInput).toBe('');
    });

    it('rejects an interest over 50 characters', () => {
      component.interestInput = 'x'.repeat(51);

      component.addInterest();

      expect(component.interests).toEqual([]);
      expect(component.formError).toBe('Each interest must be at most 50 characters.');
    });

    it('rejects more than 10 interests', () => {
      component.interests = Array.from({ length: 10 }, (_, i) => `interest${i}`);
      component.interestInput = 'one too many';

      component.addInterest();

      expect(component.interests.length).toBe(10);
      expect(component.formError).toBe('At most 10 interests are allowed.');
    });

    it('removes an interest', () => {
      component.interests = ['food', 'hiking'];

      component.removeInterest('food');

      expect(component.interests).toEqual(['hiking']);
    });
  });

  describe('submit', () => {
    it('blocks submission with an empty destination', () => {
      component.location = '   ';

      component.submit();

      expect(component.formError).toBe('Enter a destination.');
      expect(tripServiceSpy.generateTripWithAi).not.toHaveBeenCalled();
    });

    it('composes a prompt from the structured fields and calls generateTripWithAi', () => {
      component.location = 'Kyoto';
      component.days = 3;
      component.budget = 'high';
      component.pace = 'relaxed';
      component.interests = ['food', 'temples'];
      tripServiceSpy.generateTripWithAi.and.returnValue(of(trip));
      spyOn(component.created, 'emit');

      component.submit();

      expect(tripServiceSpy.generateTripWithAi).toHaveBeenCalledWith({
        prompt: '3-day trip to Kyoto, interests: food, temples, budget: high, pace: relaxed',
        title: undefined,
      });
      expect(component.created.emit).toHaveBeenCalledWith(trip);
      expect(component.submitting).toBeFalse();
    });

    it('omits the interests segment when none are added', () => {
      component.location = 'Kyoto';
      component.days = 5;
      tripServiceSpy.generateTripWithAi.and.returnValue(of(trip));

      component.submit();

      expect(tripServiceSpy.generateTripWithAi).toHaveBeenCalledWith({
        prompt: '5-day trip to Kyoto, budget: medium, pace: moderate',
        title: undefined,
      });
    });

    it('includes a trimmed title when provided', () => {
      component.location = 'Kyoto';
      component.title = '  My Custom Title  ';
      tripServiceSpy.generateTripWithAi.and.returnValue(of(trip));

      component.submit();

      expect(tripServiceSpy.generateTripWithAi).toHaveBeenCalledWith({
        prompt: jasmine.any(String),
        title: 'My Custom Title',
      });
    });

    it('does not resubmit while a request is already in flight', () => {
      component.location = 'Kyoto';
      tripServiceSpy.generateTripWithAi.and.returnValue(of(trip));
      component.submitting = true;

      component.submit();

      expect(tripServiceSpy.generateTripWithAi).not.toHaveBeenCalled();
    });

    it('shows an error toast on failure', async () => {
      component.location = 'Kyoto';
      tripServiceSpy.generateTripWithAi.and.returnValue(
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
