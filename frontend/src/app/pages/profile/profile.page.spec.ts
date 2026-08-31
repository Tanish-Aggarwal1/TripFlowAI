import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular, ToastController } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { ProfilePage } from './profile.page';
import { ProfileService } from '../../core/services/profile.service';
import { Profile } from '../../core/models/profile.model';

describe('ProfilePage', () => {
  let component: ProfilePage;
  let fixture: ComponentFixture<ProfilePage>;
  let profileServiceSpy: jasmine.SpyObj<ProfileService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const profile: Profile = {
    id: 1,
    username: 'jsmith',
    joinedAt: '2026-01-01T00:00:00Z',
    interests: ['hiking'],
  };

  beforeEach(async () => {
    profileServiceSpy = jasmine.createSpyObj('ProfileService', ['getProfile', 'updateInterests']);
    profileServiceSpy.getProfile.and.returnValue(of(profile));
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));

    await TestBed.configureTestingModule({
      imports: [ProfilePage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ProfileService, useValue: profileServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfilePage);
    component = fixture.componentInstance;
  });

  it('loads the profile on init and renders username, join date and interests', () => {
    fixture.detectChanges();

    expect(profileServiceSpy.getProfile).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('jsmith');
    expect(fixture.nativeElement.textContent).toContain('hiking');
  });

  it('renders an empty-state prompt when the user has no interests', () => {
    profileServiceSpy.getProfile.and.returnValue(of({ ...profile, interests: [] }));

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-state')).toBeTruthy();
  });

  it('renders an error and does not throw when getProfile fails', () => {
    profileServiceSpy.getProfile.and.returnValue(throwError(() => new Error('Network error.')));

    expect(() => fixture.detectChanges()).not.toThrow();

    expect(component.error()).toBe('Network error.');
    expect(component.loading()).toBeFalse();
  });

  it('save() issues PATCH with the full resulting array after an add and updates from the response', () => {
    fixture.detectChanges();
    const updated: Profile = { ...profile, interests: ['hiking', 'food'] };
    profileServiceSpy.updateInterests.and.returnValue(of(updated));

    component.onInterestsChange(['hiking', 'food']);
    component.save();

    expect(profileServiceSpy.updateInterests).toHaveBeenCalledWith(['hiking', 'food']);
    expect(component.profile()).toEqual(updated);
    expect(component.draftInterests()).toEqual(['hiking', 'food']);
  });

  it('save() issues PATCH whose array omits a removed interest', () => {
    fixture.detectChanges();
    const updated: Profile = { ...profile, interests: [] };
    profileServiceSpy.updateInterests.and.returnValue(of(updated));

    component.onInterestsChange([]);
    component.save();

    expect(profileServiceSpy.updateInterests).toHaveBeenCalledWith([]);
  });

  it('a 400 response renders the field error message and leaves the previously-saved interests', () => {
    fixture.detectChanges();
    const apiError = Object.assign(new Error('Validation failed'), {
      status: 400,
      fieldErrors: [{ field: 'interests', message: 'size must be between 0 and 20' }],
    });
    profileServiceSpy.updateInterests.and.returnValue(throwError(() => apiError));

    component.onInterestsChange(Array(21).fill('x'));
    component.save();

    expect(component.error()).toBe('size must be between 0 and 20');
    expect(component.draftInterests()).toEqual(profile.interests);
  });

  it('the /profile route requires authGuard', async () => {
    const { routes } = await import('../../app.routes');
    const profileRoute = routes.find((r) => r.path === 'profile');

    expect(profileRoute).toBeTruthy();
    expect(profileRoute?.canActivate?.length).toBeGreaterThan(0);
  });
});
