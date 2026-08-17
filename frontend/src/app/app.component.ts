import { Component, inject } from '@angular/core';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';
import { SessionStateService } from './core/services/session-state.service';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  imports: [IonApp, IonRouterOutlet],
})
export class AppComponent {
  // Injected for its side effect: a root service nothing injects is never constructed,
  // and the silent-refresh timer would be dead code.
  private readonly sessionState = inject(SessionStateService);
}
