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
});
