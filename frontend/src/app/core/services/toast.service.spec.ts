import { TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;
  let toastCtrlSpy: jasmine.SpyObj<ToastController>;
  let presentSpy: jasmine.Spy;

  beforeEach(() => {
    presentSpy = jasmine.createSpy('present').and.returnValue(Promise.resolve());
    toastCtrlSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastCtrlSpy.create.and.returnValue(Promise.resolve({ present: presentSpy } as any));

    TestBed.configureTestingModule({
      providers: [{ provide: ToastController, useValue: toastCtrlSpy }],
    });
    service = TestBed.inject(ToastService);
  });

  it('uses the error message when present', async () => {
    await service.showError(new Error('Network error.'), 'fallback');

    expect(toastCtrlSpy.create).toHaveBeenCalledWith({
      message: 'Network error.',
      duration: 2500,
      color: 'danger',
    });
    expect(presentSpy).toHaveBeenCalled();
  });

  it('falls back to the provided message when the error has none', async () => {
    await service.showError({}, 'Could not do the thing.');

    expect(toastCtrlSpy.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ message: 'Could not do the thing.' }),
    );
  });

  it('falls back when err is not an object', async () => {
    await service.showError('boom', 'Could not do the thing.');

    expect(toastCtrlSpy.create).toHaveBeenCalledWith(
      jasmine.objectContaining({ message: 'Could not do the thing.' }),
    );
  });

  it('accepts a custom duration', async () => {
    await service.showError(new Error('x'), 'fallback', 5000);

    expect(toastCtrlSpy.create).toHaveBeenCalledWith(jasmine.objectContaining({ duration: 5000 }));
  });

  it('showSuccess presents a success-colored toast with the given message', async () => {
    await service.showSuccess('Trip created!');

    expect(toastCtrlSpy.create).toHaveBeenCalledWith({
      message: 'Trip created!',
      duration: 2000,
      color: 'success',
    });
    expect(presentSpy).toHaveBeenCalled();
  });

  it('showSuccess accepts a custom duration', async () => {
    await service.showSuccess('Saved.', 4000);

    expect(toastCtrlSpy.create).toHaveBeenCalledWith(jasmine.objectContaining({ duration: 4000 }));
  });
});
