import { Injectable } from '@angular/core';
import {BehaviorSubject, Observable} from "rxjs";
import {EditorConfig} from "../models/EditorConfig";

import {cloneDeep} from 'lodash';

@Injectable({
  providedIn: 'root'
})
export class EditorConfigService {
  private editorConfig: EditorConfig = {
    editorMode: 'graph'
  };

  private editorConfigSubject = new BehaviorSubject<EditorConfig>(cloneDeep(this.editorConfig));

  public getEditorConfig(): Observable<EditorConfig> {
    return this.editorConfigSubject.asObservable();
  }

  public setEditorMode(mode: 'code' | 'graph' | 'both') {
    this.editorConfig.editorMode = mode;
    this.publishConfig();
  }

  private publishConfig() {
    this.editorConfigSubject.next(cloneDeep(this.editorConfig));
  }
}
