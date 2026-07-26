// ── Enums ────────────────────────────────────────────────────────────────────
// Mirror backend: com.tripflow.backend.domain.enums.*

export type TripVisibility = 'PUBLIC' | 'PRIVATE';

export type TripStatus = 'DRAFT' | 'IN_PROGRESS' | 'COMPLETED';

export type StopStatus = 'PLANNED' | 'VISITED' | 'SKIPPED';

// ── Stop shapes ──────────────────────────────────────────────────────────────

export interface CreateStopRequest {
  name: string;
  latitude: number;
  longitude: number;
  address?: string;
  externalPlaceId?: string;
  notes?: string;
}

export interface StopResponse {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
  address: string | null;
  stopOrder: number;
  status: StopStatus;
  notes: string | null;
}

// ── Trip shapes ──────────────────────────────────────────────────────────────

export interface CreateTripRequest {
  title: string;
  description?: string;
  tags?: string[];
  visibility: TripVisibility;
  stops: CreateStopRequest[];
}

export interface UpdateTripRequest {
  title: string;
  description?: string;
  tags?: string[];
  visibility: TripVisibility;
  stops: CreateStopRequest[];
}

export interface TripResponse {
  id: number;
  title: string;
  description: string | null;
  tags: string[];
  visibility: TripVisibility;
  status: TripStatus;
  ownerId: number;
  stops: StopResponse[];
  createdAt: string;  // ISO-8601 UTC Instant e.g. "2026-07-13T14:20:00Z"
  updatedAt: string;
  routeGeometry: string | null; //// JSON-encoded GeoJSON LineString; JSON.parse before use. Null pre-optimization.

}

// Card-sized projection returned by the paginated GET /api/trips list endpoint (REF-21) —
// no `stops` array, just a count, so list views never pull full itinerary data.
export interface TripSummaryResponse {
  id: number;
  title: string;
  visibility: TripVisibility;
  status: TripStatus;
  createdAt: string;
  updatedAt: string;
  stopCount: number;
  coverPhotoUrl: string | null;
}

// Matches Spring Data's PagedModel shape: { content, page: { size, number, totalElements, totalPages } }
export interface PagedResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

// ── API error shape (matches ApiError as of REF-10) ──────────────────────────

export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors: Array<{ field: string; message: string }> | null;
}