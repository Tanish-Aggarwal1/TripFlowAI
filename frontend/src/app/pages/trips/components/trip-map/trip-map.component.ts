import {
  Component, Input, Output, EventEmitter,
  ElementRef, ViewChild, OnChanges, OnDestroy, SimpleChanges, AfterViewInit
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonButton } from '@ionic/angular/standalone';
import mapboxgl from 'mapbox-gl';
import { environment } from '../../../../../environments/environment';
import { TripResponse, StopResponse } from '../../../../core/models/trip.model';

@Component({
  selector: 'app-trip-map',
  standalone: true,
  imports: [CommonModule, IonButton],
  templateUrl: './trip-map.component.html',
  styleUrls: ['./trip-map.component.scss'],
})
export class TripMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) trip!: TripResponse;
  @Output() optimizeRequested = new EventEmitter<void>();
  @Output() stopSelected = new EventEmitter<StopResponse>();

  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef<HTMLDivElement>;

  map: mapboxgl.Map | null = null;
  mapFailed = false;
  routeMissing = false;

  private markers: mapboxgl.Marker[] = [];
  private popup: mapboxgl.Popup | null = null;
  private viewInitialized = false;

  private readonly ROUTE_SOURCE_ID = 'trip-route';
  private readonly ROUTE_LAYER_ID = 'trip-route-line';

  get sortedStops(): StopResponse[] {
    return this.trip?.stops ? [...this.trip.stops].sort((a, b) => a.stopOrder - b.stopOrder) : [];
  }

  ngAfterViewInit(): void {
    this.viewInitialized = true;
    this.initMap();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['trip'] && !changes['trip'].firstChange && this.map) {
      this.renderTripData();
    }
  }

  ngOnDestroy(): void {
    this.clearMarkers();
    this.map?.remove();
    this.map = null;
  }

  retry(): void {
    this.mapFailed = false;
    this.initMap();
  }

  requestOptimize(): void {
    this.optimizeRequested.emit();
  }

  private initMap(): void {
    if (!this.viewInitialized || this.map) return;

    mapboxgl.accessToken = environment.mapboxToken;

    try {
      this.map = new mapboxgl.Map({
        container: this.mapContainer.nativeElement,
        style: 'mapbox://styles/mapbox/streets-v12',
        center: [0, 0],
        zoom: 1,
      });
    } catch (err) {
      console.error('Mapbox init failed', err);
      this.mapFailed = true;
      return;
    }

    this.map.addControl(new mapboxgl.NavigationControl(), 'top-right');

    this.map.on('error', (e) => {
      console.error('Mapbox tile/style error', e?.error);
      this.mapFailed = true;
    });

    this.map.on('load', () => this.renderTripData());
  }

  private renderTripData(): void {
    if (!this.map || !this.trip) return;
    this.clearMarkers();
    this.renderMarkers();
    this.renderRoute();
    this.fitBounds();
  }

  private renderMarkers(): void {
    if (!this.map) return;

    this.sortedStops.forEach((stop, index) => {
      const el = document.createElement('div');
      el.className = 'trip-map-marker';
      el.textContent = String(index + 1);

      const marker = new mapboxgl.Marker({ element: el })
        .setLngLat([stop.longitude, stop.latitude])
        .addTo(this.map!);

      el.addEventListener('click', () => {
        this.showPopup(stop, index + 1);
        this.stopSelected.emit(stop);
      });

      this.markers.push(marker);
    });
  }

  private showPopup(stop: StopResponse, order: number): void {
    if (!this.map) return;
    this.popup?.remove();

    const html = `
      <div class="trip-map-popup">
        <strong>${order}. ${this.escapeHtml(stop.name)}</strong>
        ${stop.address ? `<div class="popup-address">${this.escapeHtml(stop.address)}</div>` : ''}
        ${stop.notes ? `<div class="popup-notes">${this.escapeHtml(stop.notes)}</div>` : ''}
      </div>
    `;

    this.popup = new mapboxgl.Popup({ offset: 20 })
      .setLngLat([stop.longitude, stop.latitude])
      .setHTML(html)
      .addTo(this.map);
  }

  private renderRoute(): void {
    if (!this.map) return;

    if (this.map.getLayer(this.ROUTE_LAYER_ID)) this.map.removeLayer(this.ROUTE_LAYER_ID);
    if (this.map.getSource(this.ROUTE_SOURCE_ID)) this.map.removeSource(this.ROUTE_SOURCE_ID);

    if (!this.trip.routeGeometry) {
      this.routeMissing = true;
      return;
    }

    let geometry: GeoJSON.Geometry;
    try {
      geometry = JSON.parse(this.trip.routeGeometry);
    } catch (err) {
      console.error('Failed to parse routeGeometry', err);
      this.routeMissing = true;
      return;
    }

    this.routeMissing = false;

    this.map.addSource(this.ROUTE_SOURCE_ID, {
      type: 'geojson',
      data: { type: 'Feature', properties: {}, geometry },
    });

    this.map.addLayer({
      id: this.ROUTE_LAYER_ID,
      type: 'line',
      source: this.ROUTE_SOURCE_ID,
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#3b82f6', 'line-width': 4, 'line-opacity': 0.85 },
    });
  }

  private fitBounds(): void {
    if (!this.map || !this.trip.stops.length) return;

    if (this.trip.stops.length === 1) {
      const stop = this.trip.stops[0];
      this.map.setCenter([stop.longitude, stop.latitude]);
      this.map.setZoom(13);
      return;
    }

    const bounds = new mapboxgl.LngLatBounds();
    this.trip.stops.forEach(stop => bounds.extend([stop.longitude, stop.latitude]));
    this.map.fitBounds(bounds, { padding: 60, maxZoom: 14, duration: 0 });
  }

  private clearMarkers(): void {
    this.markers.forEach(m => m.remove());
    this.markers = [];
    this.popup?.remove();
    this.popup = null;
  }

  private escapeHtml(value: string): string {
    const div = document.createElement('div');
    div.textContent = value;
    return div.innerHTML;
  }
}