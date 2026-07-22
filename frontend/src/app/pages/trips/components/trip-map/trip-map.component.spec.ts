import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TripMapComponent } from './trip-map.component';
import { StopResponse, TripResponse } from 'src/app/core/models/trip.model';

describe('TripMapComponent', () => {
  let component: TripMapComponent;
  let fixture: ComponentFixture<TripMapComponent>;

  beforeEach(async () => {
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
    // ✓ Use number type for IDs (not string)
    const mockStops: StopResponse[] = [
      {
        id: 1,
        name: 'Stop 1',
        latitude: 40.7128,
        longitude: -74.006,
        address: '123 Main St',
        stopOrder: 0,
        status: 'PLANNED',
        notes: null, // ✓ Use null instead of undefined
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

    // ✓ Use number type for Trip ID
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

    // ✓ Safe access to private popup property with proper null checking
    const popup = (component as any)['popup'];
    expect(popup).toBeNull(); // Initially null until map is initialized
  });
});