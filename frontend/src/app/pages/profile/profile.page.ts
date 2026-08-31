import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonSpinner } from '@ionic/angular';
import { ProfileService } from '../../core/services/profile.service';
import { ToastService } from '../../core/services/toast.service';
import { Profile } from '../../core/models/profile.model';
import { InterestChipsComponent } from '../trips/components/interest-chips/interest-chips.component';

// SOCIAL-05 (D-07): matches Trip.tags's backend limit (docs/api-contracts.md), not the
// AI-prompt callers' 10-interest cap that InterestChipsComponent defaults to.
const PROFILE_MAX_INTERESTS = 20;

@Component({
  selector: 'app-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IonHeader, IonToolbar, IonTitle, IonContent, IonButton, IonSpinner, InterestChipsComponent, DatePipe],
  templateUrl: 'profile.page.html',
  styleUrls: ['profile.page.scss'],
})
export class ProfilePage implements OnInit {
  private profileService = inject(ProfileService);
  private toastService = inject(ToastService);

  readonly maxInterests = PROFILE_MAX_INTERESTS;

  profile = signal<Profile | null>(null);
  loading = signal(true);
  saving = signal(false);
  error = signal<string | null>(null);

  // Local draft: InterestChipsComponent pushes/splices this array locally, and only
  // save() sends the whole resulting array, matching the endpoint's replace-wholesale
  // semantics (never a delta).
  draftInterests = signal<string[]>([]);

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.profileService.getProfile().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.draftInterests.set(profile.interests);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message ?? 'Could not load your profile.');
        this.loading.set(false);
      },
    });
  }

  onInterestsChange(interests: string[]): void {
    this.draftInterests.set(interests);
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.profileService.updateInterests(this.draftInterests()).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.draftInterests.set(profile.interests);
        this.saving.set(false);
        this.toastService.showSuccess('Interests updated.');
      },
      error: (err) => {
        // Render the field-specific message when present (the 20-element/50-character
        // limits), and revert the draft to the previously-saved interests — a rejected
        // save must not leave an unsaved, invalid draft rendered as if it stuck.
        this.error.set(err.fieldErrors?.[0]?.message ?? err.message ?? 'Could not save your interests.');
        this.draftInterests.set(this.profile()?.interests ?? []);
        this.saving.set(false);
      },
    });
  }
}
