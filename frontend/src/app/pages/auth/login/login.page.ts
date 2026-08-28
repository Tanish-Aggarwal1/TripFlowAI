import { ChangeDetectorRef, Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IonContent, IonItem, IonInput, IonButton, IonText } from '@ionic/angular';
import { AuthService } from '../../../core/services/auth.service';
import { FieldError } from '../../../core/models/auth.model';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, IonContent, IonItem, IonInput, IonButton, IonText],
  templateUrl: './login.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./login.page.scss'],
})
export class LoginPage {

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  form: FormGroup;
  isSubmitting = false;
  generalError: string | null = null;

  constructor() {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.generalError = null;
    this.isSubmitting = true;

    const { email, password } = this.form.value;

    this.authService.login({ email, password }).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err: Error & { fieldErrors?: FieldError[] }) => {
        this.isSubmitting = false;
        this.applyServerErrors(err);
        this.cdr.markForCheck();
      },
    });
  }

  // 401 (bad credentials) always carries a plain generalError per UC-02 - no field is
  // singled out. A 400 (e.g. malformed email) can carry fieldErrors, which this attaches
  // to the matching control instead of dropping them into a generic banner.
  private applyServerErrors(err: Error & { fieldErrors?: FieldError[] }): void {
    if (err.fieldErrors?.length) {
      for (const fe of err.fieldErrors) {
        const control = this.form.get(fe.field);
        if (control) {
          control.setErrors({ server: fe.message });
        } else {
          this.generalError = fe.message;
        }
      }
    } else {
      this.generalError = err.message;
    }
  }

  fieldError(name: string): string | null {
    const control = this.form.get(name);
    if (!control || !control.touched || !control.errors) return null;

    if (control.errors['required']) return 'This field is required.';
    if (control.errors['email']) return 'Enter a valid email address.';
    if (control.errors['server']) return control.errors['server'];
    return null;
  }
}