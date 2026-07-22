import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  TripResponse,
  CreateTripRequest,
  UpdateTripRequest,
  ApiError,
} from '../models/trip.model';

@Injectable({ providedIn: 'root' })
export class TripService {
  private baseUrl = `${environment.apiBaseUrl}/trips`;
  private http = inject(HttpClient);

  // Reactive list — dashboard subscribes to this
  trips = signal<TripResponse[]>([]);

  // ── READ ────────────────────────────────────────────────────────────────────

  listTrips(): Observable<TripResponse[]> {
    return this.http.get<TripResponse[]>(this.baseUrl).pipe(
      tap((trips) => this.trips.set(trips)),
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
      tap((created) => this.trips.update((list) => [created, ...list])),
      catchError((err: HttpErrorResponse) => this.handleError(err)),
    );
  }

  updateTrip(id: number, request: UpdateTripRequest): Observable<TripResponse> {
    return this.http.put<TripResponse>(`${this.baseUrl}/${id}`, request).pipe(
      tap((updated) =>
        this.trips.update((list) =>
          list.map((t) => (t.id === updated.id ? updated : t)),
        ),
      ),
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
    tap((updated) =>
      this.trips.update((list) =>
        list.map((t) => (t.id === updated.id ? updated : t))
      )
    ),
    catchError((err: HttpErrorResponse) => this.handleError(err))
  );
}

  // ── ERROR HANDLING ───────────────────────────────────────────────────────────

  private handleError(err: HttpErrorResponse): Observable<never> {
    const body = err.error as Partial<ApiError>;
    let message = 'Something went wrong, please try again.';

    if (err.status === 0) {
      message = 'Network error. Please check your connection.';
    } else if (err.status === 403) {
      message = 'You do not have permission to do that.';
    } else if (err.status === 404) {
      message = 'Trip not found.';
    } else if (err.status === 400 && body?.message) {
      message = body.message;
    } else if (body?.message) {
      message = body.message;
    }

    const error = Object.assign(new Error(message), {
      fieldErrors: body?.fieldErrors ?? null,
      status: err.status,
    });

    return throwError(() => error);
  }
}
