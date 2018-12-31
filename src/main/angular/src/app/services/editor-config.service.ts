import { Injectable } from '@angular/core';
import {BehaviorSubject, Observable, Subject} from "rxjs";
import {EditorConfig} from "../models/EditorConfig";

import {cloneDeep} from 'lodash';

@Injectable({
  providedIn: 'root'
})
export class EditorConfigService {
  private editorConfig: EditorConfig = {
    editorMode: 'graph',
    addingEdge: false
  };

  private editorConfigSubject = new BehaviorSubject<EditorConfig>(cloneDeep(this.editorConfig));
  private addBlockSubject = new Subject<void>();

  public getEditorConfig(): Observable<EditorConfig> {
    return this.editorConfigSubject.asObservable();
  }

  public getAddBlockObservable(): Observable<void> {
    return this.addBlockSubject.asObservable();
  }

  public setEditorMode(mode: 'code' | 'graph' | 'both') {
    this.editorConfig.editorMode = mode;
    this.publishConfig();
  }

  public setAddingEdge(addingEdge: boolean) {
    this.editorConfig.addingEdge = addingEdge;
    this.publishConfig();
  }

  public addBlock() {
    this.addBlockSubject.next();
  }

  private publishConfig() {
    this.editorConfigSubject.next(cloneDeep(this.editorConfig));
  }
}
