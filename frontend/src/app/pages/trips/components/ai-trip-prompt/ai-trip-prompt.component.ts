import { Component, EventEmitter, inject, Output } from '@angular/core';
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
import { GenerateTripRequest, TripResponse } from '../../../../core/models/trip.model';
import { ToastService } from '../../../../core/services/toast.service';

const MAX_PROMPT_LENGTH = 1000;
const MAX_INTEREST_LENGTH = 50;
const MAX_INTERESTS = 10;

// Dropdown-driven trip creation on the dashboard — composes a prompt from
// structured fields (days/location/budget/pace/interests) and sends it to
// POST /api/trips/ai-generate, same endpoint the old free-text box used.
// The backend only accepts a single prompt string, so the structure lives
// entirely on this side.
@Component({
  selector: 'app-ai-trip-prompt',
  templateUrl: 'ai-trip-prompt.component.html',
  styleUrls: ['ai-trip-prompt.component.scss'],
  imports: [
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
export class AiTripPromptComponent {
  @Output() created = new EventEmitter<TripResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private tripService = inject(TripService);
  private toastService = inject(ToastService);

  readonly dayOptions = Array.from({ length: 14 }, (_, i) => i + 1);

  location = '';
  days = 3;
  budget: 'low' | 'medium' | 'high' = 'medium';
  pace: 'relaxed' | 'moderate' | 'packed' = 'moderate';
  interestInput = '';
  interests: string[] = [];
  title = '';

  submitting = false;
  formError = '';

  constructor() {
    addIcons({ sparkles, 'close-circle': closeCircle });
  }

  addInterest(): void {
    const value = this.interestInput.trim();
    if (!value) return;

    if (value.length > MAX_INTEREST_LENGTH) {
      this.formError = `Each interest must be at most ${MAX_INTEREST_LENGTH} characters.`;
      return;
    }
    if (this.interests.length >= MAX_INTERESTS) {
      this.formError = `At most ${MAX_INTERESTS} interests are allowed.`;
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

  private buildPrompt(): string {
    const parts = [`${this.days}-day trip to ${this.location.trim()}`];
    if (this.interests.length > 0) {
      parts.push(`interests: ${this.interests.join(', ')}`);
    }
    parts.push(`budget: ${this.budget}`, `pace: ${this.pace}`);
    return parts.join(', ');
  }

  submit(): void {
    this.formError = '';

    const location = this.location.trim();
    if (!location) {
      this.formError = 'Enter a destination.';
      return;
    }
    if (this.submitting) return;

    const prompt = this.buildPrompt();
    if (prompt.length > MAX_PROMPT_LENGTH) {
      this.formError = `Prompt must be at most ${MAX_PROMPT_LENGTH} characters.`;
      return;
    }

    const request: GenerateTripRequest = {
      prompt,
      title: this.title.trim() || undefined,
    };

    this.submitting = true;
    this.tripService.generateTripWithAi(request).subscribe({
      next: (trip) => {
        this.submitting = false;
        this.created.emit(trip);
      },
      error: (err) => {
        this.submitting = false;
        this.toastService.showError(err, 'Could not generate trip.', 3000);
      },
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
