import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertController, ToastController } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { StopPhotoGalleryComponent } from './stop-photo-gallery.component';
import { StopPhotoService } from '../../../../core/services/stop-photo.service';
import { StopPhotoResponse } from '../../../../core/models/trip.model';

describe('StopPhotoGalleryComponent', () => {
  let component: StopPhotoGalleryComponent;
  let fixture: ComponentFixture<StopPhotoGalleryComponent>;
  let photoServiceSpy: jasmine.SpyObj<StopPhotoService>;
  let alertCtrlSpy: jasmine.SpyObj<AlertController>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const photo1: StopPhotoResponse = {
    id: 1,
    stopId: 9,
    url: 'https://res.cloudinary.com/x/a.jpg',
    cloudinaryPublicId: 'x/a',
    caption: 'Nice',
    createdAt: '2026-08-01T10:00:00Z',
  };
  const photo2: StopPhotoResponse = {
    id: 2,
    stopId: 9,
    url: 'https://res.cloudinary.com/x/b.jpg',
    cloudinaryPublicId: 'x/b',
    caption: null,
    createdAt: '2026-08-02T10:00:00Z',
  };

  beforeEach(async () => {
    photoServiceSpy = jasmine.createSpyObj('StopPhotoService', ['listPhotos', 'deletePhoto']);
    alertCtrlSpy = jasmine.createSpyObj('AlertController', ['create']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));
    photoServiceSpy.listPhotos.and.returnValue(of([photo1, photo2]));

    await TestBed.configureTestingModule({
      imports: [StopPhotoGalleryComponent],
      providers: [
        { provide: StopPhotoService, useValue: photoServiceSpy },
        { provide: AlertController, useValue: alertCtrlSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StopPhotoGalleryComponent);
    component = fixture.componentInstance;
    component.stopId = 9;
    component.ngOnChanges({
      stopId: { currentValue: 9, previousValue: undefined, firstChange: true, isFirstChange: () => true },
    });
    fixture.detectChanges();
  });

  it('should create', () => expect(component).toBeTruthy());

  it('loads photos when stopId first binds', () => {
    expect(photoServiceSpy.listPhotos).toHaveBeenCalledWith(9);
    expect(component.photos).toEqual([photo1, photo2]);
    expect(component.loading).toBeFalse();
  });

  it('reloads photos for the new stop when stopId changes on a live instance', () => {
    const photo3: StopPhotoResponse = { ...photo1, id: 3, stopId: 14 };
    photoServiceSpy.listPhotos.calls.reset();
    photoServiceSpy.listPhotos.and.returnValue(of([photo3]));

    component.stopId = 14;
    component.ngOnChanges({
      stopId: { currentValue: 14, previousValue: 9, firstChange: false, isFirstChange: () => false },
    });

    expect(photoServiceSpy.listPhotos).toHaveBeenCalledWith(14);
    expect(component.photos).toEqual([photo3]);
  });

  it('does not reload photos when an unrelated input change fires', () => {
    photoServiceSpy.listPhotos.calls.reset();

    component.ngOnChanges({});

    expect(photoServiceSpy.listPhotos).not.toHaveBeenCalled();
  });

  it('shows an error and stops loading when listPhotos fails', () => {
    photoServiceSpy.listPhotos.and.returnValue(throwError(() => ({ message: 'Stop not found.' })));

    component.loadPhotos();

    expect(component.error).toBe('Stop not found.');
    expect(component.loading).toBeFalse();
  });

  it('opens the upload form', () => {
    component.openUpload();
    expect(component.showUpload).toBeTrue();
  });

  it('prepends the uploaded photo and closes the upload form', () => {
    const newPhoto: StopPhotoResponse = { ...photo1, id: 3 };
    spyOn(component.photosChanged, 'emit');

    component.onUploaded(newPhoto);

    expect(component.photos[0]).toEqual(newPhoto);
    expect(component.showUpload).toBeFalse();
    expect(component.photosChanged.emit).toHaveBeenCalledWith(component.photos);
  });

  it('closes the upload form on cancel', () => {
    component.showUpload = true;
    component.onUploadCancelled();
    expect(component.showUpload).toBeFalse();
  });

  describe('confirmDelete', () => {
    it('presents an alert with cancel and destructive delete buttons', async () => {
      alertCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));

      await component.confirmDelete(photo1);

      expect(alertCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({
          header: 'Delete Photo',
          buttons: jasmine.arrayContaining([
            jasmine.objectContaining({ text: 'Cancel', role: 'cancel' }),
            jasmine.objectContaining({ text: 'Delete', role: 'destructive' }),
          ]),
        }),
      );
    });

    it('removes the photo from the list when the delete handler runs and succeeds', async () => {
      photoServiceSpy.deletePhoto.and.returnValue(of(undefined));
      let capturedHandler: (() => void) | undefined;
      alertCtrlSpy.create.and.callFake((opts: any) => {
        capturedHandler = opts.buttons[1].handler;
        return Promise.resolve({ present: () => Promise.resolve() } as any);
      });

      await component.confirmDelete(photo1);
      capturedHandler?.();

      expect(photoServiceSpy.deletePhoto).toHaveBeenCalledWith(9, 1);
      expect(component.photos).toEqual([photo2]);
    });

    it('shows a toast and keeps the photo in the list on delete failure', async () => {
      photoServiceSpy.deletePhoto.and.returnValue(
        throwError(() => ({ message: 'You do not have permission to manage photos on this stop.' })),
      );
      let capturedHandler: (() => void) | undefined;
      alertCtrlSpy.create.and.callFake((opts: any) => {
        capturedHandler = opts.buttons[1].handler;
        return Promise.resolve({ present: () => Promise.resolve() } as any);
      });

      await component.confirmDelete(photo1);
      capturedHandler?.();
      await fixture.whenStable();

      expect(component.photos.length).toBe(2);
      expect(toastCtrlSpy.create).toHaveBeenCalledWith(
        jasmine.objectContaining({
          message: 'You do not have permission to manage photos on this stop.',
          color: 'danger',
        }),
      );
    });
  });
});