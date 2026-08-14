import { signal } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { RefreshResponse } from '../models/auth.model';
import { AuthService } from './auth.service';
import { SessionStateService } from './session-state.service';

describe('SessionStateService', () => {
  let expiresAt: ReturnType<typeof signal<string | null>>;
  let refresh: jasmine.Spy<() => Observable<RefreshResponse>>;

  const MINUTE = 60_000;

  function expiryIn(minutes: number): string {
    return new Date(Date.now() + minutes * MINUTE).toISOString();
  }

  function refreshResponse(): RefreshResponse {
    return { token: 'new-token', tokenType: 'Bearer', expiresAt: expiryIn(15) };
  }

  /** Publish a new expiry and let the effect that arms the timer run. */
  function publishExpiry(value: string | null): void {
    expiresAt.set(value);
    TestBed.tick();
  }

  beforeEach(() => {
    expiresAt = signal<string | null>(null);
    refresh = jasmine.createSpy('refresh').and.returnValue(of(refreshResponse()));

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { expiresAt, refresh } }],
    });
  });

  it('does not refresh early, and refreshes exactly once at the buffer point', fakeAsync(() => {
    TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(15));

    tick(13 * MINUTE);
    expect(refresh).not.toHaveBeenCalled();

    tick(MINUTE);
    expect(refresh).toHaveBeenCalledTimes(1);

    publishExpiry(null);
  }));

  it('re-arms after a successful refresh, so the loop sustains rather than firing once', fakeAsync(() => {
    refresh.and.callFake(() => {
      const res = refreshResponse();
      expiresAt.set(res.expiresAt);
      return of(res);
    });
    TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(15));

    tick(14 * MINUTE);
    expect(refresh).toHaveBeenCalledTimes(1);

    TestBed.tick();
    tick(14 * MINUTE);
    expect(refresh).toHaveBeenCalledTimes(2);

    publishExpiry(null);
  }));

  it('refreshes immediately when the stored expiry is already in the past', fakeAsync(() => {
    TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(-5));

    tick(0);
    expect(refresh).toHaveBeenCalledTimes(1);

    publishExpiry(null);
  }));

  it('marks the session expired on a failed refresh and does not re-arm', fakeAsync(() => {
    refresh.and.returnValue(throwError(() => ({ status: 401 })));
    const service = TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(15));

    tick(14 * MINUTE);
    expect(service.status()).toBe('expired');

    tick(60 * MINUTE);
    expect(refresh).toHaveBeenCalledTimes(1);
  }));

  it('leaves exactly one live timer when the schedule is replaced', fakeAsync(() => {
    TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(15));
    publishExpiry(expiryIn(20));

    tick(19 * MINUTE);
    expect(refresh).toHaveBeenCalledTimes(1);

    publishExpiry(null);
  }));

  it('refreshes when the tab becomes visible again past the scheduled point', fakeAsync(() => {
    spyOnProperty(document, 'visibilityState').and.returnValue('visible');
    TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(0.5));

    document.dispatchEvent(new Event('visibilitychange'));
    expect(refresh).toHaveBeenCalledTimes(1);

    publishExpiry(null);
  }));

  it('ignores a visibility change while the token is still comfortably valid', fakeAsync(() => {
    spyOnProperty(document, 'visibilityState').and.returnValue('visible');
    TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(15));

    document.dispatchEvent(new Event('visibilitychange'));
    expect(refresh).not.toHaveBeenCalled();

    publishExpiry(null);
  }));

  it('markExpired flips the status and stops the timer, for the 401 interceptor to call', fakeAsync(() => {
    const service = TestBed.inject(SessionStateService);
    publishExpiry(expiryIn(15));

    service.markExpired();
    expect(service.status()).toBe('expired');

    tick(60 * MINUTE);
    expect(refresh).not.toHaveBeenCalled();
  }));

  it('returns to active when a fresh login publishes a new expiry', fakeAsync(() => {
    const service = TestBed.inject(SessionStateService);
    service.markExpired();

    publishExpiry(expiryIn(15));
    expect(service.status()).toBe('active');

    publishExpiry(null);
  }));
});
