import {Component} from '@angular/core';
import {EditorConfigService} from "../../services/editor-config.service";
import {EditorConfig} from "../../models/EditorConfig";

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.scss']
})
export class ToolbarComponent {
  public editorConfig: EditorConfig;

  constructor(private editorConfigService: EditorConfigService) {
    editorConfigService.getEditorConfig().subscribe(config => {
      this.editorConfig = config;
    });
  }

  public editorModeChanged() {
    this.editorConfigService.setEditorMode(this.editorConfig.editorMode);
  }

  public addingEdgeChanged() {
    this.editorConfigService.setAddingEdge(this.editorConfig.addingEdge);
  }

  public addBlock() {
    this.editorConfigService.addBlock();
  }
}
