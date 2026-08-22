// ── Enums ────────────────────────────────────────────────────────────────────
// Mirror backend: com.tripflow.backend.domain.enums.*

export type TripVisibility = 'PUBLIC' | 'PRIVATE';

export type TripStatus = 'DRAFT' | 'PLANNED' | 'ACTIVE' | 'COMPLETED';

export type StopStatus = 'PLANNED' | 'VISITED' | 'SKIPPED';

// Mirror backend: com.tripflow.backend.domain.enums.StopType
export type StopType = 'SIGHTSEEING' | 'MEAL' | 'LODGING' | 'OTHER';

// ── Stop shapes ──────────────────────────────────────────────────────────────

export interface CreateStopRequest {
  name: string;
  latitude: number;
  longitude: number;
  address?: string;
  externalPlaceId?: string;
  notes?: string;
}

// A stop inside a full-itinerary replace (PUT /api/trips/{id}). Mirrors backend
// UpsertStopRequest — CreateStopRequest plus a leading `id` that drives merge-by-identity:
//   id matching a stop on this trip -> updated in place; its server-owned state
//     (status, dayNumber, plannedTime, stopType) and its photos survive.
//   id null -> inserted as a new stop.
//   an existing stop omitted from the list -> deleted. The only intentional delete path.
// `id` is deliberately required, not optional: forgetting it on an existing stop would
// silently delete that stop and its photos, so it must be a compile error, not a default.
export interface UpsertStopRequest extends CreateStopRequest {
  id: number | null;
}

export interface UpdateStopRequest {
  name: string;
  latitude: number;
  longitude: number;
  address?: string;
  externalPlaceId?: string;
  notes?: string;
  status?: StopStatus; // omit to leave unchanged
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
  // SCRUM-244a: null until the trip has been (re-)optimized at least once.
  dayNumber: number | null;
  plannedTime: string | null; // "HH:mm:ss" LocalTime, e.g. "09:00:00"
  stopType: StopType;
}

// ── Trip shapes ──────────────────────────────────────────────────────────────

export interface CreateTripRequest {
  title: string;
  description?: string;
  tags?: string[];
  visibility: TripVisibility;
  stops: CreateStopRequest[];
  startDate?: string; // "YYYY-MM-DD" LocalDate, optional (SCRUM-244a)
}

export interface UpdateTripRequest {
  title: string;
  description?: string;
  tags?: string[];
  visibility: TripVisibility;
  stops: UpsertStopRequest[];
  // Absent/null means "leave unchanged" — this endpoint cannot clear a startDate.
  startDate?: string;
}

// Mirrors backend UpdateTripRequest.MAX_STOPS, enforced by @Size on both create and update.
// Exceeding it is a 400, so the editor blocks the save rather than surfacing a raw error.
export const MAX_STOPS = 50;

export interface GenerateTripRequest {
  prompt: string;
  title?: string;
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
  startDate: string | null; // "YYYY-MM-DD" LocalDate (SCRUM-244a)
  // EXPORT-03: visited-of-total stop counts. completionPercentage is a 0.0-1.0 fraction,
  // mirroring the backend TripCompletion convention.
  visitedStopCount: number;
  completionPercentage: number;
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

// EXPORT-03/D-08: owner-only sibling of TripSummaryResponse, returned by GET /api/trips.
// completionPercentage is a 0.0-1.0 fraction, mirroring the backend TripCompletion
// convention. Never served on the public discovery feed — that stays on
// TripSummaryResponse so a stranger's PUBLIC trip never exposes another user's progress.
export interface TripOwnerSummaryResponse {
  id: number;
  title: string;
  visibility: TripVisibility;
  status: TripStatus;
  createdAt: string;
  updatedAt: string;
  stopCount: number;
  coverPhotoUrl: string | null;
  visitedStopCount: number;
  completionPercentage: number;
}

// SEARCH-01: optional query params for GET /api/trips. All AND together; `search` is
// independently optional. Mirrors backend TripSearchFilters + the `search` param.
export interface TripListFilters {
  search?: string;
  status?: TripStatus;
  visibility?: TripVisibility;
  startDateFrom?: string; // "YYYY-MM-DD" LocalDate
  startDateTo?: string; // "YYYY-MM-DD" LocalDate
  durationDays?: number;
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

// ── AI itinerary suggestions (SCRUM-64/149) ───────────────────────────────────

export interface ItineraryPreferencesRequest {
  interests?: string[];
  budget?: string;
  pace?: string;
}

export interface SuggestedStopResponse {
  order: number;
  name: string;
  latitude: number;
  longitude: number;
  reason: string | null;
}

export interface SuggestedItineraryResponse {
  tripId: number;
  summary: string;
  stops: SuggestedStopResponse[];
}

// ── Stop photos (SCRUM-72/164) ────────────────────────────────────────────────
// Mirrors backend: PhotoSignatureResponse, StopPhotoResponse, CreateStopPhotoRequest

export interface PhotoSignatureResponse {
  cloudName: string;
  apiKey: string;
  timestamp: number;
  signature: string;
  uploadParams: Record<string, unknown>;
}

export interface StopPhotoResponse {
  id: number;
  stopId: number;
  url: string;
  cloudinaryPublicId: string | null;
  caption: string | null;
  createdAt: string; // ISO-8601 UTC Instant
}

export interface CreateStopPhotoRequest {
  url: string;
  cloudinaryPublicId?: string;
  caption?: string;
}