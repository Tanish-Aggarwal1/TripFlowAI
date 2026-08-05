import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonItem,
  IonLabel,
  IonTextarea,
  IonButton,
  IonIcon,
  IonSpinner,
  IonProgressBar,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { camera, checkmark, close } from 'ionicons/icons';
import { StopPhotoService } from '../../../../core/services/stop-photo.service';
import { StopPhotoResponse } from '../../../../core/models/trip.model';

// SCRUM-164: picker → signature → direct-to-Cloudinary → persist. File bytes
// never hit our backend (see StopPhotoService.uploadPhoto). Gallery/thumbnail
// display is SCRUM-165 — this component only handles the upload step and
// hands the created photo back to the parent on success.
@Component({
  selector: 'app-stop-photo-upload',
  templateUrl: 'stop-photo-upload.component.html',
  styleUrls: ['stop-photo-upload.component.scss'],
  imports: [
    FormsModule,
    IonItem,
    IonLabel,
    IonTextarea,
    IonButton,
    IonIcon,
    IonSpinner,
    IonProgressBar,
  ],
})
export class StopPhotoUploadComponent {
  @Input({ required: true }) stopId!: number;
  @Output() uploaded = new EventEmitter<StopPhotoResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private stopPhotoService = inject(StopPhotoService);
  private toastCtrl = inject(ToastController);

  selectedFile: File | null = null;
  previewUrl: string | null = null;
  caption = '';

  uploading = false;
  progress = 0;
  formError = '';

  constructor() {
    addIcons({ camera, checkmark, close });
  }

  onFileSelected(event: Event): void {
    this.formError = '';
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    if (!file) {
      this.selectedFile = null;
      this.previewUrl = null;
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.formError = 'Please choose an image file.';
      input.value = '';
      return;
    }

    this.selectedFile = file;
    this.previewUrl = URL.createObjectURL(file);
  }

  submit(): void {
    this.formError = '';

    if (!this.selectedFile) {
      this.formError = 'Choose a photo first.';
      return;
    }
    if (this.uploading) return;

    this.uploading = true;
    this.progress = 0;

    this.stopPhotoService
      .uploadPhoto(this.stopId, this.selectedFile, this.caption.trim() || undefined)
      .subscribe({
        next: (event) => {
          if ('progress' in event) {
            this.progress = event.progress;
          } else {
            this.uploading = false;
            this.uploaded.emit(event.done);
          }
        },
        error: async (err) => {
          this.uploading = false;
          this.progress = 0;
          const toast = await this.toastCtrl.create({
            message: err.message ?? 'Could not upload photo.',
            duration: 3000,
            color: 'danger',
          });
          await toast.present();
        },
      });
  }

  cancel(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
    }
    this.cancelled.emit();
  }
}