import { Injectable } from '@angular/core';
import {Observable, Subject} from "rxjs";

export type EditorEvent = 'ADD_BLOCK' | 'ADD_EDGE';

@Injectable({
  providedIn: 'root'
})
export class EditorEventsService {
  private eventsSubject = new Subject<EditorEvent>();

  public events(): Observable<EditorEvent> {
    return this.eventsSubject.asObservable();
  }

  public emit(event: EditorEvent) {
    this.eventsSubject.next(event);
  }
}
