import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import { AiSuggestionCardsComponent } from './ai-suggestion-cards.component';
import { TripService } from '../../../../core/services/trip.service';
import { StopResponse, SuggestedStopResponse } from '../../../../core/models/trip.model';

describe('AiSuggestionCardsComponent', () => {
  let component: AiSuggestionCardsComponent;
  let fixture: ComponentFixture<AiSuggestionCardsComponent>;
  let tripServiceSpy: jasmine.SpyObj<TripService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const suggestion1: SuggestedStopResponse = {
    order: 1,
    name: 'St. Lawrence Market',
    latitude: 43.6487,
    longitude: -79.3715,
    reason: 'Fits your interest in food.',
  };
  const suggestion2: SuggestedStopResponse = {
    order: 2,
    name: 'Casa Loma',
    latitude: 43.6780,
    longitude: -79.4094,
    reason: null,
  };

  function addedStop(overrides: Partial<StopResponse> = {}): StopResponse {
    return {
      id: 9,
      name: 'St. Lawrence Market',
      latitude: 43.6487,
      longitude: -79.3715,
      address: null,
      stopOrder: 1,
      status: 'PLANNED',
      notes: 'Fits your interest in food.',
      dayNumber: null,
      plannedTime: null,
      stopType: 'SIGHTSEEING',
      ...overrides,
    };
  }

  beforeEach(async () => {
    tripServiceSpy = jasmine.createSpyObj('TripService', ['addStop']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(
      Promise.resolve({ present: () => Promise.resolve() } as any),
    );

    await TestBed.configureTestingModule({
      imports: [AiSuggestionCardsComponent],
      providers: [
        { provide: TripService, useValue: tripServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AiSuggestionCardsComponent);
    component = fixture.componentInstance;
    component.tripId = 1;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('rebuilds cards from the suggestions input on change', () => {
    component.suggestions = [suggestion1, suggestion2];
    component.ngOnChanges({
      suggestions: {
        currentValue: component.suggestions,
        previousValue: [],
        firstChange: true,
        isFirstChange: () => true,
      },
    });

    expect(component.cards.length).toBe(2);
    expect(component.cards.every((c) => !c.accepting && !c.accepted)).toBeTrue();
  });

  describe('dismiss', () => {
    it('removes the card from the list', () => {
      component.cards = [
        { suggestion: suggestion1, accepting: false, accepted: false },
        { suggestion: suggestion2, accepting: false, accepted: false },
      ];

      component.dismiss(component.cards[0]);

      expect(component.cards.length).toBe(1);
      expect(component.cards[0].suggestion).toBe(suggestion2);
    });
  });

  describe('accept', () => {
    it('calls TripService.addStop and marks the card accepted on success', () => {
      const card = { suggestion: suggestion1, accepting: false, accepted: false };
      component.cards = [card];
      tripServiceSpy.addStop.and.returnValue(of(addedStop()));
      spyOn(component.stopAdded, 'emit');

      component.accept(card);

      expect(tripServiceSpy.addStop).toHaveBeenCalledWith(1, {
        name: 'St. Lawrence Market',
        latitude: 43.6487,
        longitude: -79.3715,
        notes: 'Fits your interest in food.',
      });
      expect(card.accepted).toBeTrue();
      expect(card.accepting).toBeFalse();
      expect(component.stopAdded.emit).toHaveBeenCalledWith(addedStop());
    });

    it('omits notes when reason is null', () => {
      const card = { suggestion: suggestion2, accepting: false, accepted: false };
      component.cards = [card];
      tripServiceSpy.addStop.and.returnValue(of(addedStop({ name: 'Casa Loma', notes: null })));

      component.accept(card);

      expect(tripServiceSpy.addStop).toHaveBeenCalledWith(1, {
        name: 'Casa Loma',
        latitude: 43.678,
        longitude: -79.4094,
        notes: undefined,
      });
    });

    it('resets accepting and shows an error toast on failure', async () => {
      const card = { suggestion: suggestion1, accepting: false, accepted: false };
      component.cards = [card];
      tripServiceSpy.addStop.and.returnValue(throwError(() => ({ message: 'Trip not found.' })));

      component.accept(card);
      await fixture.whenStable();

      expect(card.accepting).toBeFalse();
      expect(card.accepted).toBeFalse();
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({ message: 'Trip not found.', color: 'danger' }),
      );
    });

    it('is a no-op if the card is already accepting or accepted', () => {
      const card = { suggestion: suggestion1, accepting: true, accepted: false };
      component.cards = [card];

      component.accept(card);

      expect(tripServiceSpy.addStop).not.toHaveBeenCalled();
    });
  });
});