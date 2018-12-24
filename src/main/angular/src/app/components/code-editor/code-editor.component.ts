import { Component } from '@angular/core';
import {CodeEditorTab, containers, mainFlow} from "./code-editor.model";

@Component({
  selector: 'app-code-editor',
  templateUrl: './code-editor.component.html',
  styleUrls: ['./code-editor.component.scss']
})
export class CodeEditorComponent {
  public tabs: CodeEditorTab[] = [];
  public selectedTab: CodeEditorTab = null;

  constructor() {
    this.tabs.push({
      title: 'Main Flow',
      text: mainFlow
    });
    this.tabs.push({
      title: 'Containers',
      text: containers
    });
    this.selectedTab = this.tabs[0];
  }

  public selectTab(tab: CodeEditorTab) {
    this.selectedTab = tab;
  }
}


