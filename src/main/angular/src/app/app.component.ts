import { Component } from '@angular/core';
import {EditorConfigService} from "./services/editor-config.service";
import {EditorConfig} from "./models/EditorConfig";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {

  public codeEditorContainerClass: string = '';

  constructor(editorConfigService: EditorConfigService) {
    editorConfigService.getEditorConfig().subscribe(config => this.processConfig(config));
  }

  private processConfig(config: EditorConfig) {
    switch(config.editorMode) {
      case 'graph': this.codeEditorContainerClass = 'closed'; break;
      case 'code': this.codeEditorContainerClass = 'full'; break;
      default: this.codeEditorContainerClass = 'normal';
    }
  }
}
