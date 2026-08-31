import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, computed, input } from '@angular/core';
import { FeedStop, FeedTrip } from '../../../../core/models/feed.model';
import { FeedActionRailComponent } from '../feed-action-rail/feed-action-rail.component';

/**
 * SOCIAL-01 (D-02/D-03): a single full-screen feed slide. Trip name, major
 * location and owner username stay pinned at the top and the description
 * stays pinned at the bottom regardless of which stop the inner horizontal
 * swiper is showing. Stops with no photos render a readable text card
 * instead of a blank/broken slide (D-03).
 *
 * The `swiper-container`/`swiper-slide` custom-element registration already
 * ran once in FeedPage when the lazy route loaded — do not call register()
 * again here.
 */
@Component({
  selector: 'app-feed-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  imports: [FeedActionRailComponent],
  templateUrl: 'feed-card.component.html',
  styleUrls: ['feed-card.component.scss'],
})
export class FeedCardComponent {
  trip = input.required<FeedTrip>();
  // SOCIAL-07: set by FeedPage from the outer swiper's active index. Forwarded
  // verbatim to the action rail, which uses it to fetch a rating summary only for
  // the slide the viewer is actually looking at.
  active = input(false);

  // D-02's "major location": the first stop's address when present, otherwise
  // the first stop's name, otherwise empty (a trip with no stops at all).
  majorLocation = computed<string>(() => {
    const firstStop = this.trip().stops[0];
    if (!firstStop) return '';
    return firstStop.address ?? firstStop.name ?? '';
  });

  hasPhotos(stop: FeedStop): boolean {
    return stop.photoUrls.length > 0;
  }
}
