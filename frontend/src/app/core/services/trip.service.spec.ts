import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TripService } from './trip.service';
import { HttpErrorResponse, provideHttpClient, withXhr } from '@angular/common/http';

describe('TripService', () => {
  let service: TripService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TripService, provideHttpClient(withXhr()), provideHttpClientTesting()],
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

  it('should list trips', (done) => {
    const mockSummaries = [
      { id: 1, title: 'Trip 1', visibility: 'PUBLIC' as const, status: 'DRAFT' as const, createdAt: '2026-07-22T00:00:00Z', updatedAt: '2026-07-22T00:00:00Z', stopCount: 0, coverPhotoUrl: null, visitedStopCount: 0, completionPercentage: 0 },
    ];
    const mockPage = {
      content: mockSummaries,
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    };

    service.listTrips().subscribe((page) => {
      expect(page).toEqual(mockPage);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should leave the plain request URL unchanged for an all-empty filter object', (done) => {
    const mockPage = { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } };

    service.listTrips(0, 20, { search: '  ' }).subscribe(() => done());

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should list trips filtered by a trimmed search term', (done) => {
    const mockPage = { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } };

    service.listTrips(0, 20, { search: '  paris  ' }).subscribe(() => done());

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20&search=paris');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should list trips filtered by status, visibility, date range and duration together', (done) => {
    const mockPage = { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } };

    service
      .listTrips(0, 20, {
        status: 'ACTIVE',
        visibility: 'PUBLIC',
        startDateFrom: '2026-06-01',
        startDateTo: '2026-06-30',
        durationDays: 3,
      })
      .subscribe(() => done());

    const req = httpMock.expectOne(
      'http://localhost:8080/api/trips?page=0&size=20&status=ACTIVE&visibility=PUBLIC&startDateFrom=2026-06-01&startDateTo=2026-06-30&durationDays=3',
    );
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

  it('should create trip', (done) => {
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
      visitedStopCount: 0,
      completionPercentage: 0,
    };

    service.createTrip({ title: 'New Trip', description: undefined, tags: undefined, visibility: 'PUBLIC', stops: [] }).subscribe((trip) => {
      expect(trip).toEqual(newTrip);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips');
    expect(req.request.method).toBe('POST');
    req.flush(newTrip);
  });

  it('should generate a trip with AI', (done) => {
    const generatedTrip = {
      id: 4,
      title: 'Kyoto Food Tour',
      description: 'A foodie trip',
      tags: [],
      visibility: 'PRIVATE' as const,
      status: 'DRAFT' as const,
      ownerId: 1,
      stops: [],
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
      routeGeometry: null,
      startDate: null,
      visitedStopCount: 0,
      completionPercentage: 0,
    };

    service.generateTripWithAi({ prompt: '3 days in Kyoto, food and temples' }).subscribe((trip) => {
      expect(trip).toEqual(generatedTrip);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/ai-generate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ prompt: '3 days in Kyoto, food and temples' });
    req.flush(generatedTrip);
  });

  it('should handle a 422 error from generateTripWithAi', (done) => {
    service.generateTripWithAi({ prompt: 'a trip with nothing in it' }).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(422);
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips/ai-generate');
    req.flush(
      { message: 'Gemini did not return any stops for this prompt' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
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
      visitedStopCount: 0,
      completionPercentage: 0,
    };

    service.getTrip(5).subscribe((trip) => {
      expect(trip).toEqual(mockTrip);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/5');
    expect(req.request.method).toBe('GET');
    req.flush(mockTrip);
  });

  it('should update a trip', (done) => {
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
      visitedStopCount: 0,
      completionPercentage: 0,
    };

    service.updateTrip(3, { title: 'New title', visibility: 'PUBLIC', stops: [] }).subscribe((trip) => {
      expect(trip).toEqual(updatedTrip);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/3');
    expect(req.request.method).toBe('PUT');
    req.flush(updatedTrip);
  });

  it('should delete a trip', (done) => {
    service.deleteTrip(7).subscribe(() => {
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should optimize a trip', (done) => {
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
      visitedStopCount: 0,
      completionPercentage: 0,
    };

    service.optimizeTrip(9).subscribe((trip) => {
      expect(trip).toEqual(optimizedTrip);
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

  it('should export a trip PDF as a blob', (done) => {
    const mockBlob = new Blob(['%PDF-'], { type: 'application/pdf' });

    service.exportPdf(11).subscribe((blob) => {
      expect(blob).toEqual(mockBlob);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/11/export/pdf');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(mockBlob);
  });

  // ── Feed actions (SOCIAL-02/03/04, D-04) ────────────────────────────────────

  it('should like a trip via POST', (done) => {
    service.likeTrip(12).subscribe(() => done());

    const req = httpMock.expectOne('http://localhost:8080/api/trips/12/like');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('should map a 404 from likeTrip through the standard error handler', (done) => {
    service.likeTrip(12).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('Trip not found.');
        done();
      },
    );

    httpMock
      .expectOne('http://localhost:8080/api/trips/12/like')
      .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('should unlike a trip via DELETE', (done) => {
    service.unlikeTrip(12).subscribe(() => done());

    const req = httpMock.expectOne('http://localhost:8080/api/trips/12/like');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should map an error from unlikeTrip through the standard error handler', (done) => {
    service.unlikeTrip(12).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(404);
        done();
      },
    );

    httpMock
      .expectOne('http://localhost:8080/api/trips/12/like')
      .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('should save a trip via POST', (done) => {
    service.saveTrip(13).subscribe(() => done());

    const req = httpMock.expectOne('http://localhost:8080/api/trips/13/save');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('should map an error from saveTrip through the standard error handler', (done) => {
    service.saveTrip(13).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(404);
        done();
      },
    );

    httpMock
      .expectOne('http://localhost:8080/api/trips/13/save')
      .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('should unsave a trip via DELETE', (done) => {
    service.unsaveTrip(13).subscribe(() => done());

    const req = httpMock.expectOne('http://localhost:8080/api/trips/13/save');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should map an error from unsaveTrip through the standard error handler', (done) => {
    service.unsaveTrip(13).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(404);
        done();
      },
    );

    httpMock
      .expectOne('http://localhost:8080/api/trips/13/save')
      .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('should clone a trip via POST and resolve with the new TripResponse', (done) => {
    const clonedTrip = {
      id: 14,
      title: 'Copy of Trip 13',
      description: null,
      tags: [],
      visibility: 'PRIVATE' as const,
      status: 'DRAFT' as const,
      ownerId: 1,
      stops: [],
      createdAt: '2026-08-31T00:00:00Z',
      updatedAt: '2026-08-31T00:00:00Z',
      routeGeometry: null,
      startDate: null,
      visitedStopCount: 0,
      completionPercentage: 0,
    };

    service.cloneTrip(13).subscribe((trip) => {
      expect(trip).toEqual(clonedTrip);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/13/clone');
    expect(req.request.method).toBe('POST');
    req.flush(clonedTrip);
  });

  it('should map an error from cloneTrip through the standard error handler', (done) => {
    service.cloneTrip(13).subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(404);
        done();
      },
    );

    httpMock
      .expectOne('http://localhost:8080/api/trips/13/clone')
      .flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('should list saved trips via GET', (done) => {
    const mockPage = { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } };

    service.listSavedTrips().subscribe((page) => {
      expect(page).toEqual(mockPage);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips/saved?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should map an error from listSavedTrips through the standard error handler', (done) => {
    service.listSavedTrips().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.status).toBe(500);
        done();
      },
    );

    httpMock
      .expectOne('http://localhost:8080/api/trips/saved?page=0&size=20')
      .flush({ message: 'Internal server error' }, { status: 500, statusText: 'Internal Server Error' });
  });
});