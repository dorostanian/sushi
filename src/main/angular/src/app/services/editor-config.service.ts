import { Injectable } from '@angular/core';
import {BehaviorSubject, Observable} from "rxjs";
import {EditorConfig} from "../models/EditorConfig";

import {clone} from 'lodash';

@Injectable({
  providedIn: 'root'
})
export class EditorConfigService {
  private editorConfig: EditorConfig = {
    editorMode: 'both'
  };

  private editorConfigSubject = new BehaviorSubject<EditorConfig>(clone(this.editorConfig));

  public getEditorConfig(): Observable<EditorConfig> {
    return this.editorConfigSubject.asObservable();
  }

  public setEditorMode(mode: 'code' | 'graph' | 'both') {
    this.editorConfig.editorMode = mode;
    this.publishConfig();
  }

  private publishConfig() {
    this.editorConfigSubject.next(clone(this.editorConfig));
  }
}
