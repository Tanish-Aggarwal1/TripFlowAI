import { Component, DestroyRef, inject, OnDestroy, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { IonContent, IonSpinner, IonButton } from '@ionic/angular';
import { environment } from '../../../environments/environment';

const POLL_INTERVAL_MS = 4000;
const MAX_POLL_ATTEMPTS = 30; // ~2 minutes at POLL_INTERVAL_MS

// SCRUM-273: Render's free-tier backend spins down on idle. Landed here (via
// backendAvailabilityInterceptor) when an API call timed out or failed to
// connect at all — polls the public health endpoint until the backend is
// actually back, then continues to wherever the user was headed.
//
// HttpClient is injected directly (the app's only page-level use) rather than
// through a service: the health URL is deliberately built to NOT start with
// apiBaseUrl, which keeps backendAvailabilityInterceptor from intercepting
// this request and redirecting back here in a loop. Routing this through a
// service on the normal interceptor chain would reintroduce that recursion.
@Component({
  selector: 'app-starting-up',
  templateUrl: 'starting-up.page.html',
  styleUrls: ['starting-up.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IonContent, IonSpinner, IonButton],
})
export class StartingUpPage implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private attempts = 0;

  readonly unavailable = signal(false);

  ngOnInit(): void {
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  retry(): void {
    this.attempts = 0;
    this.unavailable.set(false);
    this.startPolling();
  }

  private startPolling(): void {
    this.checkHealth();
    this.pollHandle = setInterval(() => this.checkHealth(), POLL_INTERVAL_MS);
  }

  private checkHealth(): void {
    this.attempts++;
    const healthUrl = `${environment.apiBaseUrl.replace(/\/api\/?$/, '')}/actuator/health`;
    this.http
      .get(healthUrl)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.onBackendReady(),
        error: () => {
          if (this.attempts >= MAX_POLL_ATTEMPTS) {
            this.stopPolling();
            this.unavailable.set(true);
          }
          // Otherwise still down (or still starting) - the next scheduled poll will retry.
        },
      });
  }

  private onBackendReady(): void {
    this.stopPolling();
    this.router.navigateByUrl(this.safeRedirectTarget());
  }

  // Only allow same-origin relative paths — `redirect` comes from a query
  // param an attacker can set directly (`/starting-up?redirect=...`), so a
  // scheme-relative value like `//evil.com` must not reach navigateByUrl.
  private safeRedirectTarget(): string {
    const redirect = this.route.snapshot.queryParamMap.get('redirect');
    const isSafeRelativePath = !!redirect && redirect.startsWith('/') && !redirect.startsWith('//');
    return isSafeRelativePath ? redirect : '/login';
  }

  private stopPolling(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }
}
