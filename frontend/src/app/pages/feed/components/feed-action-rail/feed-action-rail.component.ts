import { ChangeDetectionStrategy, Component, computed, effect, inject, input, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { IonButton, IonIcon } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { heart, heartOutline, bookmark, bookmarkOutline, copyOutline, star, starOutline } from 'ionicons/icons';
import { TripService } from '../../../../core/services/trip.service';
import { ToastService } from '../../../../core/services/toast.service';
import { FeedTrip } from '../../../../core/models/feed.model';

/**
 * D-04: the on-card action rail — like, save, clone and rate without leaving the
 * full-screen feed. Like, save and rate mutate in place (optimistic update, revert +
 * toast on failure); clone is the only control that intentionally navigates away, into
 * the new private copy's edit route.
 *
 * Known limitation (recorded in the plan SUMMARY): FeedTripResponse carries no
 * per-viewer like/save membership, so `liked`/`saved` always start false and reflect
 * only actions taken during the current session — not prior sessions' state. The star
 * rating does not share this limitation: it is fetched from
 * `GET /api/trips/{id}/rating` once the card becomes active (SOCIAL-07), so `myRating`
 * reflects the caller's actual prior rating, not just this session's actions.
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
  // Set by the parent card when its outer-swiper slide is the visible one. Drives the
  // rating-summary fetch below — never fetch a summary for a card the viewer hasn't
  // scrolled to yet.
  active = input(false);

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

  // SOCIAL-07: the rail's own rating state. myRating is the caller's stored value (or
  // null if they haven't rated it); averageRating/ratingCount come from the same
  // summary fetch and are never inferred from myRating alone.
  myRating = signal<number | null>(null);
  averageRating = signal<number | null>(null);
  ratingCount = signal(0);
  readonly starValues = [1, 2, 3, 4, 5] as const;

  // Star fill reflects the caller's own rating when they have one; otherwise the
  // rounded average, so a not-yet-rated trip still shows something meaningful.
  displayRating = computed<number>(() => this.myRating() ?? Math.round(this.averageRating() ?? 0));

  // Guards the one-fetch-per-card-activation contract independently of `busy` below —
  // rating mutation and the summary read are different concerns, and a rate request
  // should not block the summary from loading (or vice versa).
  private summaryRequested = signal(false);

  // Single shared in-flight flag: guards every handler below so a rapid double-tap
  // (on the same or a different control) collapses to one outstanding request rather
  // than racing two, mirroring FeedPage's loadingMore guard convention.
  busy = signal(false);

  constructor() {
    addIcons({
      heart,
      'heart-outline': heartOutline,
      bookmark,
      'bookmark-outline': bookmarkOutline,
      'copy-outline': copyOutline,
      star,
      'star-outline': starOutline,
    });

    // Fetches once per card, the first time it becomes the active slide — never once
    // per feed page load, and never again once fetched (re-activating the same slide
    // on scroll-back doesn't re-fetch a summary that a rate() call already keeps fresh
    // locally).
    effect(() => {
      if (this.active() && !this.summaryRequested()) {
        this.summaryRequested.set(true);
        this.tripService.getTripRating(this.trip().id).subscribe({
          next: (summary) => {
            this.averageRating.set(summary.averageRating);
            this.ratingCount.set(summary.ratingCount);
            this.myRating.set(summary.myRating);
          },
          error: (err: unknown) => {
            void this.toastService.showError(err, 'Could not load rating');
          },
        });
      }
    });
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

  isFilled(value: number): boolean {
    return value <= this.displayRating();
  }

  rate(value: number): void {
    if (this.busy()) return;
    this.busy.set(true);

    const previousRating = this.myRating();
    this.myRating.set(value);

    this.tripService.rateTrip(this.trip().id, value).subscribe({
      next: () => this.busy.set(false),
      error: (err: unknown) => {
        this.myRating.set(previousRating);
        this.busy.set(false);
        void this.toastService.showError(err, 'Could not update rating');
      },
    });
  }
}
