import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SignupPage } from './signup.page';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('SignupPage', () => {
  let component: SignupPage;
  let fixture: ComponentFixture<SignupPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupPage],
      providers: [
        provideHttpClient(),
        provideHttpClient(),
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SignupPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
