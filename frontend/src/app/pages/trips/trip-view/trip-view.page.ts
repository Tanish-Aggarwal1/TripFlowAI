import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import {
  IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
  IonBackButton, IonSpinner, IonIcon, IonList, IonItem, IonLabel,
  ToastController
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { create } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { TripResponse } from '../../../core/models/trip.model';
import { TripMapComponent } from '../components/trip-map/trip-map.component';

@Component({
  selector: 'app-trip-view',
  templateUrl: 'trip-view.page.html',
  styleUrls: ['trip-view.page.scss'],
  imports: [
    CommonModule,
    IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
    IonBackButton, IonSpinner, IonIcon, IonList, IonItem, IonLabel,
    TripMapComponent,
  ],
})
export class TripViewPage implements OnInit {
  trip: TripResponse | null = null;
  loading = true;
  error: string | null = null;
  optimizing = false;

  private tripId = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private tripService: TripService,
    private toastCtrl: ToastController,
  ) {
    addIcons({ create });
  }

  ngOnInit(): void {
    this.tripId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadTrip();
  }

  loadTrip(): void {
    this.loading = true;
    this.error = null;
    this.tripService.getTrip(this.tripId).subscribe({
      next: (trip) => {
        this.trip = trip;
        this.loading = false;
      },
      error: (err) => {
        this.error = err.message;
        this.loading = false;
      },
    });
  }

  editTrip(): void {
    if (this.trip) {
      this.router.navigate(['/trips', this.trip.id, 'edit']);
    }
  }

  onOptimizeRequested(): void {
    if (!this.trip || this.optimizing) return;
    this.optimizing = true;

    this.tripService.optimizeTrip(this.trip.id).subscribe({
      next: async (updated) => {
        this.trip = updated;
        this.optimizing = false;
        const toast = await this.toastCtrl.create({
          message: 'Route optimized.',
          duration: 2000,
          color: 'success',
        });
        await toast.present();
      },
      error: async (err) => {
        this.optimizing = false;
        const toast = await this.toastCtrl.create({
          message: err.message ?? 'Could not optimize route.',
          duration: 2500,
          color: 'danger',
        });
        await toast.present();
      },
    });
  }
}