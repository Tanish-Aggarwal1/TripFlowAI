import { ChangeDetectorRef, Component, EventEmitter, inject, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonItem,
  IonInput,
  IonSelect,
  IonSelectOption,
  IonButton,
  IonIcon,
  IonSpinner,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { sparkles } from 'ionicons/icons';
import { TripService } from '../../../../core/services/trip.service';
import {
  ItineraryPreferencesRequest,
  SuggestedItineraryResponse,
} from '../../../../core/models/trip.model';
import { ToastService } from '../../../../core/services/toast.service';
import { InterestChipsComponent } from '../interest-chips/interest-chips.component';

// SCRUM-67a / SCRUM-155: collects interests/budget/pace and submits to the
// existing POST /api/trips/{id}/ai-suggest endpoint (SCRUM-64, already live).
// Interest limits (max 10, 50 chars each) mirror ItineraryPreferencesRequest's
// @Size constraints and live in app-interest-chips / trip.model.ts.
@Component({
  selector: 'app-ai-preferences-form',
  templateUrl: 'ai-preferences-form.component.html',
  styleUrls: ['ai-preferences-form.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    IonItem,
    IonInput,
    IonSelect,
    IonSelectOption,
    IonButton,
    IonIcon,
    IonSpinner,
    InterestChipsComponent,
  ],
})
export class AiPreferencesFormComponent {
  @Input({ required: true }) tripId!: number;
  @Output() suggested = new EventEmitter<SuggestedItineraryResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private tripService = inject(TripService);
  private toastService = inject(ToastService);
  private cdr = inject(ChangeDetectorRef);

  interests: string[] = [];
  budget: 'low' | 'medium' | 'high' = 'medium';
  pace: 'relaxed' | 'moderate' | 'packed' = 'moderate';

  submitting = false;
  formError = '';

  constructor() {
    addIcons({ sparkles });
  }

  submit(): void {
    this.formError = '';

    if (this.interests.length === 0) {
      this.formError = 'Add at least one interest so Gemini has something to work with.';
      return;
    }
    if (this.submitting) return;

    const request: ItineraryPreferencesRequest = {
      interests: this.interests,
      budget: this.budget,
      pace: this.pace,
    };

    this.submitting = true;
    this.tripService.suggestItinerary(this.tripId, request).subscribe({
      next: (response) => {
        this.submitting = false;
        this.suggested.emit(response);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.submitting = false;
        this.toastService.showError(err, 'Could not generate suggestions.', 3000);
        this.cdr.markForCheck();
      },
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}