import { ChangeDetectorRef, Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IonButton, IonIcon, IonSpinner, AlertController } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { add, trash } from 'ionicons/icons';
import { StopPhotoService } from '../../../../core/services/stop-photo.service';
import { StopPhotoResponse } from '../../../../core/models/trip.model';
import { StopPhotoUploadComponent } from '../stop-photo-upload/stop-photo-upload.component';
import { ToastService } from '../../../../core/services/toast.service';

// SCRUM-165: displays a stop's photo gallery (thumbnail + caption/review),
// supports delete, and hosts StopPhotoUploadComponent (SCRUM-164) inline so
// a successful upload refreshes this same list in place — satisfies 164's
// AC "success closes the upload UI and refreshes the stop's photo list."
@Component({
  selector: 'app-stop-photo-gallery',
  templateUrl: 'stop-photo-gallery.component.html',
  styleUrls: ['stop-photo-gallery.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IonButton, IonIcon, IonSpinner, StopPhotoUploadComponent],
})
export class StopPhotoGalleryComponent implements OnInit {
  @Input({ required: true }) stopId!: number;
  // Not yet bound by any host — the right hook for trip-view to learn about photo
  // count changes once it stops rendering its own separate upload UI (SCRUM-432).
  @Output() photosChanged = new EventEmitter<StopPhotoResponse[]>();

  private stopPhotoService = inject(StopPhotoService);
  private alertCtrl = inject(AlertController);
  private toastService = inject(ToastService);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);

  photos: StopPhotoResponse[] = [];
  loading = true;
  error = '';

  showUpload = false;
  deletingId: number | null = null;

  constructor() {
    addIcons({ add, trash });
  }

  ngOnInit(): void {
    this.loadPhotos();
  }

  loadPhotos(): void {
    this.loading = true;
    this.error = '';
    this.stopPhotoService
      .listPhotos(this.stopId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (photos) => {
        this.photos = photos;
        this.loading = false;
        this.photosChanged.emit(this.photos);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err.message ?? 'Could not load photos.';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  openUpload(): void {
    this.showUpload = true;
  }

  onUploaded(photo: StopPhotoResponse): void {
    this.showUpload = false;
    this.photos = [photo, ...this.photos];
    this.photosChanged.emit(this.photos);
  }

  onUploadCancelled(): void {
    this.showUpload = false;
  }

  async confirmDelete(photo: StopPhotoResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Delete Photo',
      message: 'Are you sure you want to delete this photo?',
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Delete',
          role: 'destructive',
          handler: () => this.deletePhoto(photo),
        },
      ],
    });
    await alert.present();
  }

  private deletePhoto(photo: StopPhotoResponse): void {
    if (this.deletingId === photo.id) return;
    this.deletingId = photo.id;

    this.stopPhotoService
      .deletePhoto(this.stopId, photo.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: () => {
        this.deletingId = null;
        this.photos = this.photos.filter((p) => p.id !== photo.id);
        this.photosChanged.emit(this.photos);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.deletingId = null;
        this.toastService.showError(err, 'Could not delete photo.');
        this.cdr.markForCheck();
      },
    });
  }
}