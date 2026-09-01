import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { heart, heartOutline, bookmark, bookmarkOutline, copyOutline } from 'ionicons/icons';
import { TripService } from '../../../../core/services/trip.service';
import { ToastService } from '../../../../core/services/toast.service';
import { FeedTrip } from '../../../../core/models/feed.model';

/**
 * D-04: the on-card action rail — like, save and clone without leaving the full-screen
 * feed. Like and save mutate in place (optimistic update, revert + toast on failure);
 * clone is the only control that intentionally navigates away, into the new private
 * copy's edit route.
 *
 * Known limitation (recorded in the plan SUMMARY): FeedTripResponse carries no
 * per-viewer like/save membership, so `liked`/`saved` always start false and reflect
 * only actions taken during the current session — not prior sessions' state.
 */
@Component({
  selector: 'app-feed-action-rail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IonButton, IonIcon],
  templateUrl: 'feed-action-rail.component.html',
  styleUrls: ['feed-action-rail.component.scss'],
})
export class FeedActionRailComponent implements OnInit {
  trip = input.required<FeedTrip>();

  private tripService = inject(TripService);
  private toastService = inject(ToastService);
  private router = inject(Router);

  // Session-local only — see class doc.
  liked = signal(false);
  saved = signal(false);
  // Seeded from trip().likeCount in ngOnInit rather than a field initializer: signal
  // inputs are only guaranteed resolved by the first lifecycle hook, not at
  // constructor-run time (NG0950 otherwise, e.g. when a test creates this component
  // via TestBed.createComponent + componentRef.setInput rather than a template binding).
  likeCount = signal(0);

  // Single shared in-flight flag: guards every handler below so a rapid double-tap
  // (on the same or a different control) collapses to one outstanding request rather
  // than racing two, mirroring FeedPage's loadingMore guard convention.
  busy = signal(false);

  constructor() {
    addIcons({ heart, 'heart-outline': heartOutline, bookmark, 'bookmark-outline': bookmarkOutline, 'copy-outline': copyOutline });
  }

  ngOnInit(): void {
    this.likeCount.set(this.trip().likeCount);
  }

  toggleLike(): void {
    if (this.busy()) return;
    this.busy.set(true);

    const wasLiked = this.liked();
    const previousCount = this.likeCount();
    const nextLiked = !wasLiked;
    this.liked.set(nextLiked);
    this.likeCount.set(previousCount + (nextLiked ? 1 : -1));

    const request$ = nextLiked
      ? this.tripService.likeTrip(this.trip().id)
      : this.tripService.unlikeTrip(this.trip().id);

    request$.subscribe({
      next: () => this.busy.set(false),
      error: (err: unknown) => {
        this.liked.set(wasLiked);
        this.likeCount.set(previousCount);
        this.busy.set(false);
        void this.toastService.showError(err, 'Could not update like');
      },
    });
  }

  toggleSave(): void {
    if (this.busy()) return;
    this.busy.set(true);

    const wasSaved = this.saved();
    const nextSaved = !wasSaved;
    this.saved.set(nextSaved);

    const request$ = nextSaved
      ? this.tripService.saveTrip(this.trip().id)
      : this.tripService.unsaveTrip(this.trip().id);

    request$.subscribe({
      next: () => this.busy.set(false),
      error: (err: unknown) => {
        this.saved.set(wasSaved);
        this.busy.set(false);
        void this.toastService.showError(err, 'Could not update save');
      },
    });
  }

  clone(): void {
    if (this.busy()) return;
    this.busy.set(true);

    this.tripService.cloneTrip(this.trip().id).subscribe({
      next: (clonedTrip) => {
        this.busy.set(false);
        this.router.navigate(['/trips', clonedTrip.id, 'edit']);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        void this.toastService.showError(err, 'Could not clone trip');
      },
    });
  }
}
