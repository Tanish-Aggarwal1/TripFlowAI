import { ChangeDetectorRef, Component, ChangeDetectionStrategy, DestroyRef, inject, OnDestroy, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
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
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  create,
  calendarOutline,
  sparkles,
  checkmark,
  checkmarkCircle,
  camera,
  documentTextOutline,
} from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  StopResponse,
  TripResponse,
  SuggestedItineraryResponse,
  UpdateStopRequest,
  StopPhotoResponse,
} from '../../../core/models/trip.model';
import { TripMapComponent } from '../components/trip-map/trip-map.component';
import { IonModal } from '@ionic/angular';
import { AiPreferencesFormComponent } from '../components/ai-preferences-form/ai-preferences-form.component';
import { AiSuggestionCardsComponent } from '../components/ai-suggestion-cards/ai-suggestion-cards.component';
import { EditStopFormComponent } from '../components/edit-stop-form/edit-stop-form.component';
import { StopPhotoUploadComponent } from '../components/stop-photo-upload/stop-photo-upload.component';
import { StopPhotoGalleryComponent } from '../components/stop-photo-gallery/stop-photo-gallery.component';

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
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
    StopPhotoUploadComponent,
    StopPhotoGalleryComponent,
  ],
})
export class TripViewPage implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private tripService = inject(TripService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);
  private cdr = inject(ChangeDetectorRef);

  trip: TripResponse | null = null;
  loading = true;
  error: string | null = null;
  optimizing = false;
  exporting = false;
  exportingPdf = false;
  // SCRUM-67 wiring: hosts the SCRUM-155 preferences form, then swaps to the
  // SCRUM-156 suggestion cards once a response comes back.
  aiModalOpen = false;
  aiSuggestions: SuggestedItineraryResponse | null = null;

  // SCRUM-250
  editingStop: StopResponse | null = null;

  // SCRUM-164
  uploadingPhotoStop: StopResponse | null = null;

  // Quick "Visited" action — tracks the in-flight stop id so a fast double-tap
  // can't fire a second PUT before the first one resolves.
  markingVisitedId: number | null = null;

  private tripId = 0;
  private justOptimizedTimeoutId: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    addIcons({
      create,
      calendarOutline,
      sparkles,
      checkmark,
      'checkmark-circle': checkmarkCircle,
      camera,
      'document-text-outline': documentTextOutline,
    });
  }

  ngOnInit(): void {
    // SCRUM-485: subscribe rather than read route.snapshot once — Angular reuses this
    // component instance across navigations matched by the same route config (e.g.
    // trip A's page to trip B's page), so a snapshot read in ngOnInit never re-fires
    // and keeps showing trip A's data under trip B's URL.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.tripId = Number(params.get('id'));
      this.loadTrip();
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.justOptimizedTimeoutId);
  }

  loadTrip(): void {
    this.loading = true;
    this.error = null;
    this.tripService
      .getTrip(this.tripId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (trip) => {
        this.trip = trip;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err.message;
        this.loading = false;
        this.cdr.markForCheck();
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

    // Must consume sortedStops, not this.trip.stops — the consecutive-grouping below
    // relies on dayNumber being monotonically non-decreasing along stopOrder, which is
    // only true of sorted input.
    const groups: DayGroup[] = [];
    for (const stop of this.sortedStops) {
      const lastGroup = groups[groups.length - 1];
      if (lastGroup && lastGroup.dayNumber === stop.dayNumber) {
        lastGroup.stops.push(stop);
      } else {
        groups.push({ dayNumber: stop.dayNumber, stops: [stop] });
      }
    }
    return groups;
  }

  // First not-yet-visited stop in order — the one the "Next" button highlights.
  // SKIPPED stops aren't "next" either; only PLANNED counts as still upcoming.
  get nextStopId(): number | null {
    const next = this.sortedStops.find((s) => s.status === 'PLANNED');
    return next ? next.id : null;
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

  // One-tap "Visited" quick action on the stop row — toggles VISITED <-> PLANNED.
  // The PUT endpoint requires name/latitude/longitude on every request and
  // overwrites notes/address unconditionally (only `status` has real
  // omit-to-leave-unchanged semantics), so every other field must be echoed
  // back unchanged — same as EditStopFormComponent.
  toggleVisited(stop: StopResponse): void {
    if (!this.trip || this.markingVisitedId === stop.id) return;
    this.markingVisitedId = stop.id;

    const request: UpdateStopRequest = {
      name: stop.name,
      latitude: stop.latitude,
      longitude: stop.longitude,
      address: stop.address ?? undefined,
      notes: stop.notes ?? undefined,
      status: stop.status === 'VISITED' ? 'PLANNED' : 'VISITED',
    };

    this.tripService
      .updateStop(this.trip.id, stop.id, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (updated) => {
        this.markingVisitedId = null;
        this.onStopUpdated(updated);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.markingVisitedId = null;
        this.toastService.showError(err, 'Could not update stop status.');
        this.cdr.markForCheck();
      },
    });
  }
  // SCRUM-164
  openPhotoUpload(stop: StopResponse): void {
    this.uploadingPhotoStop = stop;
  }

  // SCRUM-164
  closePhotoUpload(): void {
    this.uploadingPhotoStop = null;
  }

  // SCRUM-164 — gallery refresh/thumbnail rendering deferred to SCRUM-165.
  async onPhotoUploaded(_photo: StopPhotoResponse): Promise<void> {
    this.uploadingPhotoStop = null;
    await this.toastService.showSuccess('Photo uploaded.');
  }

  onOptimizeRequested(): void {
    if (!this.trip || this.optimizing) return;
    this.optimizing = true;

    this.tripService
      .optimizeTrip(this.trip.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: async (updated) => {
        this.trip = updated;
        this.optimizing = false;
        this.justOptimized = true;
        clearTimeout(this.justOptimizedTimeoutId);
        this.justOptimizedTimeoutId = setTimeout(() => {
          this.justOptimized = false;
          this.cdr.markForCheck();
        }, 1200);
        this.cdr.markForCheck();

        await this.toastService.showSuccess('Route optimized.');
      },
      error: (err) => {
        this.optimizing = false;
        this.toastService.showError(err, 'Could not optimize route.');
        this.cdr.markForCheck();
      },
    });
  }

  exportToCalendar(): void {
    if (!this.trip || this.exporting) return;
    this.exporting = true;
    const filename = `${sanitizeFilename(this.trip.title)}.ics`;

    this.tripService
      .exportIcs(this.trip.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (blob) => {
        this.exporting = false;
        downloadBlob(blob, filename);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.exporting = false;
        this.toastService.showError(err, 'Could not export calendar.');
        this.cdr.markForCheck();
      },
    });
  }

  exportToPdf(): void {
    if (!this.trip || this.exportingPdf) return;
    this.exportingPdf = true;
    const filename = `${sanitizeFilename(this.trip.title)}.pdf`;

    this.tripService
      .exportPdf(this.trip.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (blob) => {
        this.exportingPdf = false;
        downloadBlob(blob, filename);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.exportingPdf = false;
        this.toastService.showError(err, 'Could not export PDF.');
        this.cdr.markForCheck();
      },
    });
  }
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  // Firefox requires the anchor to be in the document for a programmatic click() to
  // trigger a download — a detached element is silently ignored.
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  // click() initiates the download asynchronously; revoking on the same tick can invalidate
  // the URL before the browser has read the blob (timing-dependent, unreliable on Safari).
  setTimeout(() => window.URL.revokeObjectURL(url), 0);
}

// Matched pair with the backend's TripExportController#sanitizeFilename — the
// server-set Content-Disposition filename never reaches us here (Blob downloads
// lose response headers), so the client computes its own name for the `download`
// attribute. Keep the character-set and length-cap rules identical on both sides;
// a shared fixture set is asserted against in trip-view.page.spec.ts (frontend)
// and TripExportControllerTest (backend) to catch future drift. Exported so the
// spec can import it directly.
export function sanitizeFilename(title: string): string {
  const sanitized = title.replace(/[^a-zA-Z0-9 -]/g, '').trim();
  const withFallback = sanitized || 'trip';
  return withFallback.length > 100 ? withFallback.slice(0, 100) : withFallback;
}
