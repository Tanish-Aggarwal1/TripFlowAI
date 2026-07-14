import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonHeader, IonToolbar, IonTitle, IonContent, IonButton,
  IonItem, IonLabel, IonInput, IonTextarea, IonSelect,
  IonSelectOption, IonSpinner, IonBackButton, IonButtons,
  IonIcon, AlertController, ToastController
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { save, arrowBack } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import {
  TripResponse, CreateTripRequest, UpdateTripRequest,
  CreateStopRequest, TripVisibility
} from '../../../core/models/trip.model';
import { StopListComponent } from '../components/stop-list/stop-list.component';
@Component({
  selector: 'app-trip-edit',
  templateUrl: 'trip-edit.page.html',
  styleUrls: ['trip-edit.page.scss'],
  imports: [
    CommonModule, FormsModule,
    IonHeader, IonToolbar, IonTitle, IonContent, IonButton,
    IonItem, IonLabel, IonInput, IonTextarea, IonSelect,
    IonSelectOption, IonSpinner, IonButtons, IonIcon,StopListComponent,
  ],
})
export class TripEditPage implements OnInit {
  // ── Mode ──────────────────────────────────────────────────────────────────
  isEditMode = false;
  tripId: number | null = null;
  loading = false;
  saving = false;
  error: string | null = null;

  // ── Form fields ───────────────────────────────────────────────────────────
  title = '';
  description = '';
  tagsInput = '';           // comma-separated string → string[] on save
  visibility: TripVisibility = 'PRIVATE';

  // ── Stops (managed by stop-list child — passed via binding) ───────────────
  stops: CreateStopRequest[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private tripService: TripService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController,
  ) {
    addIcons({ save, arrowBack });
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
        this.title       = trip.title;
        this.description = trip.description ?? '';
        this.tagsInput   = (trip.tags ?? []).join(', ');
        this.visibility  = trip.visibility;
        this.stops       = trip.stops.map(s => ({
          name:            s.name,
          latitude:        s.latitude,
          longitude:       s.longitude,
          address:         s.address ?? undefined,
          externalPlaceId: undefined,
          notes:           s.notes ?? undefined,
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
      this.showToast('Title is required.', 'danger');
      return;
    }
    if (this.stops.length === 0) {
      this.showToast('Add at least one stop.', 'danger');
      return;
    }

    const tags = this.tagsInput
      .split(',')
      .map(t => t.trim())
      .filter(t => t.length > 0);

    this.saving = true;

    if (this.isEditMode && this.tripId !== null) {
      const request: UpdateTripRequest = {
        title:       this.title.trim(),
        description: this.description.trim() || undefined,
        tags,
        visibility:  this.visibility,
        stops:       this.stops,
      };
      this.tripService.updateTrip(this.tripId, request).subscribe({
        next: () => {
          this.saving = false;
          this.showToast('Trip updated!', 'success');
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.saving = false;
          this.error = err.message;
        },
      });
    } else {
      const request: CreateTripRequest = {
        title:       this.title.trim(),
        description: this.description.trim() || undefined,
        tags,
        visibility:  this.visibility,
        stops:       this.stops,
      };
      this.tripService.createTrip(request).subscribe({
        next: () => {
          this.saving = false;
          this.showToast('Trip created!', 'success');
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.saving = false;
          this.error = err.message;
        },
      });
    }
  }

  // ── Unsaved changes guard ─────────────────────────────────────────────────
  async confirmBack(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Discard changes?',
      message: 'You have unsaved changes. Leave anyway?',
      buttons: [
        { text: 'Stay', role: 'cancel' },
        { text: 'Discard', role: 'destructive',
          handler: () => this.router.navigate(['/dashboard']) },
      ],
    });
    await alert.present();
  }

  // ── Stop list callbacks (called from stop-list component) ─────────────────
onStopsChanged(stops: CreateStopRequest[]): void {
  this.stops = stops;
}

  // ── Helpers ───────────────────────────────────────────────────────────────
  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, color, duration: 2000 });
    await toast.present();
  }
}