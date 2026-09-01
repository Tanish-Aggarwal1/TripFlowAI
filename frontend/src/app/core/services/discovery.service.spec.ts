import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { DiscoveryService } from './discovery.service';
import { FeedTrip } from '../models/feed.model';

describe('DiscoveryService', () => {
  let service: DiscoveryService;
  let httpMock: HttpTestingController;

  const mockFeedTrip: FeedTrip = {
    id: 1,
    title: 'Ottawa Weekend',
    description: 'A cozy fall trip',
    tags: ['fall', 'food'],
    ownerUsername: 'jsmith',
    likeCount: 3,
    createdAt: '2026-08-30T00:00:00Z',
    stops: [
      {
        id: 10,
        name: 'Byward Market',
        address: '55 ByWard Market Sq',
        stopOrder: 1,
        notes: 'Try the beavertails',
        photoUrls: [],
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DiscoveryService, provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    service = TestBed.inject(DiscoveryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should request the feed with default page and size params', (done) => {
    const mockPage = {
      content: [mockFeedTrip],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    };

    service.getFeed().subscribe((page) => {
      expect(page).toEqual(mockPage);
      done();
    });

    const req = httpMock.expectOne('http://localhost:8080/api/discovery/feed?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should request a specific page and size', (done) => {
    const mockPage = { content: [], page: { size: 5, number: 2, totalElements: 0, totalPages: 0 } };

    service.getFeed(2, 5).subscribe(() => done());

    const req = httpMock.expectOne('http://localhost:8080/api/discovery/feed?page=2&size=5');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should map a 401 response to a rejected observable, not a swallowed empty page', (done) => {
    service.getFeed().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('You must be signed in to view the feed.');
        expect(error.status).toBe(401);
        done();
      },
    );

    const req = httpMock.expectOne('http://localhost:8080/api/discovery/feed?page=0&size=20');
    req.flush({ message: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });
  });
});
