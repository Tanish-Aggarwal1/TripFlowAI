import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SignupPage } from './signup.page';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { FieldError } from '../../../core/models/auth.model';

describe('SignupPage', () => {
  let component: SignupPage;
  let fixture: ComponentFixture<SignupPage>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  const validValue = {
    username: 'testuser',
    email: 'user@example.com',
    password: 'password123',
    confirmPassword: 'password123',
  };

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['register']);

    await TestBed.configureTestingModule({
      imports: [SignupPage],
      providers: [
        provideHttpClient(withXhr()),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SignupPage);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the signup form fields and a submit button', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('ion-input').length).toBe(4);
    expect(compiled.textContent).toContain('Create Account');
  });

  describe('passwordsMatchValidator / passwordMismatch', () => {
    it('flags the form invalid when passwords differ', () => {
      component.form.setValue({ ...validValue, confirmPassword: 'different' });
      expect(component.form.hasError('passwordMismatch')).toBeTrue();
    });

    it('does not flag the form when passwords match', () => {
      component.form.setValue(validValue);
      expect(component.form.hasError('passwordMismatch')).toBeFalse();
    });

    it('passwordMismatch is false until confirmPassword is touched', () => {
      component.form.setValue({ ...validValue, confirmPassword: 'different' });
      expect(component.passwordMismatch).toBeFalse();
    });

    it('passwordMismatch is true once confirmPassword is touched and passwords differ', () => {
      component.form.setValue({ ...validValue, confirmPassword: 'different' });
      component.form.get('confirmPassword')?.markAsTouched();
      expect(component.passwordMismatch).toBeTrue();
    });
  });

  describe('onSubmit', () => {
    it('marks all fields as touched and does not call the service when the form is invalid', () => {
      component.onSubmit();

      expect(authService.register).not.toHaveBeenCalled();
      expect(component.form.get('username')?.touched).toBeTrue();
    });

    it('navigates to /dashboard on successful registration', () => {
      authService.register.and.returnValue(of({
        token: 't', tokenType: 'Bearer', userId: 1, username: 'testuser', expiresAt: '2026-07-28T00:00:00Z',
      }));
      spyOn(router, 'navigate');

      component.form.setValue(validValue);
      component.onSubmit();

      expect(authService.register).toHaveBeenCalledWith({
        username: 'testuser', email: 'user@example.com', password: 'password123',
      });
      expect(component.isSubmitting).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('applies server field errors to the matching controls', () => {
      const fieldErrors: FieldError[] = [{ field: 'email', message: 'Email already registered.' }];
      const err = Object.assign(new Error('Please fix the errors below.'), { fieldErrors });
      authService.register.and.returnValue(throwError(() => err));

      component.form.setValue(validValue);
      component.onSubmit();

      expect(component.isSubmitting).toBeFalse();
      expect(component.form.get('email')?.errors?.['server']).toBe('Email already registered.');
    });

    it('falls back to generalError for a field error with no matching control', () => {
      const fieldErrors: FieldError[] = [{ field: 'nonexistent', message: 'Unknown field issue.' }];
      const err = Object.assign(new Error('Please fix the errors below.'), { fieldErrors });
      authService.register.and.returnValue(throwError(() => err));

      component.form.setValue(validValue);
      component.onSubmit();

      expect(component.generalError).toBe('Unknown field issue.');
    });

    it('sets generalError from the error message when there are no fieldErrors', () => {
      authService.register.and.returnValue(throwError(() => new Error('Email already registered.')));

      component.form.setValue(validValue);
      component.onSubmit();

      expect(component.generalError).toBe('Email already registered.');
    });
  });

  describe('fieldError', () => {
    it('returns null for an unknown control', () => {
      expect(component.fieldError('doesNotExist')).toBeNull();
    });

    it('returns null when the control has not been touched', () => {
      expect(component.fieldError('username')).toBeNull();
    });

    it('returns a required message once touched and empty', () => {
      const control = component.form.get('username');
      control?.markAsTouched();
      control?.setValue('');
      expect(component.fieldError('username')).toBe('This field is required.');
    });

    it('returns an email format message for an invalid address', () => {
      const control = component.form.get('email');
      control?.markAsTouched();
      control?.setValue('not-an-email');
      expect(component.fieldError('email')).toBe('Enter a valid email address.');
    });

    it('returns a minlength message with the required length', () => {
      const control = component.form.get('password');
      control?.markAsTouched();
      control?.setValue('short');
      expect(component.fieldError('password')).toBe('Minimum 8 characters.');
    });

    it('returns a server error message when present', () => {
      const control = component.form.get('email');
      control?.markAsTouched();
      control?.setErrors({ server: 'Email already registered.' });
      expect(component.fieldError('email')).toBe('Email already registered.');
    });

    it('returns null once the control is touched and valid', () => {
      const control = component.form.get('username');
      control?.markAsTouched();
      control?.setValue('validusername');
      expect(component.fieldError('username')).toBeNull();
    });
  });
});
