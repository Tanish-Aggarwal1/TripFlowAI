import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Output, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { GenerateTripRequest, TripResponse } from '../../../../core/models/trip.model';
import { ToastService } from '../../../../core/services/toast.service';
import { InterestChipsComponent } from '../interest-chips/interest-chips.component';

const MAX_PROMPT_LENGTH = 1000;

// Dropdown-driven trip creation on the dashboard — composes a prompt from
// structured fields (days/location/budget/pace/interests) and sends it to
// POST /api/trips/ai-generate, same endpoint the old free-text box used.
// The backend only accepts a single prompt string, so the structure lives
// entirely on this side.
@Component({
  selector: 'app-ai-trip-prompt',
  templateUrl: 'ai-trip-prompt.component.html',
  styleUrls: ['ai-trip-prompt.component.scss'],
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
export class AiTripPromptComponent {
  @Output() created = new EventEmitter<TripResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private tripService = inject(TripService);
  private toastService = inject(ToastService);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);

  readonly dayOptions = Array.from({ length: 14 }, (_, i) => i + 1);

  location = '';
  days = 3;
  budget: 'low' | 'medium' | 'high' = 'medium';
  pace: 'relaxed' | 'moderate' | 'packed' = 'moderate';
  interests: string[] = [];
  title = '';

  submitting = false;
  formError = '';

  constructor() {
    addIcons({ sparkles });
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
    this.tripService
      .generateTripWithAi(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (trip) => {
        this.submitting = false;
        this.created.emit(trip);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.submitting = false;
        this.toastService.showError(err, 'Could not generate trip.', 3000);
        this.cdr.markForCheck();
      },
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
