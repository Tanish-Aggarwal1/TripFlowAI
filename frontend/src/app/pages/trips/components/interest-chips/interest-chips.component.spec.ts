import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InterestChipsComponent } from './interest-chips.component';
import { expectNoA11yViolations, expectAllFormControlsLabeled } from '../../../../../testing/a11y';

describe('InterestChipsComponent', () => {
  let component: InterestChipsComponent;
  let fixture: ComponentFixture<InterestChipsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InterestChipsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(InterestChipsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('has no accessibility violations', async () => {
    await expectNoA11yViolations(fixture.nativeElement);
  });

  it('labels the interest input', () => {
    expectAllFormControlsLabeled(fixture.nativeElement);
  });

  describe('addInterest', () => {
    it('emits a trimmed interest and clears the input', () => {
      spyOn(component.interestsChange, 'emit');
      component.interestInput = '  hiking  ';

      component.addInterest();

      expect(component.interestsChange.emit).toHaveBeenCalledWith(['hiking']);
      expect(component.interestInput).toBe('');
    });

    it('ignores blank input', () => {
      spyOn(component.interestsChange, 'emit');
      component.interestInput = '   ';

      component.addInterest();

      expect(component.interestsChange.emit).not.toHaveBeenCalled();
    });

    it('rejects an interest longer than 50 characters', () => {
      spyOn(component.interestsChange, 'emit');
      component.interestInput = 'a'.repeat(51);

      component.addInterest();

      expect(component.interestsChange.emit).not.toHaveBeenCalled();
      expect(component.error).toBe('Each interest must be at most 50 characters.');
    });

    it('rejects an 11th interest (max 10)', () => {
      spyOn(component.interestsChange, 'emit');
      component.interests = Array.from({ length: 10 }, (_, i) => `interest-${i}`);
      component.interestInput = 'one-too-many';

      component.addInterest();

      expect(component.interestsChange.emit).not.toHaveBeenCalled();
      expect(component.error).toBe('At most 10 interests are allowed.');
    });

    // SOCIAL-05: the profile page overrides maxInterests to 20 (Trip.tags's backend limit).
    it('honors a caller-supplied maxInterests instead of the default 10', () => {
      spyOn(component.interestsChange, 'emit');
      component.maxInterests = 20;
      component.interests = Array.from({ length: 20 }, (_, i) => `interest-${i}`);
      component.interestInput = 'one-too-many';

      component.addInterest();

      expect(component.interestsChange.emit).not.toHaveBeenCalled();
      expect(component.error).toBe('At most 20 interests are allowed.');
    });

    it('does not emit a duplicate interest', () => {
      spyOn(component.interestsChange, 'emit');
      component.interests = ['food'];
      component.interestInput = 'food';

      component.addInterest();

      expect(component.interestsChange.emit).not.toHaveBeenCalled();
    });
  });

  describe('removeInterest', () => {
    it('emits the list without the given interest', () => {
      spyOn(component.interestsChange, 'emit');
      component.interests = ['food', 'hiking'];

      component.removeInterest('food');

      expect(component.interestsChange.emit).toHaveBeenCalledWith(['hiking']);
    });
  });

  describe('chip removal is keyboard-operable', () => {
    it('renders a focusable ion-button with an accessible name for each chip', () => {
      component.interests = ['food', 'hiking'];
      fixture.detectChanges();

      const buttons: HTMLElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('ion-chip ion-button'),
      );

      expect(buttons.length).toBe(2);
      expect(buttons[0].getAttribute('aria-label')).toBe('Remove food');
      expect(buttons[1].getAttribute('aria-label')).toBe('Remove hiking');
    });

    it('removes the interest when its button is activated, without a handler on the chip itself', () => {
      spyOn(component.interestsChange, 'emit');
      component.interests = ['food', 'hiking'];
      fixture.detectChanges();

      const chip: HTMLElement = fixture.nativeElement.querySelector('ion-chip');
      const button: HTMLElement = chip.querySelector('ion-button')!;

      button.dispatchEvent(new MouseEvent('click', { bubbles: true }));

      expect(component.interestsChange.emit).toHaveBeenCalledWith(['hiking']);
    });
  });
});
