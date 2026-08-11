import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { StopPhotoService } from './stop-photo.service';
import { PhotoSignatureResponse, StopPhotoResponse } from '../models/trip.model';

describe('StopPhotoService', () => {
  let service: StopPhotoService;
  let httpMock: HttpTestingController;

  const signature: PhotoSignatureResponse = {
    cloudName: 'tripflow',
    apiKey: 'key123',
    timestamp: 1700000000,
    signature: 'sig-abc',
    uploadParams: { folder: 'stops/9' },
  };

  const photo: StopPhotoResponse = {
    id: 1,
    stopId: 9,
    url: 'https://res.cloudinary.com/tripflow/image/upload/v1/stops/9/abc.jpg',
    cloudinaryPublicId: 'stops/9/abc',
    caption: 'Great view',
    createdAt: '2026-08-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StopPhotoService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StopPhotoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listPhotos GETs /api/stops/{id}/photos', () => {
    service.listPhotos(9).subscribe((res) => expect(res).toEqual([photo]));
    const req = httpMock.expectOne('http://localhost:8080/api/stops/9/photos');
    expect(req.request.method).toBe('GET');
    req.flush([photo]);
  });

  it('addPhoto POSTs the persist request', () => {
    const request = { url: photo.url, cloudinaryPublicId: photo.cloudinaryPublicId!, caption: 'Great view' };
    service.addPhoto(9, request).subscribe((res) => expect(res).toEqual(photo));
    const req = httpMock.expectOne('http://localhost:8080/api/stops/9/photos');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(photo);
  });

  it('deletePhoto DELETEs the photo by id', () => {
    service.deletePhoto(9, 1).subscribe();
    const req = httpMock.expectOne('http://localhost:8080/api/stops/9/photos/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('uploadPhoto chains signature -> cloudinary -> persist and emits done last', (doneFn) => {
    const file = new File(['bytes'], 'photo.jpg', { type: 'image/jpeg' });
    const emissions: unknown[] = [];

    service.uploadPhoto(9, file, 'Great view').subscribe({
      next: (e) => emissions.push(e),
      complete: () => {
        expect(emissions.some((e: any) => 'done' in e && e.done.id === 1)).toBeTrue();
        doneFn();
      },
    });

    const sigReq = httpMock.expectOne('http://localhost:8080/api/stops/9/photo-signature');
    expect(sigReq.request.method).toBe('POST');
    sigReq.flush(signature);

    const cloudinaryReq = httpMock.expectOne('https://api.cloudinary.com/v1_1/tripflow/image/upload');
    expect(cloudinaryReq.request.method).toBe('POST');
    cloudinaryReq.flush({ secure_url: photo.url, public_id: photo.cloudinaryPublicId });

    const persistReq = httpMock.expectOne('http://localhost:8080/api/stops/9/photos');
    expect(persistReq.request.method).toBe('POST');
    expect(persistReq.request.body).toEqual({
      url: photo.url,
      cloudinaryPublicId: photo.cloudinaryPublicId,
      caption: 'Great view',
    });
    persistReq.flush(photo);
  });

  it('surfaces a friendly error when the Cloudinary upload itself fails', (doneFn) => {
    const file = new File(['bytes'], 'photo.jpg', { type: 'image/jpeg' });

    service.uploadPhoto(9, file).subscribe({
      error: (err) => {
        expect(err.message).toBe('Upload to Cloudinary failed. Check your connection and try again.');
        doneFn();
      },
    });

    httpMock.expectOne('http://localhost:8080/api/stops/9/photo-signature').flush(signature);
    httpMock.expectOne('https://api.cloudinary.com/v1_1/tripflow/image/upload').error(new ProgressEvent('error'));
  });
});