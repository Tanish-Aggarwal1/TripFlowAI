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

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.discoveryService.getFeed(0, 20).subscribe({
      next: (page) => {
        this.trips.set(page.content);
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
  }
}
