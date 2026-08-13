import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
  IonList, IonItem, IonLabel, IonBadge, IonIcon,
  IonFab, IonFabButton, IonFabList, IonModal, IonSpinner, AlertController,IonThumbnail,
  ViewWillEnter
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, lockClosed, globeOutline, trash, create, sparkles, imageOutline  } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { ToastService } from '../../../core/services/toast.service';
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
    private toastService = inject(ToastService);

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

  // Set by onAiTripCreated, consumed once the modal has actually finished
  // dismissing (didDismiss) — navigating on the same tick as isOpen=false
  // races the modal's dismiss animation and can leave the overlay stuck.
  private pendingAiTripNavigation: TripResponse | null = null;

  closeAiModal(): void {
    this.aiModalOpen = false;
  }

  // Bound to (didDismiss), which fires once the modal has fully closed —
  // including a user swipe/backdrop dismiss, not just our own isOpen=false,
  // so this also re-syncs aiModalOpen the same way closeAiModal() did.
  onAiModalDismissed(): void {
    this.aiModalOpen = false;
    const trip = this.pendingAiTripNavigation;
    this.pendingAiTripNavigation = null;
    if (trip) {
      this.router.navigate(['/trips', trip.id]);
    }
  }

  onAiTripCreated(trip: TripResponse): void {
    this.pendingAiTripNavigation = trip;
    this.aiModalOpen = false;
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
                this.toastService.showError(err, 'Could not delete trip.');
              },
            });
          },
        },
      ],
    });
    await alert.present();
  }

  statusColor(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'warning';
      case 'COMPLETED':   return 'success';
      default:            return 'medium';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In progress';
      case 'COMPLETED':   return 'Completed';
      case 'DRAFT':        return 'Draft';
      default:            return status;
    }
  }
}
