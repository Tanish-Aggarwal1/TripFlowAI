import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonHeader, IonToolbar, IonTitle, IonContent, IonButton,
  IonList, IonItem, IonLabel, IonBadge, IonIcon,
  IonFab, IonFabButton, IonSpinner, AlertController,
  ViewWillEnter
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, lockClosed, globeOutline, trash, create } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { TripSummaryResponse } from '../../../core/models/trip.model';

@Component({
  selector: 'app-dashboard',
  templateUrl: 'dashboard.page.html',
  styleUrls: ['dashboard.page.scss'],
  imports: [
    IonHeader, IonToolbar, IonTitle, IonContent, IonButton,
    IonList, IonItem, IonLabel, IonBadge, IonIcon,
    IonFab, IonFabButton, IonSpinner,
  ],
})
export class DashboardPage implements ViewWillEnter {

  
    private tripService = inject(TripService);
    private router = inject(Router);
    private alertCtrl = inject(AlertController);

  trips: TripSummaryResponse[] = [];
  loading = true;
  error: string | null = null;

  constructor() {
    addIcons({ add, lockClosed, globeOutline, trash, create });
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
                this.error = err.message;
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
}