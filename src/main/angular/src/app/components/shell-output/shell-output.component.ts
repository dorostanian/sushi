import { Component } from '@angular/core';
import {ShellOutputService} from "../../services/shell-output.service";

@Component({
  selector: 'app-shell-output',
  templateUrl: './shell-output.component.html',
  styleUrls: ['./shell-output.component.scss']
})
export class ShellOutputComponent {

  public output = '';

  constructor(shellOutputService: ShellOutputService) {
    shellOutputService.getShellOutput().subscribe(message => {
      this.output += message;
    });
  }

}
