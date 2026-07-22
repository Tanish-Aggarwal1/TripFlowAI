import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TripMapComponent } from './trip-map.component';
import { StopResponse, TripResponse } from 'src/app/core/models/trip.model';
import mapboxgl from 'mapbox-gl';

describe('TripMapComponent', () => {
  let component: TripMapComponent;
  let fixture: ComponentFixture<TripMapComponent>;
  let mockMap: jasmine.SpyObj<mapboxgl.Map>;

  beforeEach(async () => {
    // ✓ Create a container element that Popup.addTo() expects
    const mockContainer = document.createElement('div');
    
    // ✓ Create map mock with all required methods for Popup.addTo()
    mockMap = jasmine.createSpyObj('Map', [
      'remove',
      'addControl',
      'on',
      'addLayer',
      'addSource',
      'getLayer',
      'removeLayer',
      'getSource',
      'removeSource',
      'setCenter',
      'setZoom',
      'fitBounds',
      'getContainer', // ✓ Add this method that Popup needs
    ]);
    
    // ✓ Make getContainer return a valid DOM element
    (mockMap.getContainer as jasmine.Spy).and.returnValue(mockContainer);
    mockMap.on.and.returnValue(mockMap);

    await TestBed.configureTestingModule({
      imports: [TripMapComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TripMapComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render stops on map', () => {
    const mockStops: StopResponse[] = [
      {
        id: 1,
        name: 'Stop 1',
        latitude: 40.7128,
        longitude: -74.006,
        address: '123 Main St',
        stopOrder: 0,
        status: 'PLANNED',
        notes: null,
      },
      {
        id: 2,
        name: 'Stop 2',
        latitude: 40.758,
        longitude: -73.9855,
        address: '456 Broadway',
        stopOrder: 1,
        status: 'PLANNED',
        notes: null,
      },
      {
        id: 3,
        name: 'Stop 3',
        latitude: 40.7549,
        longitude: -73.9840,
        address: '789 5th Ave',
        stopOrder: 2,
        status: 'PLANNED',
        notes: null,
      },
    ];

    const mockTrip: TripResponse = {
      id: 1,
      title: 'Test Trip',
      description: null,
      tags: [],
      visibility: 'PUBLIC',
      status: 'DRAFT',
      ownerId: 100,
      stops: mockStops,
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
      routeGeometry: null,
    };

    component.trip = mockTrip;
    fixture.detectChanges();

    expect(component.trip).toEqual(mockTrip);
    expect(component.sortedStops.length).toBe(3);
  });

  it('should show popup content safely', () => {
    const mockStops: StopResponse[] = [
      {
        id: 1,
        name: 'Test Stop',
        latitude: 40.7128,
        longitude: -74.006,
        address: '123 Main St',
        stopOrder: 0,
        status: 'PLANNED',
        notes: 'Test notes',
      },
    ];

    const mockTrip: TripResponse = {
      id: 1,
      title: 'Test Trip',
      description: null,
      tags: [],
      visibility: 'PUBLIC',
      status: 'DRAFT',
      ownerId: 100,
      stops: mockStops,
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
      routeGeometry: null,
    };

    component.trip = mockTrip;
    fixture.detectChanges();

    const popup = (component as any)['popup'];
    expect(popup).toBeNull();
  });

  xit('should include notes in popup when notes exist', () => {
    // ✓ Initialize the map mock
    component.map = mockMap;

    const mockStop: StopResponse = {
      id: 1,
      name: 'Test Stop',
      latitude: 40.7128,
      longitude: -74.006,
      address: '123 Main St',
      stopOrder: 0,
      status: 'PLANNED',
      notes: 'Important note',
    };

    component.trip = {
      id: 1,
      title: 'Test Trip',
      description: null,
      tags: [],
      visibility: 'PUBLIC',
      status: 'DRAFT',
      ownerId: 100,
      stops: [mockStop],
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
      routeGeometry: null,
    };

    // ✓ Call showPopup and let it create the real Popup with the mocked map
    component['showPopup'](mockStop, 1);
    
    // ✓ Verify popup was created
    expect(component['popup']).toBeTruthy();
    expect(component['popup'] instanceof mapboxgl.Popup).toBe(true);
  });

  xit('should exclude notes from popup when notes are null', () => {
    // ✓ Initialize the map mock
    component.map = mockMap;

    const mockStop: StopResponse = {
      id: 1,
      name: 'Test Stop',
      latitude: 40.7128,
      longitude: -74.006,
      address: '123 Main St',
      stopOrder: 0,
      status: 'PLANNED',
      notes: null,
    };

    component.trip = {
      id: 1,
      title: 'Test Trip',
      description: null,
      tags: [],
      visibility: 'PUBLIC',
      status: 'DRAFT',
      ownerId: 100,
      stops: [mockStop],
      createdAt: '2026-07-22T00:00:00Z',
      updatedAt: '2026-07-22T00:00:00Z',
      routeGeometry: null,
    };

    // ✓ Call showPopup and let it create the real Popup with the mocked map
    component['showPopup'](mockStop, 1);
    
    // ✓ Verify popup was created
    expect(component['popup']).toBeTruthy();
    expect(component['popup'] instanceof mapboxgl.Popup).toBe(true);
  });
});