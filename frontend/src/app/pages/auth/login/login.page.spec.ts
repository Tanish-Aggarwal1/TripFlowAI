import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoginPage } from './login.page';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { FieldError } from '../../../core/models/auth.model';

describe('LoginPage', () => {
  let component: LoginPage;
  let fixture: ComponentFixture<LoginPage>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideHttpClient(withXhr()),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginPage);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders email/password inputs and a submit button', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelectorAll('ion-input').length).toBe(2);
    expect(compiled.textContent).toContain('Log In');
  });

  describe('onSubmit', () => {
    it('marks all fields as touched and does not call the service when the form is invalid', () => {
      component.onSubmit();

      expect(authService.login).not.toHaveBeenCalled();
      expect(component.form.get('email')?.touched).toBeTrue();
      expect(component.form.get('password')?.touched).toBeTrue();
    });

    it('navigates to /dashboard on successful login', () => {
      authService.login.and.returnValue(of({
        token: 't', tokenType: 'Bearer', userId: 1, username: 'user', expiresAt: '2026-07-28T00:00:00Z',
      }));
      spyOn(router, 'navigate');

      component.form.setValue({ email: 'user@example.com', password: 'password123' });
      component.onSubmit();

      expect(authService.login).toHaveBeenCalledWith({ email: 'user@example.com', password: 'password123' });
      expect(component.isSubmitting).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('sets generalError and stops submitting on login failure', () => {
      authService.login.and.returnValue(throwError(() => new Error('Invalid credentials.')));

      component.form.setValue({ email: 'user@example.com', password: 'wrong' });
      component.onSubmit();

      expect(component.isSubmitting).toBeFalse();
      expect(component.generalError).toBe('Invalid credentials.');
    });

    it('applies server field errors to the matching controls', () => {
      const fieldErrors: FieldError[] = [{ field: 'email', message: 'Malformed email.' }];
      const err = Object.assign(new Error('Please fix the errors below.'), { fieldErrors });
      authService.login.and.returnValue(throwError(() => err));

      component.form.setValue({ email: 'user@example.com', password: 'password123' });
      component.onSubmit();

      expect(component.isSubmitting).toBeFalse();
      expect(component.form.get('email')?.errors?.['server']).toBe('Malformed email.');
    });

    it('falls back to generalError for a field error with no matching control', () => {
      const fieldErrors: FieldError[] = [{ field: 'nonexistent', message: 'Unknown field issue.' }];
      const err = Object.assign(new Error('Please fix the errors below.'), { fieldErrors });
      authService.login.and.returnValue(throwError(() => err));

      component.form.setValue({ email: 'user@example.com', password: 'password123' });
      component.onSubmit();

      expect(component.generalError).toBe('Unknown field issue.');
    });
  });

  describe('fieldError', () => {
    it('returns null for an unknown control', () => {
      expect(component.fieldError('doesNotExist')).toBeNull();
    });

    it('returns null when the control has not been touched', () => {
      component.form.get('email')?.setValue('');
      expect(component.fieldError('email')).toBeNull();
    });

    it('returns a required message once touched and empty', () => {
      const control = component.form.get('email');
      control?.markAsTouched();
      control?.setValue('');
      expect(component.fieldError('email')).toBe('This field is required.');
    });

    it('returns an email format message for an invalid address', () => {
      const control = component.form.get('email');
      control?.markAsTouched();
      control?.setValue('not-an-email');
      expect(component.fieldError('email')).toBe('Enter a valid email address.');
    });

    it('returns null once the control is touched and valid', () => {
      const control = component.form.get('password');
      control?.markAsTouched();
      control?.setValue('password123');
      expect(component.fieldError('password')).toBeNull();
    });

    it('returns a server error message when present', () => {
      const control = component.form.get('email');
      control?.markAsTouched();
      control?.setErrors({ server: 'Malformed email.' });
      expect(component.fieldError('email')).toBe('Malformed email.');
    });
  });
});
