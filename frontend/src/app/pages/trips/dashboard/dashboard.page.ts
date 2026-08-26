import { ChangeDetectorRef, Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import {
  IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
  IonList, IonItem, IonLabel, IonBadge, IonIcon, IonSearchbar, IonSelect, IonSelectOption,
  IonFab, IonFabButton, IonFabList, IonModal, IonSpinner, AlertController,IonThumbnail,
  ViewWillEnter
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { add, lockClosed, globeOutline, trash, create, sparkles, imageOutline, mapOutline } from 'ionicons/icons';
import { TripService } from '../../../core/services/trip.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  TripOwnerSummaryResponse,
  TripResponse,
  TripListFilters,
  TripStatus,
  TripVisibility,
} from '../../../core/models/trip.model';
import { AiTripPromptComponent } from '../components/ai-trip-prompt/ai-trip-prompt.component';


@Component({
  selector: 'app-dashboard',
  templateUrl: 'dashboard.page.html',
  styleUrls: ['dashboard.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonButtons,
    IonList, IonItem, IonLabel, IonBadge, IonIcon, IonSearchbar, IonSelect, IonSelectOption,
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
    private cdr = inject(ChangeDetectorRef);

  trips: TripOwnerSummaryResponse[] = [];
  loading = true;
  error: string | null = null;

  // D-15/SEARCH-01: the whole filter set (search + status/visibility/date/duration)
  // flows through one debounced/distinct/switchMap stream, so a keystroke and a filter
  // change share one request path and one in-flight-request policy rather than racing.
  filters: TripListFilters = {};
  private filterChanges = new Subject<TripListFilters>();

  // "Create with AI" modal (dashboard entry point for generating a whole new
  // trip from a free-text prompt), distinct from the SCRUM-67 ai-suggest
  // modal on trip-view, which only adds suggestions onto an existing trip.
  aiModalOpen = false;

  constructor() {
    addIcons({
      add,
      lockClosed,
      globeOutline,
      trash,
      create,
      sparkles,
      'image-outline': imageOutline,
      'map-outline': mapOutline,
    });

    this.filterChanges
      .pipe(
        debounceTime(350),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        switchMap((filters) => {
          this.loading = true;
          this.error = null;
          return this.tripService.listTrips(0, 20, filters);
        }),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (page) => {
          this.trips = page.content;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.error = err.message;
          this.loading = false;
          this.cdr.markForCheck();
        },
      });
  }

  ionViewWillEnter(): void {
    this.loadTrips();
  }

  onSearchChange(term: string): void {
    this.filters = { ...this.filters, search: term };
    this.filterChanges.next(this.filters);
  }

  onStatusChange(value: TripStatus | null): void {
    this.filters = { ...this.filters, status: value ?? undefined };
    this.filterChanges.next(this.filters);
  }

  onVisibilityChange(value: TripVisibility | null): void {
    this.filters = { ...this.filters, visibility: value ?? undefined };
    this.filterChanges.next(this.filters);
  }

  onStartDateFromChange(value: string): void {
    this.filters = { ...this.filters, startDateFrom: value || undefined };
    this.filterChanges.next(this.filters);
  }

  onStartDateToChange(value: string): void {
    this.filters = { ...this.filters, startDateTo: value || undefined };
    this.filterChanges.next(this.filters);
  }

  onDurationDaysChange(value: string): void {
    this.filters = { ...this.filters, durationDays: value === '' ? undefined : Number(value) };
    this.filterChanges.next(this.filters);
  }

  clearFilters(): void {
    this.filters = {};
    this.filterChanges.next(this.filters);
  }

  loadTrips(): void {
    this.loading = true;
    this.error = null;
    this.tripService.listTrips(0, 20, this.filters).subscribe({
      next: (page) => {
        this.trips = page.content;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err.message;
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  openTrip(trip: TripOwnerSummaryResponse): void {
    this.router.navigate(['/trips', trip.id]);
  }

  editTrip(trip: TripOwnerSummaryResponse, event: Event): void {
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

  async confirmDelete(trip: TripOwnerSummaryResponse, event: Event): Promise<void> {
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
                this.cdr.markForCheck();
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
      case 'PLANNED':   return 'tertiary';
      case 'ACTIVE':    return 'warning';
      case 'COMPLETED': return 'success';
      default:          return 'medium';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'PLANNED':   return 'Planned';
      case 'ACTIVE':    return 'Active';
      case 'COMPLETED': return 'Completed';
      case 'DRAFT':     return 'Draft';
      default:          return status;
    }
  }

  // EXPORT-03: rounded in the component, not the template, so it stays unit-testable.
  completionPercent(trip: TripOwnerSummaryResponse): number {
    return Math.round(trip.completionPercentage * 100);
  }

  completionLabel(trip: TripOwnerSummaryResponse): string {
    return `${trip.visitedStopCount} of ${trip.stopCount} visited (${this.completionPercent(trip)}%)`;
  }
}
