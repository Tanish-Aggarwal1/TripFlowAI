import { Component, EventEmitter, inject, Input, OnInit, Output, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonItem,
  IonInput,
  IonTextarea,
  IonSelect,
  IonSelectOption,
  IonButton,
  IonIcon,
  IonSpinner,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { checkmark } from 'ionicons/icons';
import { TripService } from '../../../../core/services/trip.service';
import { StopResponse, UpdateStopRequest } from '../../../../core/models/trip.model';
import { ToastService } from '../../../../core/services/toast.service';

// SCRUM-250: edits name/address/notes/status on an existing stop via the
// already-live PUT /api/trips/{tripId}/stops/{stopId} endpoint. Coordinates and
// externalPlaceId are carried through unchanged — no map-picker UI exists yet.
@Component({
  selector: 'app-edit-stop-form',
  templateUrl: 'edit-stop-form.component.html',
  styleUrls: ['edit-stop-form.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    FormsModule,
    IonItem,
    IonInput,
    IonTextarea,
    IonSelect,
    IonSelectOption,
    IonButton,
    IonIcon,
    IonSpinner,
  ],
})
export class EditStopFormComponent implements OnInit {
  @Input({ required: true }) tripId!: number;
  @Input({ required: true }) stop!: StopResponse;
  @Output() updated = new EventEmitter<StopResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private tripService = inject(TripService);
  private toastService = inject(ToastService);

  name = '';
  address = '';
  notes = '';
  status: StopResponse['status'] = 'PLANNED';

  submitting = false;
  formError = '';

  constructor() {
    addIcons({ checkmark });
  }

  ngOnInit(): void {
    this.name = this.stop.name;
    this.address = this.stop.address ?? '';
    this.notes = this.stop.notes ?? '';
    this.status = this.stop.status;
  }

  submit(): void {
    this.formError = '';

    const name = this.name.trim();
    if (!name) {
      this.formError = 'Name is required.';
      return;
    }
    if (this.submitting) return;

    const request: UpdateStopRequest = {
      name,
      latitude: this.stop.latitude,
      longitude: this.stop.longitude,
      address: this.address.trim() || undefined,
      notes: this.notes.trim() || undefined,
      status: this.status,
    };

    this.submitting = true;
    this.tripService.updateStop(this.tripId, this.stop.id, request).subscribe({
      next: (updated) => {
        this.submitting = false;
        this.updated.emit(updated);
      },
      error: (err) => {
        this.submitting = false;
        this.toastService.showError(err, 'Could not update stop.', 3000);
      },
    });
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
