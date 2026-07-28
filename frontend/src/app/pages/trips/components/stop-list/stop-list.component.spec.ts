import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ItemReorderEventDetail } from '@ionic/angular/standalone';
import { StopListComponent } from './stop-list.component';

describe('StopListComponent', () => {
  let component: StopListComponent;
  let fixture: ComponentFixture<StopListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StopListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(StopListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('addStop validation', () => {
    it('requires a name', () => {
      component.newName = '';
      component.newLat = '45';
      component.newLng = '-79';

      component.addStop();

      expect(component.formError).toBe('Stop name is required.');
      expect(component.stops).toEqual([]);
    });

    it('requires valid numeric latitude/longitude', () => {
      component.newName = 'Cottage';
      component.newLat = 'abc';
      component.newLng = '-79';

      component.addStop();

      expect(component.formError).toBe('Valid latitude and longitude are required.');
      expect(component.stops).toEqual([]);
    });

    it('rejects latitude outside -90..90', () => {
      component.newName = 'Cottage';
      component.newLat = '999';
      component.newLng = '-79';

      component.addStop();

      expect(component.formError).toBe('Latitude must be between -90 and 90.');
      expect(component.stops).toEqual([]);
    });

    it('rejects latitude below -90', () => {
      component.newName = 'Cottage';
      component.newLat = '-91';
      component.newLng = '-79';

      component.addStop();

      expect(component.formError).toBe('Latitude must be between -90 and 90.');
      expect(component.stops).toEqual([]);
    });

    it('rejects longitude outside -180..180', () => {
      component.newName = 'Cottage';
      component.newLat = '45';
      component.newLng = '999';

      component.addStop();

      expect(component.formError).toBe('Longitude must be between -180 and 180.');
      expect(component.stops).toEqual([]);
    });

    it('accepts boundary coordinates (90/-90 latitude, 180/-180 longitude)', () => {
      component.newName = 'North Pole';
      component.newLat = '90';
      component.newLng = '180';

      component.addStop();

      expect(component.formError).toBe('');
      expect(component.stops.length).toBe(1);
    });
  });

  describe('addStop happy path', () => {
    it('adds a valid stop, trims fields, emits stopsChanged, and resets the form', () => {
      spyOn(component.stopsChanged, 'emit');
      component.newName = ' Cottage ';
      component.newLat = '45.0';
      component.newLng = '-79.9';
      component.newAddress = ' 123 Main St ';
      component.newNotes = ' Nice place ';

      component.addStop();

      expect(component.stops).toEqual([
        { name: 'Cottage', latitude: 45.0, longitude: -79.9, address: '123 Main St', notes: 'Nice place' },
      ]);
      expect(component.stopsChanged.emit).toHaveBeenCalledWith(component.stops);
      expect(component.newName).toBe('');
      expect(component.newLat).toBe('');
      expect(component.formError).toBe('');
    });

    it('omits address/notes when left blank', () => {
      component.newName = 'Cottage';
      component.newLat = '45.0';
      component.newLng = '-79.9';

      component.addStop();

      expect(component.stops[0].address).toBeUndefined();
      expect(component.stops[0].notes).toBeUndefined();
    });
  });

  describe('removeStop', () => {
    it('removes the stop at the given index and emits the updated list', () => {
      component.stops = [
        { name: 'A', latitude: 1, longitude: 1 },
        { name: 'B', latitude: 2, longitude: 2 },
      ];
      spyOn(component.stopsChanged, 'emit');

      component.removeStop(0);

      expect(component.stops).toEqual([{ name: 'B', latitude: 2, longitude: 2 }]);
      expect(component.stopsChanged.emit).toHaveBeenCalledWith(component.stops);
    });
  });

  describe('handleReorder', () => {
    it('moves a stop from one index to another and emits the updated list', () => {
      component.stops = [
        { name: 'A', latitude: 1, longitude: 1 },
        { name: 'B', latitude: 2, longitude: 2 },
        { name: 'C', latitude: 3, longitude: 3 },
      ];
      spyOn(component.stopsChanged, 'emit');
      const completeSpy = jasmine.createSpy('complete');
      const event = {
        detail: { from: 0, to: 2, complete: completeSpy } as unknown as ItemReorderEventDetail,
      } as CustomEvent<ItemReorderEventDetail>;

      component.handleReorder(event);

      expect(component.stops.map((s) => s.name)).toEqual(['B', 'C', 'A']);
      expect(component.stopsChanged.emit).toHaveBeenCalledWith(component.stops);
      expect(completeSpy).toHaveBeenCalled();
    });
  });
});
