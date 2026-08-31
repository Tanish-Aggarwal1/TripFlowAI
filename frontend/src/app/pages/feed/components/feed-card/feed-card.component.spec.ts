import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedCardComponent } from './feed-card.component';
import { FeedTrip } from '../../../../core/models/feed.model';

describe('FeedCardComponent', () => {
  let fixture: ComponentFixture<FeedCardComponent>;

  function makeTrip(overrides: Partial<FeedTrip> = {}): FeedTrip {
    return {
      id: 1,
      title: 'Coastal Road Trip',
      description: 'A scenic drive down the coast.',
      tags: [],
      ownerUsername: 'alice',
      likeCount: 3,
      createdAt: '2026-01-01T00:00:00Z',
      stops: [
        { id: 1, name: 'Stop One', address: '123 Main St', stopOrder: 0, notes: 'Great view', photoUrls: ['https://example.com/a.jpg'] },
        { id: 2, name: 'Stop Two', address: null, stopOrder: 1, notes: 'Second stop', photoUrls: ['https://example.com/b.jpg'] },
        { id: 3, name: 'Stop Three', address: null, stopOrder: 2, notes: 'Third stop', photoUrls: ['https://example.com/c.jpg'] },
      ],
      ...overrides,
    };
  }

  async function createFixture(trip: FeedTrip): Promise<ComponentFixture<FeedCardComponent>> {
    await TestBed.configureTestingModule({
      imports: [FeedCardComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
    }).compileComponents();

    const fx = TestBed.createComponent(FeedCardComponent);
    fx.componentRef.setInput('trip', trip);
    fx.detectChanges();
    return fx;
  }

  it('renders three inner swiper-slide elements with images when all stops have photos', async () => {
    fixture = await createFixture(makeTrip());

    const slides = fixture.nativeElement.querySelectorAll('swiper-slide');
    const images = fixture.nativeElement.querySelectorAll('img.stop-photo');
    expect(slides.length).toBe(3);
    expect(images.length).toBe(3);
  });

  it('renders a text block with stop name/notes and zero img elements when all stops have no photos', async () => {
    const trip = makeTrip({
      stops: [
        { id: 1, name: 'Empty Stop A', address: null, stopOrder: 0, notes: 'Nothing to see yet', photoUrls: [] },
        { id: 2, name: 'Empty Stop B', address: null, stopOrder: 1, notes: null, photoUrls: [] },
      ],
    });
    fixture = await createFixture(trip);

    const images = fixture.nativeElement.querySelectorAll('img');
    expect(images.length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Empty Stop A');
    expect(fixture.nativeElement.textContent).toContain('Nothing to see yet');
    expect(fixture.nativeElement.textContent).toContain('Empty Stop B');
  });

  it('independently renders photo or text form per stop in a mixed trip', async () => {
    const trip = makeTrip({
      stops: [
        { id: 1, name: 'Photo Stop', address: null, stopOrder: 0, notes: null, photoUrls: ['https://example.com/a.jpg'] },
        { id: 2, name: 'No Photo Stop', address: null, stopOrder: 1, notes: 'text only', photoUrls: [] },
      ],
    });
    fixture = await createFixture(trip);

    expect(fixture.nativeElement.querySelectorAll('img.stop-photo').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('No Photo Stop');
    expect(fixture.nativeElement.textContent).toContain('text only');
  });

  it('renders trip title, major location and owner username in the header region', async () => {
    fixture = await createFixture(makeTrip());

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Coastal Road Trip');
    expect(text).toContain('123 Main St');
    expect(text).toContain('alice');
  });

  it('renders the description in the footer region when present', async () => {
    fixture = await createFixture(makeTrip({ description: 'A scenic drive down the coast.' }));
    expect(fixture.nativeElement.querySelector('.bottom-overlay')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('A scenic drive down the coast.');
  });

  it('renders nothing in the footer region when the description is null', async () => {
    fixture = await createFixture(makeTrip({ description: null }));
    expect(fixture.nativeElement.querySelector('.bottom-overlay')).toBeFalsy();
    expect(fixture.nativeElement.textContent).not.toContain('null');
  });

  it('keeps the header trip title unchanged after the inner slide index changes', async () => {
    fixture = await createFixture(makeTrip());
    const titleBefore = fixture.nativeElement.querySelector('.trip-title').textContent;

    const innerSwiper = fixture.nativeElement.querySelector('swiper-container') as any;
    innerSwiper.dispatchEvent(new CustomEvent('swiperslidechange', { detail: [{ activeIndex: 2 }] }));
    fixture.detectChanges();

    const titleAfter = fixture.nativeElement.querySelector('.trip-title').textContent;
    expect(titleAfter).toBe(titleBefore);
    expect(titleAfter).toContain('Coastal Road Trip');
  });
});
