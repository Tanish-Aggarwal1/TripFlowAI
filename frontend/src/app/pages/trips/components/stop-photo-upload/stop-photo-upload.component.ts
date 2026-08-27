import { ChangeDetectorRef, Component, EventEmitter, inject, Input, Output, ChangeDetectionStrategy, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonItem,
  IonLabel,
  IonTextarea,
  IonButton,
  IonIcon,
  IonSpinner,
  IonProgressBar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { camera, checkmark, close } from 'ionicons/icons';
import { StopPhotoService } from '../../../../core/services/stop-photo.service';
import { StopPhotoResponse } from '../../../../core/models/trip.model';
import { ToastService } from '../../../../core/services/toast.service';

// Mirrors StopPhotoService.MAX_UPLOAD_BYTES (backend) / the Cloudinary max_file_size
// signed for this upload — reject oversized files before the network round trip.
const MAX_UPLOAD_BYTES = 10_000_000;

// SCRUM-164: picker → signature → direct-to-Cloudinary → persist. File bytes
// never hit our backend (see StopPhotoService.uploadPhoto). Gallery/thumbnail
// display is SCRUM-165 — this component only handles the upload step and
// hands the created photo back to the parent on success.
@Component({
  selector: 'app-stop-photo-upload',
  templateUrl: 'stop-photo-upload.component.html',
  styleUrls: ['stop-photo-upload.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
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
export class StopPhotoUploadComponent implements OnDestroy {
  @Input({ required: true }) stopId!: number;
  @Output() uploaded = new EventEmitter<StopPhotoResponse>();
  @Output() cancelled = new EventEmitter<void>();

  private stopPhotoService = inject(StopPhotoService);
  private toastService = inject(ToastService);
  private cdr = inject(ChangeDetectorRef);

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
    this.revokePreview();
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    if (!file) {
      this.selectedFile = null;
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.formError = 'Please choose an image file.';
      input.value = '';
      this.selectedFile = null;
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      this.formError = 'Photo must be 10MB or smaller.';
      input.value = '';
      this.selectedFile = null;
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
            this.revokePreview();
            this.uploaded.emit(event.done);
          }
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.uploading = false;
          this.progress = 0;
          this.toastService.showError(err, 'Could not upload photo.', 3000);
          this.cdr.markForCheck();
        },
      });
  }

  cancel(): void {
    this.revokePreview();
    this.cancelled.emit();
  }

  ngOnDestroy(): void {
    this.revokePreview();
  }

  private revokePreview(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
      this.previewUrl = null;
    }
  }
}