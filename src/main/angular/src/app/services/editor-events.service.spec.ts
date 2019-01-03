import { TestBed } from '@angular/core/testing';

import { EditorEventsService } from './editor-events.service';

describe('EditorEventsService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: EditorEventsService = TestBed.get(EditorEventsService);
    expect(service).toBeTruthy();
  });
});
