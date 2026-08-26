import { ChangeDetectorRef, Component, Input, Output, EventEmitter, ChangeDetectionStrategy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonItem, IonLabel, IonButton, IonIcon,
  IonInput, IonReorderGroup, IonReorder, ItemReorderEventDetail
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { add, trash } from 'ionicons/icons';
import { UpsertStopRequest } from '../../../../core/models/trip.model';

@Component({
  selector: 'app-stop-list',
  templateUrl: 'stop-list.component.html',
  styleUrls: ['stop-list.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
     IonItem, IonLabel, IonButton, IonIcon,
    IonInput, IonReorderGroup, IonReorder,
  ],
})
export class StopListComponent {
  // ── Inputs / Outputs ──────────────────────────────────────────────────────
  // Carries UpsertStopRequest, not CreateStopRequest, so an existing stop's id survives
  // add/remove/reorder. Dropping an id here would delete that stop on save.
  @Input() stops: UpsertStopRequest[] = [];
  @Output() stopsChanged = new EventEmitter<UpsertStopRequest[]>();

  // ── New stop form fields ──────────────────────────────────────────────────
  newName    = '';
  newLat     = '';
  newLng     = '';
  newAddress = '';
  newNotes   = '';
  formError  = '';

  private cdr = inject(ChangeDetectorRef);

  constructor() {
    addIcons({ add, trash });
  }

  // ── Add a stop ────────────────────────────────────────────────────────────
  addStop(): void {
    this.formError = '';

    if (!this.newName.trim()) {
      this.formError = 'Stop name is required.';
      return;
    }
    const lat = parseFloat(this.newLat);
    const lng = parseFloat(this.newLng);
    if (isNaN(lat) || isNaN(lng)) {
      this.formError = 'Valid latitude and longitude are required.';
      return;
    }
    if (lat < -90 || lat > 90) {
      this.formError = 'Latitude must be between -90 and 90.';
      return;
    }
    if (lng < -180 || lng > 180) {
      this.formError = 'Longitude must be between -180 and 180.';
      return;
    }

    // id null = insert. This is the only place a brand-new stop is built; existing stops
    // come from trip-edit's loadTrip with their real id, and the two never share a path.
    const stop: UpsertStopRequest = {
      id:      null,
      name:    this.newName.trim(),
      latitude:  lat,
      longitude: lng,
      address: this.newAddress.trim() || undefined,
      notes:   this.newNotes.trim()   || undefined,
    };

    this.stops = [...this.stops, stop];
    this.stopsChanged.emit(this.stops);
    this.resetForm();
    this.cdr.markForCheck();
  }

  // ── Remove a stop ─────────────────────────────────────────────────────────
  removeStop(index: number): void {
    this.stops = this.stops.filter((_, i) => i !== index);
    this.stopsChanged.emit(this.stops);
    this.cdr.markForCheck();
  }

  // ── Reorder via Ionic reorder group ──────────────────────────────────────
handleReorder(event: CustomEvent<ItemReorderEventDetail>): void {
    const reordered = [...this.stops];
    const item = reordered.splice(event.detail.from, 1)[0];
    reordered.splice(event.detail.to, 0, item);
    this.stops = reordered;
    this.stopsChanged.emit(this.stops);
    event.detail.complete();
    this.cdr.markForCheck();
  }

  // ── Reset add form ────────────────────────────────────────────────────────
  private resetForm(): void {
    this.newName    = '';
    this.newLat     = '';
    this.newLng     = '';
    this.newAddress = '';
    this.newNotes   = '';
    this.formError  = '';
  }
}