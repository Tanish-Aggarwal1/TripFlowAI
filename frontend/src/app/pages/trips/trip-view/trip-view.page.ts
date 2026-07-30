import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import {
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonButton,
  IonButtons,
  IonBackButton,
  IonSpinner,
  IonIcon,
  IonList,
  IonItem,
  IonItemDivider,
  IonLabel,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { create, calendarOutline, sparkles } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { StopResponse, TripResponse, SuggestedItineraryResponse } from '../../../core/models/trip.model';
import { TripMapComponent } from '../components/trip-map/trip-map.component';
import { IonModal } from '@ionic/angular/standalone';
import { AiPreferencesFormComponent } from '../components/ai-preferences-form/ai-preferences-form.component';
import { AiSuggestionCardsComponent } from '../components/ai-suggestion-cards/ai-suggestion-cards.component';
import { EditStopFormComponent } from '../components/edit-stop-form/edit-stop-form.component';

// SCRUM-244b: consecutive stops sharing a dayNumber, in existing stopOrder — dayNumber
// is monotonically non-decreasing along stopOrder (ItineraryScheduler never assigns an
// earlier day to a later stop), so grouping consecutive equal values is equivalent to
// grouping by day. A trip with no schedule yet renders as a single dayNumber: null group.
interface DayGroup {
  dayNumber: number | null;
  stops: StopResponse[];
}

@Component({
  selector: 'app-trip-view',
  templateUrl: 'trip-view.page.html',
  styleUrls: ['trip-view.page.scss'],
  imports: [
    CommonModule,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonButton,
    IonButtons,
    IonBackButton,
    IonSpinner,
    IonIcon,
    IonList,
    IonItem,
    IonItemDivider,
    IonLabel,
    TripMapComponent,
    IonModal,
    AiPreferencesFormComponent,
    AiSuggestionCardsComponent,
    EditStopFormComponent,
  ],
})
export class TripViewPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private tripService = inject(TripService);
  private toastCtrl = inject(ToastController);

  trip: TripResponse | null = null;
  loading = true;
  error: string | null = null;
  optimizing = false;
  exporting = false;
    // SCRUM-67 wiring: hosts the SCRUM-155 preferences form, then swaps to the
  // SCRUM-156 suggestion cards once a response comes back.
  aiModalOpen = false;
  aiSuggestions: SuggestedItineraryResponse | null = null;

  // SCRUM-250
  editingStop: StopResponse | null = null;

  private tripId = 0;

  constructor() {
    addIcons({ create, calendarOutline, sparkles});
  }

  ngOnInit(): void {
    this.tripId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadTrip();
  }

  loadTrip(): void {
    this.loading = true;
    this.error = null;
    this.tripService.getTrip(this.tripId).subscribe({
      next: (trip) => {
        this.trip = trip;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.message;
        this.loading = false;
      },
    });
  }
  
  justOptimized = false;

  get sortedStops() {
    return this.trip?.stops
      ? [...this.trip.stops].sort((a, b) => a.stopOrder - b.stopOrder)
      : [];
  }

  get dayGroups(): DayGroup[] {
    if (!this.trip) return [];

    const groups: DayGroup[] = [];
    for (const stop of this.trip.stops) {
      const lastGroup = groups[groups.length - 1];
      if (lastGroup && lastGroup.dayNumber === stop.dayNumber) {
        lastGroup.stops.push(stop);
      } else {
        groups.push({ dayNumber: stop.dayNumber, stops: [stop] });
      }
    }
    return groups;
  }

  // Manual parsing rather than `new Date(plannedTime)` — a bare "HH:mm:ss" string with
  // no date component is not reliably parsed as a time-of-day across browsers and can
  // silently yield "Invalid Date".
  formatPlannedTime(plannedTime: string): string {
    const [hourStr, minuteStr] = plannedTime.split(':');
    const hour = Number(hourStr);
    const minute = Number(minuteStr);
    const period = hour >= 12 ? 'PM' : 'AM';
    const displayHour = hour % 12 === 0 ? 12 : hour % 12;
    return `${displayHour}:${minute.toString().padStart(2, '0')} ${period}`;
  }

  editTrip(): void {
    if (this.trip) {
      this.router.navigate(['/trips', this.trip.id, 'edit']);
    }
  }
  openAiSuggest(): void {
    this.aiSuggestions = null;
    this.aiModalOpen = true;
  }

  closeAiModal(): void {
    this.aiModalOpen = false;
    this.aiSuggestions = null;
  }

  onSuggested(response: SuggestedItineraryResponse): void {
    this.aiSuggestions = response;
  }

  // Appends locally rather than refetching the whole trip so the modal doesn't
  // flash the page-level loading spinner behind it on every accepted card.
  onStopAdded(stop: StopResponse): void {
    if (this.trip) {
      this.trip = { ...this.trip, stops: [...this.trip.stops, stop] };
    }
  }

  openEditStop(stop: StopResponse): void {
    this.editingStop = stop;
  }

  closeEditStop(): void {
    this.editingStop = null;
  }

  // Patches the edited stop in place rather than refetching the whole trip so
  // the modal doesn't flash the page-level loading spinner behind it.
  onStopUpdated(updated: StopResponse): void {
    if (this.trip) {
      this.trip = {
        ...this.trip,
        stops: this.trip.stops.map((s) => (s.id === updated.id ? updated : s)),
      };
    }
    this.editingStop = null;
  }

  onOptimizeRequested(): void {
    if (!this.trip || this.optimizing) return;
    this.optimizing = true;

    this.tripService.optimizeTrip(this.trip.id).subscribe({
      next: async (updated) => {
        this.trip = updated;
        this.optimizing = false;
        this.justOptimized = true;
        setTimeout(() => (this.justOptimized = false), 1200);

        const toast = await this.toastCtrl.create({
          message: 'Route optimized.',
          duration: 2000,
          color: 'success',
        });
        await toast.present();
      },
      error: async (err) => {
        this.optimizing = false;
        const toast = await this.toastCtrl.create({
          message: err.message ?? 'Could not optimize route.',
          duration: 2500,
          color: 'danger',
        });
        await toast.present();
      },
    });
  }

  exportToCalendar(): void {
    if (!this.trip || this.exporting) return;
    this.exporting = true;
    const filename = `${sanitizeFilename(this.trip.title)}.ics`;

    this.tripService.exportIcs(this.trip.id).subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, filename);
      },
      error: async (err) => {
        this.exporting = false;
        const toast = await this.toastCtrl.create({
          message: err.message ?? 'Could not export calendar.',
          duration: 2500,
          color: 'danger',
        });
        await toast.present();
      },
    });
  }
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  window.URL.revokeObjectURL(url);
}

// Mirrors the backend's TripExportController#sanitizeFilename — the server-set
// Content-Disposition filename never reaches us here (Blob downloads lose response
// headers), so the client picks its own name for the `download` attribute.
function sanitizeFilename(title: string): string {
  const sanitized = title.replace(/[^a-zA-Z0-9 -]/g, '').trim();
  return sanitized || 'trip';
}
