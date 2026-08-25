import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { TripMapComponent } from './trip-map.component';
import { StopResponse, TripResponse } from '../../../../core/models/trip.model';
import mapboxgl from 'mapbox-gl';

function makeStop(overrides: Partial<StopResponse> = {}): StopResponse {
  return {
    id: 1,
    name: 'Stop 1',
    latitude: 40.7128,
    longitude: -74.006,
    address: '123 Main St',
    stopOrder: 0,
    status: 'PLANNED',
    notes: null,
    dayNumber: null,
    plannedTime: null,
    stopType: 'SIGHTSEEING',
    ...overrides,
  };
}

function makeTrip(overrides: Partial<TripResponse> = {}): TripResponse {
  return {
    id: 1,
    title: 'Test Trip',
    description: null,
    tags: [],
    visibility: 'PUBLIC',
    status: 'DRAFT',
    ownerId: 100,
    stops: [makeStop()],
    createdAt: '2026-07-22T00:00:00Z',
    updatedAt: '2026-07-22T00:00:00Z',
    routeGeometry: null,
    startDate: null,
    visitedStopCount: 0,
    completionPercentage: 0,
    ...overrides,
  };
}

describe('TripMapComponent', () => {
  let component: TripMapComponent;
  let fixture: ComponentFixture<TripMapComponent>;

  let mockMap: jasmine.SpyObj<mapboxgl.Map>;
  let mockMarker: jasmine.SpyObj<mapboxgl.Marker>;
  let mockPopup: jasmine.SpyObj<mapboxgl.Popup>;
  let mockBounds: jasmine.SpyObj<mapboxgl.LngLatBounds>;

  let onHandlers: Record<string, (arg?: unknown) => void>;
  let markerElements: HTMLDivElement[];

  beforeEach(async () => {
    onHandlers = {};
    markerElements = [];

    mockMap = jasmine.createSpyObj('Map', [
      'remove', 'addControl', 'on', 'addLayer', 'addSource',
      'getLayer', 'removeLayer', 'getSource', 'removeSource',
      'setCenter', 'setZoom', 'fitBounds',
    ]);
    (mockMap.on as jasmine.Spy).and.callFake((event: string, cb: (arg?: unknown) => void) => {
      onHandlers[event] = cb;
      return mockMap;
    });

    mockMarker = jasmine.createSpyObj('Marker', ['setLngLat', 'addTo', 'remove']);
    mockMarker.setLngLat.and.returnValue(mockMarker);
    mockMarker.addTo.and.returnValue(mockMarker);

    mockPopup = jasmine.createSpyObj('Popup', ['setLngLat', 'setHTML', 'addTo', 'remove']);
    mockPopup.setLngLat.and.returnValue(mockPopup);
    mockPopup.setHTML.and.returnValue(mockPopup);
    mockPopup.addTo.and.returnValue(mockPopup);

    mockBounds = jasmine.createSpyObj('LngLatBounds', ['extend']);
    mockBounds.extend.and.returnValue(mockBounds);

    spyOn(mapboxgl, 'Map').and.returnValue(mockMap);
    spyOn(mapboxgl, 'NavigationControl').and.returnValue({} as mapboxgl.NavigationControl);
    // Reflect.construct (used internally when a spied constructor is invoked with `new`)
    // requires the fake to be a real constructor function — an arrow function throws
    // "target is not a constructor", so this must be a `function` expression.
    spyOn(mapboxgl, 'Marker').and.callFake(function (opts?: mapboxgl.MarkerOptions) {
      if (opts?.element instanceof HTMLDivElement) markerElements.push(opts.element);
      return mockMarker;
    });
    spyOn(mapboxgl, 'Popup').and.returnValue(mockPopup);
    spyOn(mapboxgl, 'LngLatBounds').and.returnValue(mockBounds);

    await TestBed.configureTestingModule({
      imports: [TripMapComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TripMapComponent);
    component = fixture.componentInstance;
  });

  function triggerLoad(): void {
    onHandlers['load']();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('sortedStops / canOptimize getters', () => {
    it('sortedStops returns [] before trip is assigned', () => {
      expect(component.sortedStops).toEqual([]);
    });

    it('sortedStops sorts by stopOrder', () => {
      component.trip = makeTrip({
        stops: [makeStop({ id: 2, stopOrder: 1 }), makeStop({ id: 1, stopOrder: 0 })],
      });
      expect(component.sortedStops.map((s) => s.id)).toEqual([1, 2]);
    });

    it('canOptimize is false with fewer than 2 stops', () => {
      component.trip = makeTrip({ stops: [makeStop()] });
      expect(component.canOptimize).toBeFalse();
    });

    it('canOptimize is true with 2 or more stops', () => {
      component.trip = makeTrip({ stops: [makeStop({ id: 1 }), makeStop({ id: 2 })] });
      expect(component.canOptimize).toBeTrue();
    });
  });

  describe('initMap', () => {
    it('creates the map and registers handlers on success', () => {
      component.trip = makeTrip();
      fixture.detectChanges(false);

      expect(component.map).toBe(mockMap);
      expect(component.mapFailed).toBeFalse();
      expect(mockMap.addControl).toHaveBeenCalled();
      expect(onHandlers['error']).toBeDefined();
      expect(onHandlers['load']).toBeDefined();
    });

    it('sets mapFailed when the Map constructor throws', () => {
      (mapboxgl.Map as unknown as jasmine.Spy).and.callFake(function () {
        throw new Error('no webgl');
      });
      spyOn(console, 'error');

      component.trip = makeTrip();
      fixture.detectChanges(false);

      expect(component.mapFailed).toBeTrue();
      expect(component.map).toBeNull();
      expect(console.error).toHaveBeenCalled();
    });

    it('sets mapFailed when the map emits an error event', () => {
      spyOn(console, 'error');
      component.trip = makeTrip();
      fixture.detectChanges(false);

      onHandlers['error']({ error: new Error('tile error') });

      expect(component.mapFailed).toBeTrue();
      expect(console.error).toHaveBeenCalled();
    });

    it('does nothing if called again while a map already exists', () => {
      component.trip = makeTrip();
      fixture.detectChanges(false);

      expect(() => component['initMap']()).not.toThrow();
      expect(mapboxgl.Map).toHaveBeenCalledTimes(1);
    });
  });

  describe('retry', () => {
    it('clears mapFailed and re-initializes the map', () => {
      (mapboxgl.Map as unknown as jasmine.Spy).and.callFake(function () {
        throw new Error('no webgl');
      });
      spyOn(console, 'error');
      component.trip = makeTrip();
      fixture.detectChanges(false);
      expect(component.mapFailed).toBeTrue();

      (mapboxgl.Map as unknown as jasmine.Spy).and.returnValue(mockMap);
      component.retry();

      expect(component.mapFailed).toBeFalse();
      expect(component.map).toBe(mockMap);
    });

    it('tears down and rebuilds the map when it emits an error event (regression: initMap() no-ops while this.map is still set)', () => {
      spyOn(console, 'error');
      component.trip = makeTrip();
      fixture.detectChanges(false);
      const failedMap = mockMap;

      onHandlers['error']({ error: new Error('tile error') });
      expect(component.mapFailed).toBeTrue();
      expect(component.map).toBe(failedMap);

      const rebuiltMap = jasmine.createSpyObj('Map', [
        'remove', 'addControl', 'on', 'addLayer', 'addSource',
        'getLayer', 'removeLayer', 'getSource', 'removeSource',
        'setCenter', 'setZoom', 'fitBounds',
      ]) as jasmine.SpyObj<mapboxgl.Map>;
      (rebuiltMap.on as jasmine.Spy).and.callFake((event: string, cb: (arg?: unknown) => void) => {
        onHandlers[event] = cb;
        return rebuiltMap;
      });
      (mapboxgl.Map as unknown as jasmine.Spy).and.returnValue(rebuiltMap);

      component.retry();

      expect(failedMap.remove).toHaveBeenCalled();
      expect(component.mapFailed).toBeFalse();
      expect(component.map).toBe(rebuiltMap);
      expect(component.map).not.toBe(failedMap);
    });
  });

  describe('requestOptimize', () => {
    it('emits optimizeRequested', () => {
      const spy = jasmine.createSpy('optimizeRequested');
      component.optimizeRequested.subscribe(spy);
      component.requestOptimize();
      expect(spy).toHaveBeenCalled();
    });
  });

  describe('rendering on load', () => {
    it('renders a marker per stop and clicking one emits stopSelected and shows a popup', () => {
      const stop1 = makeStop({ id: 1, name: 'Stop 1', stopOrder: 0 });
      const stop2 = makeStop({ id: 2, name: 'Stop 2', stopOrder: 1 });
      component.trip = makeTrip({ stops: [stop1, stop2] });
      fixture.detectChanges(false);
      triggerLoad();

      expect(mapboxgl.Marker).toHaveBeenCalledTimes(2);
      expect(markerElements.length).toBe(2);

      const selected = jasmine.createSpy('stopSelected');
      component.stopSelected.subscribe(selected);

      markerElements[0].click();

      expect(selected).toHaveBeenCalledWith(stop1);
      expect(mockPopup.setHTML).toHaveBeenCalled();
      expect(mockPopup.addTo).toHaveBeenCalledWith(mockMap);
    });

    it('removes an existing popup before showing a new one', () => {
      const stop1 = makeStop({ id: 1, stopOrder: 0 });
      const stop2 = makeStop({ id: 2, stopOrder: 1 });
      component.trip = makeTrip({ stops: [stop1, stop2] });
      fixture.detectChanges(false);
      triggerLoad();

      markerElements[0].click();
      markerElements[1].click();

      expect(mockPopup.remove).toHaveBeenCalled();
    });

    it('escapes stop name, address, and notes in the popup HTML', () => {
      const stop = makeStop({
        name: '<b>Bad</b>',
        address: '<script>alert(1)</script>',
        notes: '<i>note</i>',
      });
      component.trip = makeTrip({ stops: [stop] });
      fixture.detectChanges(false);
      triggerLoad();

      markerElements[0].click();

      const html = mockPopup.setHTML.calls.mostRecent().args[0] as string;
      expect(html).not.toContain('<b>Bad</b>');
      expect(html).toContain('&lt;b&gt;Bad&lt;/b&gt;');
      expect(html).not.toContain('<script>alert(1)</script>');
      expect(html).not.toContain('<i>note</i>');
    });

    it('escapes quotes in the popup HTML', () => {
      const stop = makeStop({ name: `"onmouseover="alert(1)` });
      component.trip = makeTrip({ stops: [stop] });
      fixture.detectChanges(false);
      triggerLoad();

      markerElements[0].click();

      const html = mockPopup.setHTML.calls.mostRecent().args[0] as string;
      expect(html).not.toContain('"onmouseover="alert(1)');
      expect(html).toContain('&quot;onmouseover=&quot;alert(1)');
    });

    it('omits the address/notes blocks when they are null', () => {
      const stop = makeStop({ address: null, notes: null });
      component.trip = makeTrip({ stops: [stop] });
      fixture.detectChanges(false);
      triggerLoad();

      markerElements[0].click();

      const html = mockPopup.setHTML.calls.mostRecent().args[0] as string;
      expect(html).not.toContain('popup-address');
      expect(html).not.toContain('popup-notes');
    });

    it('renders the route when routeGeometry is valid JSON', () => {
      const geometry = { type: 'LineString', coordinates: [[0, 0], [1, 1]] };
      component.trip = makeTrip({ routeGeometry: JSON.stringify(geometry) });
      fixture.detectChanges(false);
      triggerLoad();

      expect(mockMap.addSource).toHaveBeenCalledWith('trip-route', jasmine.objectContaining({
        type: 'geojson',
        data: jasmine.objectContaining({ geometry }),
      }));
      expect(mockMap.addLayer).toHaveBeenCalled();
    });

    it('removes an existing layer/source before re-adding the route', () => {
      (mockMap.getLayer as jasmine.Spy).and.returnValue(true);
      (mockMap.getSource as jasmine.Spy).and.returnValue(true);
      component.trip = makeTrip({ routeGeometry: JSON.stringify({ type: 'LineString', coordinates: [] }) });
      fixture.detectChanges(false);
      triggerLoad();

      expect(mockMap.removeLayer).toHaveBeenCalledWith('trip-route-line');
      expect(mockMap.removeSource).toHaveBeenCalledWith('trip-route');
    });

    it('does not add a route source when routeGeometry is null', () => {
      component.trip = makeTrip({ routeGeometry: null });
      fixture.detectChanges(false);
      triggerLoad();

      expect(mockMap.addSource).not.toHaveBeenCalled();
    });

    it('logs and skips adding a route source when routeGeometry is malformed JSON', () => {
      spyOn(console, 'error');
      component.trip = makeTrip({ routeGeometry: 'not-json' });
      fixture.detectChanges(false);
      triggerLoad();

      expect(console.error).toHaveBeenCalled();
      expect(mockMap.addSource).not.toHaveBeenCalled();
    });

    it('does nothing for fitBounds when there are no stops', () => {
      component.trip = makeTrip({ stops: [] });
      fixture.detectChanges(false);
      triggerLoad();

      expect(mockMap.setCenter).not.toHaveBeenCalled();
      expect(mockMap.fitBounds).not.toHaveBeenCalled();
    });

    it('centers and zooms on the single stop when there is exactly one', () => {
      const stop = makeStop({ latitude: 1, longitude: 2 });
      component.trip = makeTrip({ stops: [stop] });
      fixture.detectChanges(false);
      triggerLoad();

      expect(mockMap.setCenter).toHaveBeenCalledWith([2, 1]);
      expect(mockMap.setZoom).toHaveBeenCalledWith(13);
      expect(mockMap.fitBounds).not.toHaveBeenCalled();
    });

    it('fits bounds across all stops when there are multiple', () => {
      const stop1 = makeStop({ id: 1, latitude: 1, longitude: 2 });
      const stop2 = makeStop({ id: 2, latitude: 3, longitude: 4 });
      component.trip = makeTrip({ stops: [stop1, stop2] });
      fixture.detectChanges(false);
      triggerLoad();

      expect(mockBounds.extend).toHaveBeenCalledWith([2, 1]);
      expect(mockBounds.extend).toHaveBeenCalledWith([4, 3]);
      expect(mockMap.fitBounds).toHaveBeenCalledWith(mockBounds, jasmine.objectContaining({ maxZoom: 14 }));
    });
  });

  describe('ngOnChanges', () => {
    it('re-renders when trip changes after the first change and the map exists', () => {
      component.trip = makeTrip();
      fixture.detectChanges(false);
      const renderSpy = spyOn<any>(component, 'renderTripData');

      component.ngOnChanges({
        trip: new SimpleChange(component.trip, makeTrip({ id: 2 }), false),
      });

      expect(renderSpy).toHaveBeenCalled();
    });

    it('does not re-render on the first change', () => {
      component.trip = makeTrip();
      fixture.detectChanges(false);
      const renderSpy = spyOn<any>(component, 'renderTripData');

      component.ngOnChanges({
        trip: new SimpleChange(undefined, component.trip, true),
      });

      expect(renderSpy).not.toHaveBeenCalled();
    });

    it('does not re-render when the map has not been created', () => {
      const renderSpy = spyOn<any>(component, 'renderTripData');

      component.ngOnChanges({
        trip: new SimpleChange(undefined, makeTrip(), false),
      });

      expect(renderSpy).not.toHaveBeenCalled();
    });
  });

  describe('ngOnDestroy', () => {
    it('clears markers, the popup, and removes the map', () => {
      const stop1 = makeStop({ id: 1, stopOrder: 0 });
      const stop2 = makeStop({ id: 2, stopOrder: 1 });
      component.trip = makeTrip({ stops: [stop1, stop2] });
      fixture.detectChanges(false);
      triggerLoad();
      markerElements[0].click();

      component.ngOnDestroy();

      expect(mockMarker.remove).toHaveBeenCalledTimes(2);
      expect(mockPopup.remove).toHaveBeenCalled();
      expect(mockMap.remove).toHaveBeenCalled();
      expect(component.map).toBeNull();
    });
  });
});
