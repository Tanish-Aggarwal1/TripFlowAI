import { Component, EventEmitter, inject, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonButton, IonIcon, IonSpinner, AlertController } from '@ionic/angular/standalone';
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
  imports: [CommonModule, IonButton, IonIcon, IonSpinner, StopPhotoUploadComponent],
})
export class StopPhotoGalleryComponent implements OnInit {
  @Input({ required: true }) stopId!: number;
  @Output() photosChanged = new EventEmitter<StopPhotoResponse[]>();

  private stopPhotoService = inject(StopPhotoService);
  private alertCtrl = inject(AlertController);
  private toastService = inject(ToastService);

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
    this.stopPhotoService.listPhotos(this.stopId).subscribe({
      next: (photos) => {
        this.photos = photos;
        this.loading = false;
        this.photosChanged.emit(this.photos);
      },
      error: (err) => {
        this.error = err.message ?? 'Could not load photos.';
        this.loading = false;
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

    this.stopPhotoService.deletePhoto(this.stopId, photo.id).subscribe({
      next: () => {
        this.deletingId = null;
        this.photos = this.photos.filter((p) => p.id !== photo.id);
        this.photosChanged.emit(this.photos);
      },
      error: (err) => {
        this.deletingId = null;
        this.toastService.showError(err, 'Could not delete photo.');
      },
    });
  }
}