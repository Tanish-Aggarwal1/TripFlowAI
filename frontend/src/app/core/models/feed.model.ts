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
