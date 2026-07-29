import {
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonButton, IonIcon, IonSpinner, ToastController } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { addCircleOutline, closeOutline } from 'ionicons/icons';
import { TripService } from '../../../../core/services/trip.service';
import { SuggestedStopResponse, StopResponse } from '../../../../core/models/trip.model';

// NOTE (flagged for Tanish): SCRUM-156's AC mentions description/category/duration
// per card. The backend's SuggestedStopResponse only has order/name/lat/lng/reason
// (see AiItineraryService / SuggestedItinerary.SuggestedStop) -- no such fields
// exist. Using `reason` as the descriptive line below. Either amend the AC or
// file a backend follow-up if those fields are actually wanted.
interface SuggestionCardState {
  suggestion: SuggestedStopResponse;
  accepting: boolean;
  accepted: boolean;
}

// SCRUM-67b / SCRUM-156: renders AI suggestions as cards. Accept calls the existing
// POST /api/trips/{tripId}/stops endpoint directly -- nothing is persisted by
// ai-suggest itself (see AiController's javadoc).
@Component({
  selector: 'app-ai-suggestion-cards',
  templateUrl: 'ai-suggestion-cards.component.html',
  styleUrls: ['ai-suggestion-cards.component.scss'],
  imports: [CommonModule, IonButton, IonIcon, IonSpinner],
})
export class AiSuggestionCardsComponent implements OnChanges {
  @Input({ required: true }) tripId!: number;
  @Input() summary: string | null = null;
  @Input() suggestions: SuggestedStopResponse[] = [];
  @Output() stopAdded = new EventEmitter<StopResponse>();

  private tripService = inject(TripService);
  private toastCtrl = inject(ToastController);

  cards: SuggestionCardState[] = [];

  constructor() {
    addIcons({ 'add-circle-outline': addCircleOutline, 'close-outline': closeOutline });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['suggestions']) {
      this.cards = this.suggestions.map((suggestion) => ({
        suggestion,
        accepting: false,
        accepted: false,
      }));
    }
  }

  dismiss(card: SuggestionCardState): void {
    this.cards = this.cards.filter((c) => c !== card);
  }

  accept(card: SuggestionCardState): void {
    if (card.accepting || card.accepted) return;
    card.accepting = true;

    this.tripService
      .addStop(this.tripId, {
        name: card.suggestion.name,
        latitude: card.suggestion.latitude,
        longitude: card.suggestion.longitude,
        notes: card.suggestion.reason ?? undefined,
      })
      .subscribe({
        next: async (stop) => {
          card.accepting = false;
          card.accepted = true;
          this.stopAdded.emit(stop);
          const toast = await this.toastCtrl.create({
            message: `${stop.name} added to your trip.`,
            duration: 2000,
            color: 'success',
          });
          await toast.present();
        },
        error: async (err) => {
          card.accepting = false;
          const toast = await this.toastCtrl.create({
            message: err.message ?? 'Could not add stop.',
            duration: 2500,
            color: 'danger',
          });
          await toast.present();
        },
      });
  }
}