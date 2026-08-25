import { Component, inject, OnDestroy, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { IonContent, IonSpinner } from '@ionic/angular/standalone';
import { environment } from '../../../environments/environment';

const POLL_INTERVAL_MS = 4000;

// SCRUM-273: Render's free-tier backend spins down on idle. Landed here (via
// backendAvailabilityInterceptor) when an API call timed out or failed to
// connect at all — polls the public health endpoint until the backend is
// actually back, then continues to wherever the user was headed.
@Component({
  selector: 'app-starting-up',
  templateUrl: 'starting-up.page.html',
  styleUrls: ['starting-up.page.scss'],
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [IonContent, IonSpinner],
})
export class StartingUpPage implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.checkHealth();
    this.pollHandle = setInterval(() => this.checkHealth(), POLL_INTERVAL_MS);
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  private checkHealth(): void {
    const healthUrl = `${environment.apiBaseUrl.replace(/\/api\/?$/, '')}/actuator/health`;
    this.http.get(healthUrl).subscribe({
      next: () => this.onBackendReady(),
      error: () => {
        // Still down (or still starting) - the next scheduled poll will retry.
      },
    });
  }

  private onBackendReady(): void {
    this.stopPolling();
    const redirect = this.route.snapshot.queryParamMap.get('redirect') || '/login';
    this.router.navigateByUrl(redirect);
  }

  private stopPolling(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }
}
