import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { CodeEditorComponent } from './components/code-editor/code-editor.component';
import { GraphEditorComponent } from './components/graph-editor/graph-editor.component';
import { ShellOutputComponent } from './components/shell-output/shell-output.component';
import { ToolbarComponent } from './components/toolbar/toolbar.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { NgbCollapseModule } from "@ng-bootstrap/ng-bootstrap";

@NgModule({
  declarations: [
    AppComponent,
    CodeEditorComponent,
    GraphEditorComponent,
    ShellOutputComponent,
    ToolbarComponent,
    NavbarComponent
  ],
  imports: [
    BrowserModule,
    NgbCollapseModule
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
