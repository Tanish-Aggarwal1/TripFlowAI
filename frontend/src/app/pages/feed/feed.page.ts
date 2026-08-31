import {
  ChangeDetectionStrategy,
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { IonContent, IonSpinner } from '@ionic/angular';
import { register } from 'swiper/element/bundle';
import { DiscoveryService } from '../../core/services/discovery.service';
import { FeedTrip } from '../../core/models/feed.model';
import { FeedCardComponent } from './components/feed-card/feed-card.component';

// Registers <swiper-container>/<swiper-slide> as custom elements. Done here (the
// lazily-loaded feed route) rather than main.ts so Swiper never enters the
// login/dashboard bundle for users who never open the feed.
register();

/**
 * SOCIAL-01 (D-01): the outer vertical "trip-to-trip" swiper. Full-screen,
 * one PUBLIC trip per slide, rendered via FeedCardComponent (D-02's fixed
 * header/footer chrome, D-03's no-photo text fallback, the inner horizontal
 * "stop-to-stop" swiper).
 */
@Component({
  selector: 'app-feed',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IonContent, IonSpinner, FeedCardComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: 'feed.page.html',
  styleUrls: ['feed.page.scss'],
})
export class FeedPage implements OnInit {
  private discoveryService = inject(DiscoveryService);

  trips = signal<FeedTrip[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  activeIndex = signal(0);

  // Paging state (Task 4): currentPage/totalPages mirror the last-loaded
  // PagedResponse.page so loadNextPage() knows when the feed is exhausted.
  currentPage = signal(0);
  totalPages = signal(1);
  loadingMore = signal(false);

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.discoveryService.getFeed(0, 20).subscribe({
      next: (page) => {
        this.trips.set(page.content);
        this.currentPage.set(page.page.number);
        this.totalPages.set(page.page.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message ?? 'Could not load the feed.');
        this.loading.set(false);
      },
    });
  }

  onSlideChange(event: Event): void {
    const swiper = (event as CustomEvent).detail?.[0];
    if (swiper && typeof swiper.activeIndex === 'number') {
      this.activeIndex.set(swiper.activeIndex);
    }
    if (this.activeIndex() >= this.trips().length - 3) {
      this.loadNextPage();
    }
  }

  // Guarded by loadingMore (set synchronously, before the request resolves) so
  // two threshold-crossing slide-change events in quick succession issue one
  // request, not two. Guarded by totalPages so an exhausted feed stops asking.
  loadNextPage(): void {
    if (this.loadingMore() || this.currentPage() + 1 >= this.totalPages()) {
      return;
    }
    this.loadingMore.set(true);
    this.discoveryService.getFeed(this.currentPage() + 1, 20).subscribe({
      next: (page) => {
        this.trips.update((trips) => [...trips, ...page.content]);
        this.currentPage.set(page.page.number);
        this.totalPages.set(page.page.totalPages);
        this.loadingMore.set(false);
      },
      error: (err) => {
        // Non-destructive: leave the already-loaded trips in place.
        this.error.set(err.message ?? 'Could not load more trips.');
        this.loadingMore.set(false);
      },
    });
  }
}
