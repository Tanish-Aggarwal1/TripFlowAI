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

  describe('submit', () => {
    it('blocks submission with an empty prompt', () => {
      component.promptText = '   ';

      component.submit();

      expect(component.formError).toBe('Describe the trip you want.');
      expect(tripServiceSpy.generateTripWithAi).not.toHaveBeenCalled();
    });

    it('blocks submission when the prompt exceeds 1000 characters', () => {
      component.promptText = 'x'.repeat(1001);

      component.submit();

      expect(component.formError).toBe('Prompt must be at most 1000 characters.');
      expect(tripServiceSpy.generateTripWithAi).not.toHaveBeenCalled();
    });

    it('calls TripService.generateTripWithAi and emits created on success', () => {
      component.promptText = '3 days in Kyoto, food and temples';
      tripServiceSpy.generateTripWithAi.and.returnValue(of(trip));
      spyOn(component.created, 'emit');

      component.submit();

      expect(tripServiceSpy.generateTripWithAi).toHaveBeenCalledWith({
        prompt: '3 days in Kyoto, food and temples',
        title: undefined,
      });
      expect(component.created.emit).toHaveBeenCalledWith(trip);
      expect(component.submitting).toBeFalse();
    });

    it('includes a trimmed title when provided', () => {
      component.promptText = 'a trip';
      component.title = '  My Custom Title  ';
      tripServiceSpy.generateTripWithAi.and.returnValue(of(trip));

      component.submit();

      expect(tripServiceSpy.generateTripWithAi).toHaveBeenCalledWith({
        prompt: 'a trip',
        title: 'My Custom Title',
      });
    });

    it('does not resubmit while a request is already in flight', () => {
      component.promptText = 'a trip';
      tripServiceSpy.generateTripWithAi.and.returnValue(of(trip));
      component.submitting = true;

      component.submit();

      expect(tripServiceSpy.generateTripWithAi).not.toHaveBeenCalled();
    });

    it('shows an error toast on failure', async () => {
      component.promptText = 'a trip';
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
