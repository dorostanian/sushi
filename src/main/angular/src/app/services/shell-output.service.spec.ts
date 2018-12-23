import { TestBed } from '@angular/core/testing';

import { ShellOutputService } from './shell-output.service';

describe('ShellOutputService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: ShellOutputService = TestBed.get(ShellOutputService);
    expect(service).toBeTruthy();
  });
});
