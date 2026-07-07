import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { AuthResponse, FieldError, LoginRequest, RegisterRequest } from '../models/auth.model';
import { environment } from '../../../environments/environment';

const TOKEN_KEY = 'tripflow_token';
const USER_KEY = 'tripflow_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private baseUrl = `${environment.apiBaseUrl}/auth`;

  // Reactive auth state other components/guards can read
  isAuthenticated = signal<boolean>(this.hasValidToken());
  currentUsername = signal<string | null>(this.getStoredUsername());

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request).pipe(
      tap((res) => this.handleAuthSuccess(res)),
      catchError((err: HttpErrorResponse) => this.handleAuthError(err))
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, request).pipe(
      tap((res) => this.handleAuthSuccess(res)),
      catchError((err: HttpErrorResponse) => this.handleAuthError(err))
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.isAuthenticated.set(false);
    this.currentUsername.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private handleAuthSuccess(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify({ userId: res.userId, username: res.username }));
    this.isAuthenticated.set(true);
    this.currentUsername.set(res.username);
  }

  // Maps backend responses to the generic, non-revealing messages required by UC-01 / UC-02

private handleAuthError(err: HttpErrorResponse) {
  const body = err.error as { message?: string; fieldErrors?: FieldError[] };
  let message = 'Something went wrong, please try again.';
  let fieldErrors: FieldError[] | undefined;

  if (err.status === 401) {
    message = 'Invalid credentials.';
  } else if (err.status === 409) {
    message = body?.message ?? 'Email already registered.';
  } else if (err.status === 400 && body?.fieldErrors?.length) {
    message = 'Please fix the errors below.';
    fieldErrors = body.fieldErrors;
  } else if (err.status === 0) {
    message = 'Network error. Please check your connection and try again.';
  }

  const error = Object.assign(new Error(message), { fieldErrors });
  return throwError(() => error);
}

  private hasValidToken(): boolean {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  private getStoredUsername(): string | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw).username : null;
  }
}