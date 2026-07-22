import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TripViewPage } from './trip-view.page';

describe('TripViewPage', () => {
  let component: TripViewPage;
  let fixture: ComponentFixture<TripViewPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(TripViewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
