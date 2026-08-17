import { DestroyRef, Injectable, effect, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';

export type SessionStatus = 'active' | 'refreshing' | 'expired';

// D-05: renew shortly before the 15-minute access token lapses, not after a 401.
const REFRESH_BUFFER_MS = 60_000;

// Observes the access token's expiry and renews it in the background. It deliberately owns
// no presentation and no session teardown: the status signal is all it publishes, and the
// AppComponent banner/dialog (D-06) and AuthService own everything the user sees.
// The dependency points one way — this service knows AuthService, never the reverse.
@Injectable({ providedIn: 'root' })
export class SessionStateService {
  private readonly auth = inject(AuthService);
  private readonly sessionStatus = signal<SessionStatus>('active');
  private timer: ReturnType<typeof setTimeout> | null = null;

  readonly status = this.sessionStatus.asReadonly();

  private readonly onVisibilityChange = (): void => {
    if (document.visibilityState !== 'visible') return;
    if (this.sessionStatus() === 'expired') return;
    // A backgrounded tab can have its timeout throttled or fired late, so trust the clock
    // rather than the timer once the tab is back in front of the user.
    if (this.isDue(this.auth.expiresAt())) this.renew();
  };

  constructor() {
    effect(() => this.schedule(this.auth.expiresAt()));
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    inject(DestroyRef).onDestroy(() => {
      this.clearTimer();
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    });
  }

  /** A 401 from a normal API call means this session is done, whatever the timer thinks. */
  markExpired(): void {
    this.clearTimer();
    this.sessionStatus.set('expired');
  }

  private schedule(expiresAt: string | null): void {
    this.clearTimer();
    if (!expiresAt) return;
    // A new expiry is proof of a live session — this is also what clears the banner on re-login.
    this.sessionStatus.set('active');
    const delay = Math.max(0, Date.parse(expiresAt) - Date.now() - REFRESH_BUFFER_MS);
    this.timer = setTimeout(() => this.renew(), delay);
  }

  private renew(): void {
    this.clearTimer();
    this.sessionStatus.set('refreshing');
    this.auth.refresh().subscribe({
      // The re-arm rides on the effect: a success updates expiresAt, which re-runs schedule.
      // A failure leaves expiresAt alone, so nothing re-arms and a dead session cannot spin.
      next: () => this.sessionStatus.set('active'),
      error: () => this.sessionStatus.set('expired'),
    });
  }

  private isDue(expiresAt: string | null): boolean {
    return expiresAt !== null && Date.parse(expiresAt) - Date.now() <= REFRESH_BUFFER_MS;
  }

  private clearTimer(): void {
    if (this.timer === null) return;
    clearTimeout(this.timer);
    this.timer = null;
  }
}
