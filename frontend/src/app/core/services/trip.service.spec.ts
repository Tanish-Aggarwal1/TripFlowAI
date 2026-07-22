import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TripService } from './trip.service';
import { HttpErrorResponse } from '@angular/common/http';

describe('TripService', () => {
  let service: TripService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TripService],
    });
    service = TestBed.inject(TripService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should list trips and update signal', (done) => {
    const mockTrips = [
      { id: 1, title: 'Trip 1', description: null, tags: [], visibility: 'PUBLIC' as const, status: 'DRAFT' as const, ownerId: 1, stops: [], createdAt: '2026-07-22T00:00:00Z', updatedAt: '2026-07-22T00:00:00Z', routeGeometry: null },
    ];

    service.listTrips().subscribe((trips) => {
      expect(trips).toEqual(mockTrips);
      expect(service.trips()).toEqual(mockTrips);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    expect(req.request.method).toBe('GET');
    req.flush(mockTrips);
  });

  // ✓ Test error handling with fieldErrors present
  it('should handle error with fieldErrors', (done) => {
    service.listTrips().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('Validation failed');
        expect(error.fieldErrors).toEqual([{ field: 'title', message: 'Title is required' }]);
        expect(error.status).toBe(400);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    const mockErrorResponse = {
      status: 400,
      message: 'Validation failed',
      fieldErrors: [{ field: 'title', message: 'Title is required' }],
    };
    req.flush(mockErrorResponse, { status: 400, statusText: 'Bad Request' });
  });

  // ✓ Test error handling without fieldErrors (tests the ?? null branch)
  it('should handle error without fieldErrors', (done) => {
    service.listTrips().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('Internal server error');
        expect(error.fieldErrors).toBeNull();
        expect(error.status).toBe(500);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    const mockErrorResponse = {
      status: 500,
      message: 'Internal server error',
    };
    req.flush(mockErrorResponse, { status: 500, statusText: 'Internal Server Error' });
  });

  it('should handle network error (status 0)', (done) => {
    service.listTrips().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('Network error. Please check your connection.');
        expect(error.status).toBe(0);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
  });

  it('should handle 403 Forbidden error', (done) => {
    service.listTrips().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('You do not have permission to do that.');
        expect(error.status).toBe(403);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
  });

  it('should handle 404 Not Found error', (done) => {
    service.getTrip(999).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('Trip not found.');
        expect(error.status).toBe(404);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips/999');
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('should create trip and update signal', (done) => {
    const newTrip = {
      id: 2,
      title: 'New Trip',
      description: null,
      tags: [],
      visibility: 'PUBLIC' as const,
      status: 'DRAFT' as const,
      ownerId: 1,
      stops: [],
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
      routeGeometry: null,
    };

    service.trips.set([]);
    service.createTrip({ title: 'New Trip', description: undefined, tags: undefined, visibility: 'PUBLIC', stops: [] }).subscribe((trip) => {
      expect(trip).toEqual(newTrip);
      expect(service.trips()).toContain(newTrip);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    expect(req.request.method).toBe('POST');
    req.flush(newTrip);
  });
});