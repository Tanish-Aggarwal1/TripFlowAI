import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonItem,
  IonLabel,
  IonInput,
  IonSelect,
  IonSelectOption,
  IonButton,
  IonIcon,
  IonSpinner,
  IonChip,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { sparkles, closeCircle } from 'ionicons/icons';
import { TripService } from '../../../../core/services/trip.service';
import {
  ItineraryPreferencesRequest,
  SuggestedItineraryResponse,
} from '../../../../core/models/trip.model';
import { ToastService } from '../../../../core/services/toast.service';

// SCRUM-67a / SCRUM-155: collects interests/budget/pace and submits to the
// existing POST /api/trips/{id}/ai-suggest endpoint (SCRUM-64, already live).
// Client-side limits mirror ItineraryPreferencesRequest's @Size constraints
// (max 10 interests, 50 chars each) so bad input never reaches the API.
@Component({
  selector: 'app-ai-preferences-form',
  templateUrl: 'ai-preferences-form.component.html',
  styleUrls: ['ai-preferences-form.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    IonItem,
    IonLabel,
    IonInput,
    IonSelect,
    IonSelectOption,
    IonButton,
    IonIcon,
    IonSpinner,
    IonChip,
  ],
})
export class AiPreferencesFormComponent {
  @Input({ required: true }) tripId!: number;
  @Output() suggested = new EventEmitter<SuggestedItineraryResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private tripService = inject(TripService);
  private toastService = inject(ToastService);

  interestInput = '';
  interests: string[] = [];
  budget: 'low' | 'medium' | 'high' = 'medium';
  pace: 'relaxed' | 'moderate' | 'packed' = 'moderate';

  submitting = false;
  formError = '';

  constructor() {
    addIcons({ sparkles, 'close-circle': closeCircle });
  }

  addInterest(): void {
    const value = this.interestInput.trim();
    if (!value) return;

    if (value.length > 50) {
      this.formError = 'Each interest must be at most 50 characters.';
      return;
    }
    if (this.interests.length >= 10) {
      this.formError = 'At most 10 interests are allowed.';
      return;
    }
    if (!this.interests.includes(value)) {
      this.interests = [...this.interests, value];
    }
    this.interestInput = '';
    this.formError = '';
  }

  removeInterest(interest: string): void {
    this.interests = this.interests.filter((i) => i !== interest);
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
      },
      error: (err) => {
        this.submitting = false;
        this.toastService.showError(err, 'Could not generate suggestions.', 3000);
      },
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}