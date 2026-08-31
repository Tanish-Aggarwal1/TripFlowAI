/** Field names are character-identical to backend FeedTripResponse's record components. */
export interface FeedStop {
  id: number;
  name: string;
  address: string | null;
  stopOrder: number;
  notes: string | null;
  photoUrls: string[];
}

export interface FeedTrip {
  id: number;
  title: string;
  description: string | null;
  tags: string[];
  ownerUsername: string;
  likeCount: number;
  createdAt: string;
  stops: FeedStop[];
}

/** SOCIAL-07. Field names are character-identical to backend TripRatingSummaryResponse's
 * record components. `averageRating`/`myRating` are `null` (not `0`) when there is no
 * average to show yet / the caller hasn't rated the trip. */
export interface TripRatingSummary {
  averageRating: number | null;
  ratingCount: number;
  myRating: number | null;
}
