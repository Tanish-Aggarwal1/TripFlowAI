import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular';
import { of, throwError } from 'rxjs';
import { StopPhotoUploadComponent } from './stop-photo-upload.component';
import { StopPhotoService } from '../../../../core/services/stop-photo.service';
import { StopPhotoResponse } from '../../../../core/models/trip.model';
import { expectNoA11yViolations, expectAllFormControlsLabeled } from '../../../../../testing/a11y';

describe('StopPhotoUploadComponent', () => {
  let component: StopPhotoUploadComponent;
  let fixture: ComponentFixture<StopPhotoUploadComponent>;
  let photoServiceSpy: jasmine.SpyObj<StopPhotoService>;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;

  const photo: StopPhotoResponse = {
    id: 1,
    stopId: 9,
    url: 'https://res.cloudinary.com/x/y.jpg',
    cloudinaryPublicId: 'x/y',
    caption: 'Nice',
    createdAt: '2026-08-01T10:00:00Z',
  };

  beforeEach(async () => {
    photoServiceSpy = jasmine.createSpyObj('StopPhotoService', ['uploadPhoto']);
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));

    await TestBed.configureTestingModule({
      imports: [StopPhotoUploadComponent],
      providers: [
        { provide: StopPhotoService, useValue: photoServiceSpy },
        { provide: ToastController, useValue: toastCtrlSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StopPhotoUploadComponent);
    component = fixture.componentInstance;
    component.stopId = 9;
    fixture.detectChanges();
  });

  it('should create', () => expect(component).toBeTruthy());

  it('has no accessibility violations', async () => {
    await expectNoA11yViolations(fixture.nativeElement);
  });

  it('labels every form control', () => {
    expectAllFormControlsLabeled(fixture.nativeElement);
  });

  it('blocks submit with no file selected', () => {
    component.submit();
    expect(component.formError).toBe('Choose a photo first.');
    expect(photoServiceSpy.uploadPhoto).not.toHaveBeenCalled();
  });

  it('rejects a non-image file on selection', () => {
    const file = new File(['x'], 'doc.pdf', { type: 'application/pdf' });
    const input = { files: [file], value: '' } as unknown as HTMLInputElement;

    component.onFileSelected({ target: input } as unknown as Event);

    expect(component.formError).toBe('Please choose an image file.');
    expect(component.selectedFile).toBeNull();
  });

  it('tracks progress events without completing', () => {
    const file = new File(['x'], 'photo.jpg', { type: 'image/jpeg' });
    component.selectedFile = file;
    photoServiceSpy.uploadPhoto.and.returnValue(of({ progress: 42 }));

    component.submit();

    expect(component.progress).toBe(42);
    expect(component.uploading).toBeTrue();
  });

  it('emits uploaded and resets state on the done event', () => {
    const file = new File(['x'], 'photo.jpg', { type: 'image/jpeg' });
    component.selectedFile = file;
    component.caption = 'Nice';
    photoServiceSpy.uploadPhoto.and.returnValue(of({ done: photo }));
    spyOn(component.uploaded, 'emit');

    component.submit();

    expect(photoServiceSpy.uploadPhoto).toHaveBeenCalledWith(9, file, 'Nice');
    expect(component.uploaded.emit).toHaveBeenCalledWith(photo);
    expect(component.uploading).toBeFalse();
  });

  it('sends undefined caption when blank', () => {
    const file = new File(['x'], 'photo.jpg', { type: 'image/jpeg' });
    component.selectedFile = file;
    component.caption = '   ';
    photoServiceSpy.uploadPhoto.and.returnValue(of({ done: photo }));

    component.submit();

    expect(photoServiceSpy.uploadPhoto).toHaveBeenCalledWith(9, file, undefined);
  });

  it('shows a toast and resets uploading state on error', async () => {
    const file = new File(['x'], 'photo.jpg', { type: 'image/jpeg' });
    component.selectedFile = file;
    photoServiceSpy.uploadPhoto.and.returnValue(
      throwError(() => ({ message: 'Upload to Cloudinary failed. Check your connection and try again.' })),
    );

    component.submit();
    await fixture.whenStable();

    expect(component.uploading).toBeFalse();
    expect(toastCtrlSpy.create).toHaveBeenCalledWith(
      jasmine.objectContaining({
        message: 'Upload to Cloudinary failed. Check your connection and try again.',
        color: 'danger',
      }),
    );
  });

  it('emits cancelled on cancel', () => {
    spyOn(component.cancelled, 'emit');
    component.cancel();
    expect(component.cancelled.emit).toHaveBeenCalled();
  });
});