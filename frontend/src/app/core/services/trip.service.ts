import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
import {
  TripResponse,
  TripSummaryResponse,
  PagedResponse,
  CreateTripRequest,
  UpdateTripRequest,
  ItineraryPreferencesRequest,
  SuggestedItineraryResponse,
  CreateStopRequest,
  StopResponse,
} from '../models/trip.model';

@Injectable({ providedIn: 'root' })
export class TripService {
  private baseUrl = `${environment.apiBaseUrl}/trips`;
  private http = inject(HttpClient);

  // Reactive list — dashboard subscribes to this
  trips = signal<TripSummaryResponse[]>([]);

  // ── READ ────────────────────────────────────────────────────────────────────

  listTrips(page = 0, size = 20): Observable<PagedResponse<TripSummaryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedResponse<TripSummaryResponse>>(this.baseUrl, { params }).pipe(
      tap((result) => this.trips.set(result.content)),
      catchError((err: HttpErrorResponse) => this.handleError(err)),
    );
  }

  getTrip(id: number): Observable<TripResponse> {
    return this.http
      .get<TripResponse>(`${this.baseUrl}/${id}`)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // ── WRITE ───────────────────────────────────────────────────────────────────

  createTrip(request: CreateTripRequest): Observable<TripResponse> {
    return this.http.post<TripResponse>(this.baseUrl, request).pipe(
      tap((created) => this.trips.update((list) => [this.toSummary(created), ...list])),
      catchError((err: HttpErrorResponse) => this.handleError(err)),
    );
  }

  updateTrip(id: number, request: UpdateTripRequest): Observable<TripResponse> {
    return this.http.put<TripResponse>(`${this.baseUrl}/${id}`, request).pipe(
      tap((updated) => {
        const summary = this.toSummary(updated);
        this.trips.update((list) =>
          list.map((t) => (t.id === summary.id ? summary : t)),
        );
      }),
      catchError((err: HttpErrorResponse) => this.handleError(err)),
    );
  }

  deleteTrip(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`).pipe(
      tap(() => this.trips.update((list) => list.filter((t) => t.id !== id))),
      catchError((err: HttpErrorResponse) => this.handleError(err)),
    );
  }

  optimizeTrip(id: number): Observable<TripResponse> {
  return this.http.post<TripResponse>(`${this.baseUrl}/${id}/optimize`, {}).pipe(
    tap((updated) => {
      const summary = this.toSummary(updated);
      this.trips.update((list) =>
        list.map((t) => (t.id === summary.id ? summary : t))
      );
    }),
    catchError((err: HttpErrorResponse) => this.handleError(err))
  );
}

  suggestItinerary(
    tripId: number,
    preferences: ItineraryPreferencesRequest,
  ): Observable<SuggestedItineraryResponse> {
    return this.http
      .post<SuggestedItineraryResponse>(`${this.baseUrl}/${tripId}/ai-suggest`, preferences)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // SCRUM-156: calls the existing nested stop-create endpoint (StopController) so an
  // individual AI suggestion can be accepted as a stop without a full trip update.
  addStop(tripId: number, request: CreateStopRequest): Observable<StopResponse> {
    return this.http
      .post<StopResponse>(`${this.baseUrl}/${tripId}/stops`, request)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // SCRUM-177: responseType 'blob' since this is a file download, not JSON. Error
  // bodies come back as a Blob too (not parsed JSON) in that case, but handleError's
  // 403/404 overrides are plain strings that don't need to read the body, so mapping
  // still resolves correctly.
  exportIcs(tripId: number): Observable<Blob> {
    return this.http
      .get(`${this.baseUrl}/${tripId}/calendar.ics`, { responseType: 'blob' })
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // ── MAPPING ──────────────────────────────────────────────────────────────────

  private toSummary(trip: TripResponse): TripSummaryResponse {
    return {
      id: trip.id,
      title: trip.title,
      visibility: trip.visibility,
      status: trip.status,
      createdAt: trip.createdAt,
      updatedAt: trip.updatedAt,
      stopCount: trip.stops.length,
      coverPhotoUrl: null,
    };
  }

  // ── ERROR HANDLING ───────────────────────────────────────────────────────────

  private handleError(err: HttpErrorResponse): Observable<never> {
    const error = mapApiError(err, {
      messagesByStatus: {
        403: 'You do not have permission to do that.',
        404: 'Trip not found.',
      },
      fallbackToBackendMessage: true,
    });

    return throwError(() => error);
  }
}
