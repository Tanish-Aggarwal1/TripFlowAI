import { Component, HostListener, inject, ChangeDetectionStrategy } from '@angular/core';
import {
  AlertController,
  IonApp,
  IonButton,
  IonButtons,
  IonRouterOutlet,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { AuthService } from './core/services/auth.service';
import { SessionStateService } from './core/services/session-state.service';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [IonApp, IonButton, IonButtons, IonRouterOutlet, IonTitle, IonToolbar],
})
export class AppComponent {
  private readonly sessionState = inject(SessionStateService);
  private readonly auth = inject(AuthService);
  private readonly alertCtrl = inject(AlertController);

  // Stops rapid clicking from stacking dialogs, and stops the banner's own button —
  // whose click bubbles to document — from also triggering one.
  private expiryHandled = false;

  readonly sessionStatus = this.sessionState.status;

  // D-06: the banner is passive. The first thing the user tries to do after expiry is what
  // escalates to a blocking dialog.
  @HostListener('document:click')
  async onDocumentClick(): Promise<void> {
    if (this.sessionStatus() !== 'expired') {
      this.expiryHandled = false;
      return;
    }
    if (this.expiryHandled) return;
    this.expiryHandled = true;

    const alert = await this.alertCtrl.create({
      header: 'Session expired',
      message: 'Your session has expired. Please log in again to continue.',
      backdropDismiss: false,
      buttons: [{ text: 'Log in', handler: () => this.auth.logout() }],
    });
    await alert.present();
    await alert.onDidDismiss();
    this.expiryHandled = false;
  }

  // logout() owns the storage clear and the navigation; don't duplicate either here.
  logIn(): void {
    this.expiryHandled = true;
    this.auth.logout();
  }
}
