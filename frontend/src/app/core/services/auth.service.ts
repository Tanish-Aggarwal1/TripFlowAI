import { inject, Injectable, Injector, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { AuthResponse, LoginRequest, RefreshResponse, RegisterRequest } from '../models/auth.model';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
import { SessionStateService } from './session-state.service';

const TOKEN_KEY = 'tripflow_token';
const USER_KEY = 'tripflow_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private baseUrl = `${environment.apiBaseUrl}/auth`;
  private http = inject(HttpClient);
  private router = inject(Router);
  private injector = inject(Injector);

  // Reactive auth state other components/guards can read
  isAuthenticated = signal<boolean>(this.hasValidToken());

  // ISO expiry of the current access token. SessionStateService arms the silent-refresh
  // timer off this; seeding it from the stored token is what lets a page reload re-arm.
  expiresAt = signal<string | null>(this.storedTokenExpiry());

  // withCredentials is not optional on these two: a browser discards Set-Cookie on the response
  // to a non-credentialed cross-origin request, and frontend/backend are cross-origin in every
  // environment this ships to. Without it the refresh cookie is never stored at all.
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, request, { withCredentials: true })
      .pipe(
        tap((res) => this.handleAuthSuccess(res)),
        catchError((err: HttpErrorResponse) => this.handleAuthError(err))
      );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, request, { withCredentials: true })
      .pipe(
        tap((res) => this.handleAuthSuccess(res)),
        catchError((err: HttpErrorResponse) => this.handleAuthError(err))
      );
  }

  // Redeems the httpOnly refresh cookie for a new access token. The header is not decorative:
  // it forces a CORS preflight and is the backend's CSRF gate, which answers 400 without it.
  refresh(): Observable<RefreshResponse> {
    return this.http
      .post<RefreshResponse>(
        `${this.baseUrl}/refresh`,
        {},
        { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }
      )
      .pipe(
        tap((res) => {
          localStorage.setItem(TOKEN_KEY, res.token);
          this.expiresAt.set(res.expiresAt);
          this.isAuthenticated.set(true);
        }),
        // Local-only teardown on a 401: the refresh token is known-dead, so calling the
        // revoking logout() here would be redundant, and navigating would fight whatever
        // the caller (guard, silent-refresh timer) is doing about the failure. A transport
        // failure proves nothing about the session, so it must leave storage alone.
        catchError((err: HttpErrorResponse) => {
          if (err.status === 401) this.clearLocalSession();
          return throwError(() => err);
        })
      );
  }

  logout(): void {
    this.http
      .post(
        `${this.baseUrl}/logout`,
        {},
        { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }
      )
      // Local teardown runs on both paths: a user must be able to get out even when the
      // server cannot be told, and the endpoint answers 204 for every cookie state anyway.
      .subscribe({ next: () => this.clearSession(), error: () => this.clearSession() });
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private clearSession(): void {
    this.clearLocalSession();
    // Resolved lazily: SessionStateService injects AuthService, so a field injection here
    // would be a DI cycle. Without the reset the expiry dialog re-opens on the login page.
    this.injector.get(SessionStateService).reset();
    this.router.navigate(['/login']);
  }

  /** Storage and signals only — no server call, no navigation. */
  private clearLocalSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.isAuthenticated.set(false);
    this.expiresAt.set(null);
  }

  private handleAuthSuccess(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify({ userId: res.userId, username: res.username }));
    this.isAuthenticated.set(true);
    this.expiresAt.set(res.expiresAt);
  }

  // Maps backend responses to the generic, non-revealing messages required by UC-01 / UC-02
  private handleAuthError(err: HttpErrorResponse) {
    const error = mapApiError(err, {
      messagesByStatus: {
        401: 'Invalid credentials.',
        409: (body) => body?.message ?? 'Email already registered.',
        400: (body) => (body?.fieldErrors?.length ? 'Please fix the errors below.' : undefined),
      },
      networkErrorMessage: 'Network error. Please check your connection and try again.',
    });

    return throwError(() => error);
  }

  hasValidToken(): boolean {
    return this.storedTokenExpiry() !== null;
  }

  // Single owner of the JWT-payload decode — two copies of a token parser is how they drift.
  private storedTokenExpiry(): string | null {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiresAtMs = payload.exp * 1000;
      return expiresAtMs > Date.now() ? new Date(expiresAtMs).toISOString() : null;
    } catch {
      return null;
    }
  }
}
