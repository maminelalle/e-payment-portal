import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { I18n } from './core/i18n';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  constructor(_: I18n) {}
}
