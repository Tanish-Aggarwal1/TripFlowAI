import { provideZoneChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ItemReorderEventDetail } from '@ionic/angular/standalone';
import { StopListComponent } from './stop-list.component';
import { expectNoA11yViolations, expectAllFormControlsLabeled } from '../../../../../testing/a11y';

describe('StopListComponent', () => {
  let component: StopListComponent;
  let fixture: ComponentFixture<StopListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StopListComponent],
      // TestBed defaults to zoneless change detection (Angular 21); this app
      // still runs zone-based (see provideZoneChangeDetection() in main.ts),
      // so match that here or plain field mutations on `component.stops`
      // won't be picked up the way they are in the real app.
      providers: [provideZoneChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(StopListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('has no accessibility violations', async () => {
    await expectNoA11yViolations(fixture.nativeElement);
  });

  it('labels every form control', () => {
    expectAllFormControlsLabeled(fixture.nativeElement);
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
        { id: null, name: 'Cottage', latitude: 45.0, longitude: -79.9, address: '123 Main St', notes: 'Nice place' },
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
        { id: 10, name: 'A', latitude: 1, longitude: 1 },
        { id: 11, name: 'B', latitude: 2, longitude: 2 },
      ];
      spyOn(component.stopsChanged, 'emit');

      component.removeStop(0);

      expect(component.stops).toEqual([{ id: 11, name: 'B', latitude: 2, longitude: 2 }]);
      expect(component.stopsChanged.emit).toHaveBeenCalledWith(component.stops);
    });
  });

  describe('rendering with duplicate stop names', () => {
    it('keeps each row bound to its own data after removing a duplicate-named stop', () => {
      component.stops = [
        { id: 20, name: 'Coffee', latitude: 1, longitude: 1, address: 'First St' },
        { id: 21, name: 'Coffee', latitude: 2, longitude: 2, address: 'Second St' },
      ];
      fixture.detectChanges();

      component.removeStop(0);
      fixture.detectChanges();

      const rows: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('ion-item h3');
      expect(rows.length).toBe(1);
      expect(rows[0].textContent).toContain('1. Coffee');
      const addresses: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('ion-item p');
      expect(addresses[0].textContent).toContain('Second St');
    });
  });

  describe('handleReorder', () => {
    it('moves a stop from one index to another and emits the updated list', () => {
      component.stops = [
        { id: 10, name: 'A', latitude: 1, longitude: 1 },
        { id: 11, name: 'B', latitude: 2, longitude: 2 },
        { id: 12, name: 'C', latitude: 3, longitude: 3 },
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

    // Reorder splices the array, so it is the one operation here that could detach an id
    // from its stop. A scrambled id is worse than a lost one: it would rewrite the wrong
    // server-side stop on save rather than just inserting a new one.
    it('keeps each id attached to its own stop through a reorder', () => {
      component.stops = [
        { id: 10, name: 'A', latitude: 1, longitude: 1 },
        { id: 11, name: 'B', latitude: 2, longitude: 2 },
        { id: 12, name: 'C', latitude: 3, longitude: 3 },
      ];
      const event = {
        detail: { from: 0, to: 2, complete: () => undefined } as unknown as ItemReorderEventDetail,
      } as CustomEvent<ItemReorderEventDetail>;

      component.handleReorder(event);

      expect(component.stops.map((s) => [s.id, s.name])).toEqual([
        [11, 'B'],
        [12, 'C'],
        [10, 'A'],
      ]);
    });
  });

  describe('id round trip', () => {
    it('removeStop drops only the removed id and leaves the survivors intact', () => {
      component.stops = [
        { id: 10, name: 'A', latitude: 1, longitude: 1 },
        { id: 11, name: 'B', latitude: 2, longitude: 2 },
        { id: 12, name: 'C', latitude: 3, longitude: 3 },
      ];

      component.removeStop(1);

      expect(component.stops.map((s) => s.id)).toEqual([10, 12]);
    });

    // A new stop must be null, never 0 or undefined — null is what tells the backend to
    // insert. undefined would serialise away and read as "existing stop, id missing".
    it('addStop gives a brand-new stop a null id', () => {
      component.newName = 'Cottage';
      component.newLat = '45';
      component.newLng = '-79';

      component.addStop();

      expect(component.stops[0].id).toBeNull();
    });
  });
});
