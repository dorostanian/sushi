import {Component} from '@angular/core';
import {EditorConfigService} from "../../services/editor-config.service";

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.scss']
})
export class ToolbarComponent {
  public editorMode: 'code' | 'graph' | 'both';

  constructor(private editorConfigService: EditorConfigService) {
    editorConfigService.getEditorConfig().subscribe(config => {
      this.editorMode = config.editorMode;
    });
  }

  public editorModeChanged() {
    this.editorConfigService.setEditorMode(this.editorMode);
  }
}
