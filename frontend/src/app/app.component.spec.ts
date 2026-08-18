import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AlertButton, AlertController } from '@ionic/angular/standalone';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';
import { SessionStateService, SessionStatus } from './core/services/session-state.service';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let status: ReturnType<typeof signal<SessionStatus>>;
  let authSpy: jasmine.SpyObj<AuthService>;
  let alertCtrlSpy: jasmine.SpyObj<AlertController>;
  let dismiss: () => void;

  beforeEach(async () => {
    status = signal<SessionStatus>('active');
    authSpy = jasmine.createSpyObj('AuthService', ['logout']);
    alertCtrlSpy = jasmine.createSpyObj('AlertController', ['create']);
    // Resolved synchronously as create() is called, so a test can dismiss the alert without
    // having to guess how many microtasks deep onDidDismiss() is awaited.
    alertCtrlSpy.create.and.callFake(() => {
      const dismissed = new Promise<void>((resolve) => (dismiss = resolve));
      return Promise.resolve({
        present: () => Promise.resolve(),
        onDidDismiss: () => dismissed,
      } as unknown as HTMLIonAlertElement);
    });

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SessionStateService, useValue: { status, markExpired: () => {} } },
        { provide: AuthService, useValue: authSpy },
        { provide: AlertController, useValue: alertCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
  });

  function bannerText(): string | undefined {
    return fixture.nativeElement.querySelector('ion-toolbar')?.textContent;
  }

  it('should create the app', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows no banner while the session is healthy', () => {
    expect(bannerText()).toBeUndefined();
  });

  it('shows the inline banner once the session expires (D-06)', () => {
    status.set('expired');
    fixture.detectChanges();

    expect(bannerText()).toContain('session has expired');
  });

  it('does not open a dialog while the session is healthy', async () => {
    await fixture.componentInstance.onDocumentClick();

    expect(alertCtrlSpy.create).not.toHaveBeenCalled();
  });

  it('opens exactly one dialog no matter how fast the user clicks', async () => {
    status.set('expired');

    const clicks = [
      fixture.componentInstance.onDocumentClick(),
      fixture.componentInstance.onDocumentClick(),
      fixture.componentInstance.onDocumentClick(),
    ];
    dismiss();
    await Promise.all(clicks);

    expect(alertCtrlSpy.create).toHaveBeenCalledTimes(1);
  });

  it('logs out through AuthService when the dialog action is taken', async () => {
    status.set('expired');
    const click = fixture.componentInstance.onDocumentClick();

    const buttons = alertCtrlSpy.create.calls.mostRecent().args[0]?.buttons as AlertButton[];
    buttons[0].handler?.(undefined);
    dismiss();
    await click;

    expect(authSpy.logout).toHaveBeenCalled();
  });

  it('the banner clears and the dialog stops re-opening once logout resets the status', async () => {
    // Mirrors the real wiring: AuthService.clearSession() calls SessionStateService.reset().
    authSpy.logout.and.callFake(() => status.set('active'));
    status.set('expired');

    const click = fixture.componentInstance.onDocumentClick();
    const buttons = alertCtrlSpy.create.calls.mostRecent().args[0]?.buttons as AlertButton[];
    buttons[0].handler?.(undefined);
    dismiss();
    await click;
    fixture.detectChanges();

    expect(bannerText()).toBeUndefined();
    await fixture.componentInstance.onDocumentClick();
    expect(alertCtrlSpy.create).toHaveBeenCalledTimes(1);
  });

  it('acting on the banner logs out without also raising the dialog', async () => {
    status.set('expired');
    fixture.componentInstance.logIn();
    await fixture.componentInstance.onDocumentClick();

    expect(authSpy.logout).toHaveBeenCalledTimes(1);
    expect(alertCtrlSpy.create).not.toHaveBeenCalled();
  });
});
