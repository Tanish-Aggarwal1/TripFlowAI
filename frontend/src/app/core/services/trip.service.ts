import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
import {
  TripResponse,
  TripOwnerSummaryResponse,
  PagedResponse,
  CreateTripRequest,
  UpdateTripRequest,
  GenerateTripRequest,
  ItineraryPreferencesRequest,
  SuggestedItineraryResponse,
  CreateStopRequest,
  UpdateStopRequest,
  StopResponse,
} from '../models/trip.model';

@Injectable({ providedIn: 'root' })
export class TripService {
  private baseUrl = `${environment.apiBaseUrl}/trips`;
  private http = inject(HttpClient);

  // ── READ ────────────────────────────────────────────────────────────────────

  // SEARCH-01: search is appended only when non-blank, so an unfiltered request's URL
  // (`?page=&size=`) stays exactly as it was before search existed.
  listTrips(
    page = 0,
    size = 20,
    search?: string,
  ): Observable<PagedResponse<TripOwnerSummaryResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http
      .get<PagedResponse<TripOwnerSummaryResponse>>(this.baseUrl, { params })
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  getTrip(id: number): Observable<TripResponse> {
    return this.http
      .get<TripResponse>(`${this.baseUrl}/${id}`)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // ── WRITE ───────────────────────────────────────────────────────────────────

  createTrip(request: CreateTripRequest): Observable<TripResponse> {
    return this.http
      .post<TripResponse>(this.baseUrl, request)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // SCRUM-ai-generate: sends a free-text prompt to Gemini and creates a whole
  // new trip in one call — distinct from suggestItinerary/addStop, which only
  // add suggestions onto a trip that already exists.
  generateTripWithAi(request: GenerateTripRequest): Observable<TripResponse> {
    return this.http
      .post<TripResponse>(`${this.baseUrl}/ai-generate`, request)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  updateTrip(id: number, request: UpdateTripRequest): Observable<TripResponse> {
    return this.http
      .put<TripResponse>(`${this.baseUrl}/${id}`, request)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  deleteTrip(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/${id}`)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  optimizeTrip(id: number): Observable<TripResponse> {
    return this.http
      .post<TripResponse>(`${this.baseUrl}/${id}/optimize`, {})
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
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

  // SCRUM-250: calls the existing nested stop-update endpoint (StopController).
  updateStop(
    tripId: number,
    stopId: number,
    request: UpdateStopRequest,
  ): Observable<StopResponse> {
    return this.http
      .put<StopResponse>(`${this.baseUrl}/${tripId}/stops/${stopId}`, request)
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

  // EXPORT-02: same blob-download shape as exportIcs above.
  exportPdf(tripId: number): Observable<Blob> {
    return this.http
      .get(`${this.baseUrl}/${tripId}/export/pdf`, { responseType: 'blob' })
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
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
