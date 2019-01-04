import {Component} from '@angular/core';
import {EditorConfigService} from "../../services/editor-config.service";
import {EditorConfig} from "../../models/EditorConfig";
import {EditorEventsService} from "../../services/editor-events.service";

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.scss']
})
export class ToolbarComponent {
  public editorConfig: EditorConfig;

  constructor(private editorConfigService: EditorConfigService,
              private editorEventsService: EditorEventsService) {
    editorConfigService.getEditorConfig().subscribe(config => {
      this.editorConfig = config;
    });
  }

  public editorModeChanged() {
    this.editorConfigService.setEditorMode(this.editorConfig.editorMode);
  }

  public addBlock() {
    this.editorEventsService.emit('ADD_BLOCK');
  }

  public addEdge() {
    this.editorEventsService.emit('ADD_EDGE');
  }
}
