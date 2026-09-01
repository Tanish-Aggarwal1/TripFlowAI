import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
import { Profile, UpdateInterestsRequest } from '../models/profile.model';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private baseUrl = `${environment.apiBaseUrl}/profile`;
  private http = inject(HttpClient);

  getProfile(): Observable<Profile> {
    return this.http
      .get<Profile>(this.baseUrl)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  updateInterests(interests: string[]): Observable<Profile> {
    const request: UpdateInterestsRequest = { interests };
    return this.http
      .patch<Profile>(`${this.baseUrl}/interests`, request)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // fallbackToBackendMessage surfaces the server's validation message (e.g. the 20-element
  // or 50-character limit) so the interests-editing UI can render it directly.
  private handleError(err: HttpErrorResponse): Observable<never> {
    const error = mapApiError(err, { fallbackToBackendMessage: true });
    return throwError(() => error);
  }
}
