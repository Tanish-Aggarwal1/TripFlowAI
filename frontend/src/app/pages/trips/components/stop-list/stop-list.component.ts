import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DragDropModule, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import {
  IonList, IonItem, IonLabel, IonButton, IonIcon,
  IonInput, IonReorderGroup, IonReorder, ItemReorderEventDetail
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, trash, reorderTwo } from 'ionicons/icons';
import { CreateStopRequest } from '../../../../core/models/trip.model';

@Component({
  selector: 'app-stop-list',
  templateUrl: 'stop-list.component.html',
  styleUrls: ['stop-list.component.scss'],
  imports: [
    CommonModule, FormsModule, DragDropModule,
     IonItem, IonLabel, IonButton, IonIcon,
    IonInput, IonReorderGroup, IonReorder,
  ],
})
export class StopListComponent {
  // ── Inputs / Outputs ──────────────────────────────────────────────────────
  @Input() stops: CreateStopRequest[] = [];
  @Output() stopsChanged = new EventEmitter<CreateStopRequest[]>();

  // ── New stop form fields ──────────────────────────────────────────────────
  newName    = '';
  newLat     = '';
  newLng     = '';
  newAddress = '';
  newNotes   = '';
  formError  = '';

  constructor() {
    addIcons({ add, trash, reorderTwo });
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

    const stop: CreateStopRequest = {
      name:    this.newName.trim(),
      latitude:  lat,
      longitude: lng,
      address: this.newAddress.trim() || undefined,
      notes:   this.newNotes.trim()   || undefined,
    };

    this.stops = [...this.stops, stop];
    this.stopsChanged.emit(this.stops);
    this.resetForm();
  }

  // ── Remove a stop ─────────────────────────────────────────────────────────
  removeStop(index: number): void {
    this.stops = this.stops.filter((_, i) => i !== index);
    this.stopsChanged.emit(this.stops);
  }

  // ── Reorder via Ionic reorder group ──────────────────────────────────────
handleReorder(event: CustomEvent<ItemReorderEventDetail>): void {
    const reordered = [...this.stops];
    const item = reordered.splice(event.detail.from, 1)[0];
    reordered.splice(event.detail.to, 0, item);
    this.stops = reordered;
    this.stopsChanged.emit(this.stops);
    event.detail.complete();
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