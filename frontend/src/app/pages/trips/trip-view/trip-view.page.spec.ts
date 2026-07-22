import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TripViewPage } from './trip-view.page';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';

describe('TripViewPage', () => {
  let component: TripViewPage;
  let fixture: ComponentFixture<TripViewPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TripViewPage],
      providers: [
        provideHttpClient(),
        provideHttpClient(),
        provideRouter([]),
        provideIonicAngular(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TripViewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
