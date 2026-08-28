import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IonItem, IonInput, IonButton, IonIcon, IonChip } from '@ionic/angular';
import { addIcons } from 'ionicons';
import { closeCircle } from 'ionicons/icons';
import { MAX_INTEREST_LENGTH, MAX_INTERESTS } from '../../../../core/models/trip.model';

// Shared by ai-trip-prompt and ai-preferences-form (SCRUM-442) — both collect the
// same interest-tag list against the same backend @Size constraints.
@Component({
  selector: 'app-interest-chips',
  templateUrl: 'interest-chips.component.html',
  styleUrls: ['interest-chips.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IonItem, IonInput, IonButton, IonIcon, IonChip],
})
export class InterestChipsComponent {
  @Input() label = 'Interests';
  @Input() interests: string[] = [];
  @Output() interestsChange = new EventEmitter<string[]>();

  interestInput = '';
  error = '';

  constructor() {
    addIcons({ 'close-circle': closeCircle });
  }

  addInterest(): void {
    const value = this.interestInput.trim();
    if (!value) return;

    if (value.length > MAX_INTEREST_LENGTH) {
      this.error = `Each interest must be at most ${MAX_INTEREST_LENGTH} characters.`;
      return;
    }
    if (this.interests.length >= MAX_INTERESTS) {
      this.error = `At most ${MAX_INTERESTS} interests are allowed.`;
      return;
    }
    if (!this.interests.includes(value)) {
      this.interestsChange.emit([...this.interests, value]);
    }
    this.interestInput = '';
    this.error = '';
  }

  removeInterest(interest: string): void {
    this.interestsChange.emit(this.interests.filter((i) => i !== interest));
  }
}
