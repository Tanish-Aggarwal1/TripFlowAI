import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { ProfileService } from './profile.service';
import { Profile } from '../models/profile.model';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  const mockProfile: Profile = {
    id: 1,
    username: 'jsmith',
    joinedAt: '2026-01-01T00:00:00Z',
    interests: ['hiking'],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProfileService, provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('getProfile issues a GET against /profile', (done) => {
    service.getProfile().subscribe((profile) => {
      expect(profile).toEqual(mockProfile);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/profile');
    expect(req.request.method).toBe('GET');
    req.flush(mockProfile);
  });

  it('updateInterests issues a PATCH against /profile/interests with the full array', (done) => {
    const updated: Profile = { ...mockProfile, interests: ['hiking', 'food'] };

    service.updateInterests(['hiking', 'food']).subscribe((profile) => {
      expect(profile).toEqual(updated);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/profile/interests');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ interests: ['hiking', 'food'] });
    req.flush(updated);
  });

  it('maps a getProfile network error to a rejected observable', (done) => {
    service.getProfile().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(0);
        done();
      },
    );

    const req = httpMock.expectOne('http://localhost:8080/api/profile');
    req.error(new ProgressEvent('error'));
  });

  it('maps a 400 validation response to a rejected observable carrying fieldErrors', (done) => {
    service.updateInterests(Array(21).fill('x')).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(400);
        expect(error.fieldErrors).toEqual([{ field: 'interests', message: 'size must be between 0 and 20' }]);
        done();
      },
    );

    const req = httpMock.expectOne('http://localhost:8080/api/profile/interests');
    req.flush(
      {
        message: 'Validation failed',
        fieldErrors: [{ field: 'interests', message: 'size must be between 0 and 20' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
  });
});
