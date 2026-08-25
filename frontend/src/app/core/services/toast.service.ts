import { inject, Injectable } from '@angular/core';
import { ToastController } from '@ionic/angular';

// SCRUM-260: the "clear busy flag, build a toast from err.message ?? fallback,
// present it" pattern was duplicated across trip-view.page.ts and half a dozen
// components (ai-trip-prompt, ai-preferences-form, ai-suggestion-cards,
// stop-photo-upload, stop-photo-gallery, edit-stop-form). Centralizing the
// error-toast half here; the busy-flag reset stays inline per call site since
// that part is caller-specific.
@Injectable({ providedIn: 'root' })
export class ToastService {
  private toastCtrl = inject(ToastController);

  async showError(err: unknown, fallback: string, duration = 2500): Promise<void> {
    const toast = await this.toastCtrl.create({
      message: (err as { message?: string })?.message ?? fallback,
      duration,
      color: 'danger',
    });
    await toast.present();
  }

  async showSuccess(message: string, duration = 2000): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      duration,
      color: 'success',
    });
    await toast.present();
  }
}
