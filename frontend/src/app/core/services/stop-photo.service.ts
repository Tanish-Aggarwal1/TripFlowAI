import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Observable, catchError, throwError, switchMap, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
import {
  PhotoSignatureResponse,
  StopPhotoResponse,
  CreateStopPhotoRequest,
} from '../models/trip.model';

// SCRUM-164: signed direct-to-Cloudinary upload. File bytes never touch our
// backend — only the signed params (from our API) and the final secure_url
// (posted back to our API to persist) do.
@Injectable({ providedIn: 'root' })
export class StopPhotoService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/stops`;

  // ── Our backend ──────────────────────────────────────────────────────────

  private getSignature(stopId: number): Observable<PhotoSignatureResponse> {
    return this.http
      .post<PhotoSignatureResponse>(`${this.baseUrl}/${stopId}/photo-signature`, {})
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  addPhoto(stopId: number, request: CreateStopPhotoRequest): Observable<StopPhotoResponse> {
    return this.http
      .post<StopPhotoResponse>(`${this.baseUrl}/${stopId}/photos`, request)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  listPhotos(stopId: number): Observable<StopPhotoResponse[]> {
    return this.http
      .get<StopPhotoResponse[]>(`${this.baseUrl}/${stopId}/photos`)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  deletePhoto(stopId: number, photoId: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/${stopId}/photos/${photoId}`)
      .pipe(catchError((err: HttpErrorResponse) => this.handleError(err)));
  }

  // ── Cloudinary (direct, different origin — our auth interceptor is scoped
  // to apiBaseUrl so no Bearer token leaks to it) + our persist endpoint,
  // chained into one call. Emits { progress } during upload, then a final
  // { done: StopPhotoResponse } once persisted. ─────────────────────────────

  uploadPhoto(
    stopId: number,
    file: File,
    caption?: string,
  ): Observable<{ progress: number } | { done: StopPhotoResponse }> {
    return this.getSignature(stopId).pipe(
      switchMap((sig) => this.uploadToCloudinary(sig, file)),
      switchMap((result) => {
        if ('progress' in result) {
          return new Observable<{ progress: number } | { done: StopPhotoResponse }>((sub) => {
            sub.next(result);
            sub.complete();
          });
        }
        return this.addPhoto(stopId, {
          url: result.secureUrl,
          cloudinaryPublicId: result.publicId,
          caption,
        }).pipe(map((photo) => ({ done: photo })));
      }),
    );
  }

  private uploadToCloudinary(
    sig: PhotoSignatureResponse,
    file: File,
  ): Observable<{ progress: number } | { secureUrl: string; publicId: string }> {
    const form = new FormData();
    form.append('file', file);
    form.append('api_key', sig.apiKey);
    form.append('timestamp', String(sig.timestamp));
    form.append('signature', sig.signature);
    for (const [key, value] of Object.entries(sig.uploadParams)) {
      form.append(key, String(value));
    }

    const url = `https://api.cloudinary.com/v1_1/${sig.cloudName}/image/upload`;

    return this.http.post(url, form, { reportProgress: true, observe: 'events' }).pipe(
      map((event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          return { progress: Math.round((100 * event.loaded) / event.total) };
        }
        if (event.type === HttpEventType.Response) {
          const body = event.body as { secure_url: string; public_id: string };
          return { secureUrl: body.secure_url, publicId: body.public_id };
        }
        return { progress: 0 };
      }),
      catchError(() =>
        throwError(() => ({
          message: 'Upload to Cloudinary failed. Check your connection and try again.',
        })),
      ),
    );
  }

  // ── Error handling ───────────────────────────────────────────────────────

  private handleError(err: HttpErrorResponse): Observable<never> {
    const error = mapApiError(err, {
      messagesByStatus: {
        403: 'You do not have permission to manage photos on this stop.',
        404: 'Stop not found.',
      },
      fallbackToBackendMessage: true,
    });
    return throwError(() => error);
  }
}