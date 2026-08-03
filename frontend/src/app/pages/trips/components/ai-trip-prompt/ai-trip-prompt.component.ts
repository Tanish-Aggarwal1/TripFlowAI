import { Component, EventEmitter, inject, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonItem,
  IonLabel,
  IonInput,
  IonTextarea,
  IonButton,
  IonIcon,
  IonSpinner,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { sparkles } from 'ionicons/icons';
import { TripService } from '../../../../core/services/trip.service';
import { GenerateTripRequest, TripResponse } from '../../../../core/models/trip.model';

const MAX_PROMPT_LENGTH = 1000;

// Free-text prompt on the dashboard that creates a brand-new trip in one call
// via POST /api/trips/ai-generate — distinct from ai-preferences-form, which
// only adds suggestions onto a trip that already exists.
@Component({
  selector: 'app-ai-trip-prompt',
  templateUrl: 'ai-trip-prompt.component.html',
  styleUrls: ['ai-trip-prompt.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    IonItem,
    IonLabel,
    IonInput,
    IonTextarea,
    IonButton,
    IonIcon,
    IonSpinner,
  ],
})
export class AiTripPromptComponent {
  @Output() created = new EventEmitter<TripResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private tripService = inject(TripService);
  private toastCtrl = inject(ToastController);

  promptText = '';
  title = '';

  submitting = false;
  formError = '';

  constructor() {
    addIcons({ sparkles });
  }

  submit(): void {
    this.formError = '';

    const prompt = this.promptText.trim();
    if (!prompt) {
      this.formError = 'Describe the trip you want.';
      return;
    }
    if (prompt.length > MAX_PROMPT_LENGTH) {
      this.formError = `Prompt must be at most ${MAX_PROMPT_LENGTH} characters.`;
      return;
    }
    if (this.submitting) return;

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
      error: async (err) => {
        this.submitting = false;
        const toast = await this.toastCtrl.create({
          message: err.message ?? 'Could not generate trip.',
          duration: 3000,
          color: 'danger',
        });
        await toast.present();
      },
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
