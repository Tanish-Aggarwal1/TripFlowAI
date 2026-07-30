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
    const mockSummaries = [
      { id: 1, title: 'Trip 1', visibility: 'PUBLIC' as const, status: 'DRAFT' as const, createdAt: '2026-07-22T00:00:00Z', updatedAt: '2026-07-22T00:00:00Z', stopCount: 0, coverPhotoUrl: null },
    ];
    const mockPage = {
      content: mockSummaries,
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    };

    service.listTrips().subscribe((page) => {
      expect(page).toEqual(mockPage);
      expect(service.trips()).toEqual(mockSummaries);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
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

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
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

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
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

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
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

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
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
      startDate: null,
    };

    service.trips.set([]);
    service.createTrip({ title: 'New Trip', description: undefined, tags: undefined, visibility: 'PUBLIC', stops: [] }).subscribe((trip) => {
      expect(trip).toEqual(newTrip);
      expect(service.trips()).toContain(jasmine.objectContaining({ id: 2, title: 'New Trip', stopCount: 0 }));
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    expect(req.request.method).toBe('POST');
    req.flush(newTrip);
  });

  it('should suggest an itinerary', (done) => {
    const mockResponse = {
      tripId: 50,
      summary: 'A 3-day cultural and culinary tour of Toronto.',
      stops: [
        {
          order: 1,
          name: 'St. Lawrence Market',
          latitude: 43.6487,
          longitude: -79.3715,
          reason: 'Fits your interest in food and history.',
        },
      ],
    };

    service
      .suggestItinerary(50, { interests: ['history', 'food'], budget: 'moderate', pace: 'relaxed' })
      .subscribe((result) => {
        expect(result).toEqual(mockResponse);
        done();
      });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/50/ai-suggest');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ interests: ['history', 'food'], budget: 'moderate', pace: 'relaxed' });
    req.flush(mockResponse);
  });

  it('should handle 502 error from suggestItinerary', (done) => {
    service.suggestItinerary(50, {}).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('AI itinerary service is temporarily unavailable');
        expect(error.status).toBe(502);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips/50/ai-suggest');
    req.flush(
      { message: 'AI itinerary service is temporarily unavailable' },
      { status: 502, statusText: 'Bad Gateway' }
    );
  });

  it('should add a stop via the nested stop-create endpoint', (done) => {
    const mockStop = {
      id: 9,
      name: 'St. Lawrence Market',
      latitude: 43.6487,
      longitude: -79.3715,
      address: null,
      stopOrder: 1,
      status: 'PLANNED' as const,
      notes: 'Fits your interest in food and history.',
      dayNumber: null,
      plannedTime: null,
      stopType: 'SIGHTSEEING' as const,
    };

    service
      .addStop(50, {
        name: 'St. Lawrence Market',
        latitude: 43.6487,
        longitude: -79.3715,
        notes: 'Fits your interest in food and history.',
      })
      .subscribe((result) => {
        expect(result).toEqual(mockStop);
        done();
      });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/50/stops');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'St. Lawrence Market',
      latitude: 43.6487,
      longitude: -79.3715,
      notes: 'Fits your interest in food and history.',
    });
    req.flush(mockStop);
  });

  it('should handle error from addStop', (done) => {
    service.addStop(50, { name: 'X', latitude: 0, longitude: 0 }).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(404);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips/50/stops');
    req.flush({ message: 'Trip not found.' }, { status: 404, statusText: 'Not Found' });
  });

  it('should update a stop via the nested stop-update endpoint', (done) => {
    const mockStop = {
      id: 9,
      name: 'St. Lawrence Market',
      latitude: 43.6487,
      longitude: -79.3715,
      address: '93 Front St E',
      stopOrder: 1,
      status: 'VISITED' as const,
      notes: 'Went early to beat the crowds.',
      dayNumber: null,
      plannedTime: null,
      stopType: 'SIGHTSEEING' as const,
    };

    service
      .updateStop(50, 9, {
        name: 'St. Lawrence Market',
        latitude: 43.6487,
        longitude: -79.3715,
        address: '93 Front St E',
        notes: 'Went early to beat the crowds.',
        status: 'VISITED',
      })
      .subscribe((result) => {
        expect(result).toEqual(mockStop);
        done();
      });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/50/stops/9');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      name: 'St. Lawrence Market',
      latitude: 43.6487,
      longitude: -79.3715,
      address: '93 Front St E',
      notes: 'Went early to beat the crowds.',
      status: 'VISITED',
    });
    req.flush(mockStop);
  });

  it('should handle error from updateStop', (done) => {
    service.updateStop(50, 9, { name: 'X', latitude: 0, longitude: 0 }).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(403);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips/50/stops/9');
    req.flush(
      { message: 'You do not have permission to do that.' },
      { status: 403, statusText: 'Forbidden' },
    );
  });

  it('should fetch a single trip', (done) => {
    const mockTrip = {
      id: 5,
      title: 'Trip 5',
      description: null,
      tags: [],
      visibility: 'PUBLIC' as const,
      status: 'DRAFT' as const,
      ownerId: 1,
      stops: [],
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
      routeGeometry: null,
      startDate: null,
    };

    service.getTrip(5).subscribe((trip) => {
      expect(trip).toEqual(mockTrip);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/5');
    expect(req.request.method).toBe('GET');
    req.flush(mockTrip);
  });

  it('should update a trip and sync the signal', (done) => {
    const existingSummary = { id: 3, title: 'Old title', visibility: 'PUBLIC' as const, status: 'DRAFT' as const, createdAt: '2026-07-22T00:00:00Z', updatedAt: '2026-07-22T00:00:00Z', stopCount: 0, coverPhotoUrl: null };
    const updatedTrip = {
      id: 3,
      title: 'New title',
      description: null,
      tags: [],
      visibility: 'PUBLIC' as const,
      status: 'DRAFT' as const,
      ownerId: 1,
      stops: [],
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-23T00:00:00Z',
      routeGeometry: null,
      startDate: null,
    };

    service.trips.set([existingSummary]);
    service.updateTrip(3, { title: 'New title', visibility: 'PUBLIC', stops: [] }).subscribe((trip) => {
      expect(trip).toEqual(updatedTrip);
      expect(service.trips()).toEqual([jasmine.objectContaining({ id: 3, title: 'New title' })]);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/3');
    expect(req.request.method).toBe('PUT');
    req.flush(updatedTrip);
  });

  it('should delete a trip and remove it from the signal', (done) => {
    const summary = { id: 7, title: 'Trip 7', visibility: 'PUBLIC' as const, status: 'DRAFT' as const, createdAt: '2026-07-22T00:00:00Z', updatedAt: '2026-07-22T00:00:00Z', stopCount: 0, coverPhotoUrl: null };

    service.trips.set([summary]);
    service.deleteTrip(7).subscribe(() => {
      expect(service.trips()).toEqual([]);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should optimize a trip and sync the signal', (done) => {
    const existingSummary = { id: 9, title: 'Trip 9', visibility: 'PUBLIC' as const, status: 'DRAFT' as const, createdAt: '2026-07-22T00:00:00Z', updatedAt: '2026-07-22T00:00:00Z', stopCount: 0, coverPhotoUrl: null };
    const optimizedTrip = {
      id: 9,
      title: 'Trip 9',
      description: null,
      tags: [],
      visibility: 'PUBLIC' as const,
      status: 'DRAFT' as const,
      ownerId: 1,
      stops: [],
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-23T00:00:00Z',
      routeGeometry: '{"type":"LineString","coordinates":[]}',
      startDate: null,
    };

    service.trips.set([existingSummary]);
    service.optimizeTrip(9).subscribe((trip) => {
      expect(trip).toEqual(optimizedTrip);
      expect(service.trips()).toEqual([jasmine.objectContaining({ id: 9 })]);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/9/optimize');
    expect(req.request.method).toBe('POST');
    req.flush(optimizedTrip);
  });

  it('should export a trip calendar as a blob', (done) => {
    const mockBlob = new Blob(['BEGIN:VCALENDAR'], { type: 'text/calendar' });

    service.exportIcs(11).subscribe((blob) => {
      expect(blob).toEqual(mockBlob);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/11/calendar.ics');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(mockBlob);
  });
});