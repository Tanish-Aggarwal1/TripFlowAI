import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
import { PagedResponse } from '../models/trip.model';
import { FeedTrip } from '../models/feed.model';

/**
 * SOCIAL-01: the authenticated "For You" feed data seam. The Bearer token is attached
 * by the existing HTTP interceptor chain — no auth header is set here.
 */
@Injectable({ providedIn: 'root' })
export class DiscoveryService {
  private baseUrl = `${environment.apiBaseUrl}/discovery`;
  private http = inject(HttpClient);

  getFeed(page = 0, size = 20): Observable<PagedResponse<FeedTrip>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http
      .get<PagedResponse<FeedTrip>>(`${this.baseUrl}/feed`, { params })
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  private handleError(err: HttpErrorResponse): Observable<never> {
    const error = mapApiError(err, {
      messagesByStatus: {
        401: 'You must be signed in to view the feed.',
        404: 'Feed not found.',
      },
      fallbackToBackendMessage: true,
    });

    return throwError(() => error);
  }
}
