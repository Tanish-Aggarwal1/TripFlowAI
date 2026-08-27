import { ChangeDetectorRef, Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IonContent, IonItem, IonInput, IonButton, IonText } from '@ionic/angular';
import { AuthService } from '../../../core/services/auth.service';

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
      error: (err: Error) => {
        this.isSubmitting = false;
        // UC-02: same generic message regardless of which field was wrong
        this.generalError = err.message;
        this.cdr.markForCheck();
      },
    });
  }

  fieldError(name: string): string | null {
    const control = this.form.get(name);
    if (!control || !control.touched || !control.errors) return null;

    if (control.errors['required']) return 'This field is required.';
    if (control.errors['email']) return 'Enter a valid email address.';
    return null;
  }
}