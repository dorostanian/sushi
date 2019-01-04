import { Injectable } from '@angular/core';
import {HttpClient} from "@angular/common/http";

@Injectable({
  providedIn: 'root'
})
export class StateMachineService {
  constructor(private httpClient: HttpClient) { }



  public setCode(code: string) {

  }

  public setDigraph() {

  }
}
