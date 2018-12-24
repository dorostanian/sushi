import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ShellOutputComponent } from './shell-output.component';

describe('ShellOutputComponent', () => {
  let component: ShellOutputComponent;
  let fixture: ComponentFixture<ShellOutputComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ShellOutputComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ShellOutputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
