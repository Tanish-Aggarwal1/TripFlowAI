import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
  IonList, IonItem, IonLabel, IonBadge, IonIcon,
  IonFab, IonFabButton, IonFabList, IonModal, IonSpinner, AlertController, ToastController,IonThumbnail,
  ViewWillEnter
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, lockClosed, globeOutline, trash, create, sparkles, imageOutline  } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { TripSummaryResponse, TripResponse } from '../../../core/models/trip.model';
import { AiTripPromptComponent } from '../components/ai-trip-prompt/ai-trip-prompt.component';


@Component({
  selector: 'app-dashboard',
  templateUrl: 'dashboard.page.html',
  styleUrls: ['dashboard.page.scss'],
  imports: [
    IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
    IonList, IonItem, IonLabel, IonBadge, IonIcon,
    IonFab, IonFabButton, IonFabList, IonModal, IonSpinner,
    AiTripPromptComponent,
    IonThumbnail,
  ],
})
export class DashboardPage implements ViewWillEnter {


    private tripService = inject(TripService);
    private router = inject(Router);
    private alertCtrl = inject(AlertController);
    private toastCtrl = inject(ToastController);

  trips: TripSummaryResponse[] = [];
  loading = true;
  error: string | null = null;

  // "Create with AI" modal (dashboard entry point for generating a whole new
  // trip from a free-text prompt), distinct from the SCRUM-67 ai-suggest
  // modal on trip-view, which only adds suggestions onto an existing trip.
  aiModalOpen = false;

  constructor() {
    addIcons({ add, lockClosed, globeOutline, trash, create, sparkles, 'image-outline': imageOutline });
  }

  ionViewWillEnter(): void {
    this.loadTrips();
  }

  loadTrips(): void {
    this.loading = true;
    this.error = null;
    this.tripService.listTrips().subscribe({
      next: (page) => {
        this.trips = page.content;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.message;
        this.loading = false;
      },
    });
  }

  openTrip(trip: TripSummaryResponse): void {
    this.router.navigate(['/trips', trip.id]);
  }

  editTrip(trip: TripSummaryResponse, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/trips', trip.id, 'edit']);
  }

  createTrip(): void {
    this.router.navigate(['/trips/new']);
  }

  openAiCreate(): void {
    this.aiModalOpen = true;
  }

  closeAiModal(): void {
    this.aiModalOpen = false;
  }

  onAiTripCreated(trip: TripResponse): void {
    this.aiModalOpen = false;
    this.router.navigate(['/trips', trip.id]);
  }

  async confirmDelete(trip: TripSummaryResponse, event: Event): Promise<void> {
    event.stopPropagation();
    const alert = await this.alertCtrl.create({
      header: 'Delete Trip',
      message: `Are you sure you want to delete "${trip.title}"?`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Delete',
          role: 'destructive',
          handler: () => {
            this.tripService.deleteTrip(trip.id).subscribe({
              next: () => {
                this.trips = this.trips.filter((t) => t.id !== trip.id);
              },
              error: (err) => {
                this.showToast(err.message, 'danger');
              },
            });
          },
        },
      ],
    });
    await alert.present();
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      color,
      duration: 2000,
    });
    await toast.present();
  }

  statusColor(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'warning';
      case 'COMPLETED':   return 'success';
      default:            return 'medium';
    }
  }
}
