import {Injectable} from '@angular/core';
import {Observable, Subject} from "rxjs";
import {map} from "rxjs/operators";

@Injectable({
  providedIn: 'root'
})
export class ShellOutputService {

  // private websocketSubject: WebSocketSubject<WebSocketMessage> = WebSocketSubject.create('ws://localhost:8080/ws');
  private websocketSubject = new Subject<WebSocketMessage>();
  private timeout = 250;

  public constructor() {
    this.publishMockMessage();
  }

  public publishMockMessage() {
    this.websocketSubject.next({
      first: '',
      second: `> gcloud config set project PROJECT_ID\n`
    });
    this.timeout *= 2;
    setTimeout(() => this.publishMockMessage(), this.timeout);
  }

  public getShellOutput(): Observable<string> {
    return this.websocketSubject.asObservable().pipe(
      map(msg => msg.second)
    );
  }
}

interface WebSocketMessage {
  first: string;
  second: string;
}

