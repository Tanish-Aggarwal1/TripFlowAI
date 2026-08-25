import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import {
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonButton,
  IonItem,
  IonLabel,
  IonInput,
  IonTextarea,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonButtons,
  IonIcon,
  AlertController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { save, arrowBack, map as mapIcon } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  TripResponse,
  CreateTripRequest,
  UpdateTripRequest,
  UpsertStopRequest,
  TripVisibility,
  MAX_STOPS,
} from '../../../core/models/trip.model';
import { StopListComponent } from '../components/stop-list/stop-list.component';

@Component({
  selector: 'app-trip-edit',
  templateUrl: 'trip-edit.page.html',
  styleUrls: ['trip-edit.page.scss'],
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    FormsModule,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonButton,
    IonItem,
    IonLabel,
    IonInput,
    IonTextarea,
    IonSelect,
    IonSelectOption,
    IonSpinner,
    IonButtons,
    IonIcon,
    StopListComponent,
  ],
})
export class TripEditPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private tripService = inject(TripService);
  private alertCtrl = inject(AlertController);
  private toastService = inject(ToastService);

  // ── Mode ──────────────────────────────────────────────────────────────────
  isEditMode = false;
  tripId: number | null = null;
  loading = false;
  saving = false;
  error: string | null = null;

  // ── Form fields ───────────────────────────────────────────────────────────
  title = '';
  description = '';
  tagsInput = ''; // comma-separated string → string[] on save
  visibility: TripVisibility = 'PRIVATE';
  // No UI edits this yet; it is loaded and echoed back so an edit can never clear it,
  // independently of the backend treating an absent startDate as "leave unchanged".
  startDate: string | undefined;

  // ── Stops (managed by stop-list child — passed via binding) ───────────────
  stops: UpsertStopRequest[] = [];

  constructor() {
    addIcons({ save, arrowBack, map: mapIcon });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.tripId = Number(id);
      this.loadTrip(this.tripId);
    }
  }

  // ── Load existing trip (edit mode) ────────────────────────────────────────
  private loadTrip(id: number): void {
    this.loading = true;
    this.tripService.getTrip(id).subscribe({
      next: (trip: TripResponse) => {
        this.title = trip.title;
        this.description = trip.description ?? '';
        this.tagsInput = (trip.tags ?? []).join(', ');
        this.visibility = trip.visibility;
        this.startDate = trip.startDate ?? undefined;
        // s.id is the load-bearing field: it merges this stop in place on save, preserving
        // its status/dayNumber/plannedTime/stopType and photos. Drop it and save() deletes
        // the stop and re-inserts a bare copy. StopResponse carries no externalPlaceId, so
        // that one genuinely cannot be round-tripped — the backend re-resolves by lat/lng.
        this.stops = trip.stops.map((s) => ({
          id: s.id,
          name: s.name,
          latitude: s.latitude,
          longitude: s.longitude,
          address: s.address ?? undefined,
          notes: s.notes ?? undefined,
        }));
        this.loading = false;
      },
      error: (err) => {
        this.error = err.message;
        this.loading = false;
      },
    });
  }

  // ── Save ──────────────────────────────────────────────────────────────────
  async save(): Promise<void> {
    if (!this.title.trim()) {
      this.toastService.showError(undefined, 'Title is required.');
      return;
    }
    if (this.stops.length === 0) {
      this.toastService.showError(undefined, 'Add at least one stop.');
      return;
    }
    // Backend @Size(max = MAX_STOPS) would reject this as a bare 400.
    if (this.stops.length > MAX_STOPS) {
      this.toastService.showError(undefined, `A trip can have at most ${MAX_STOPS} stops.`);
      return;
    }

    const tags = this.tagsInput
      .split(',')
      .map((t) => t.trim())
      .filter((t) => t.length > 0);

    this.saving = true;

    if (this.isEditMode && this.tripId !== null) {
      const request: UpdateTripRequest = {
        title: this.title.trim(),
        description: this.description.trim() || undefined,
        tags,
        visibility: this.visibility,
        stops: this.stops,
        startDate: this.startDate,
      };
      this.tripService.updateTrip(this.tripId, request).subscribe({
        next: () => {
          this.saving = false;
          this.toastService.showSuccess('Trip updated!');
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.saving = false;
          this.error = err.message;
        },
      });
    } else {
      const request: CreateTripRequest = {
        title: this.title.trim(),
        description: this.description.trim() || undefined,
        tags,
        visibility: this.visibility,
        // Create takes CreateStopRequest, which has no id by design — every stop here is
        // new, so strip the always-null id rather than sending a meaningless field.
        stops: this.stops.map(({ id: _id, ...stop }) => stop),
      };
      this.tripService.createTrip(request).subscribe({
        next: () => {
          this.saving = false;
          this.toastService.showSuccess('Trip created!');
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.saving = false;
          this.error = err.message;
        },
      });
    }
  }

  // ── Navigate to map view ────────────────────────────────────────────────
  viewOnMap(): void {
    if (this.tripId !== null) {
      this.router.navigate(['/trips', this.tripId]);
    }
  }

  // ── Unsaved changes guard ─────────────────────────────────────────────────
  async confirmBack(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Discard changes?',
      message: 'You have unsaved changes. Leave anyway?',
      buttons: [
        { text: 'Stay', role: 'cancel' },
        {
          text: 'Discard',
          role: 'destructive',
          handler: () => this.router.navigate(['/dashboard']),
        },
      ],
    });
    await alert.present();
  }

  // ── Stop list callbacks (called from stop-list component) ─────────────────
  onStopsChanged(stops: UpsertStopRequest[]): void {
    this.stops = stops;
  }
}
