import { Component, input } from '@angular/core';

/** Page provisoire pour les écrans pas encore développés (voir docs/04-plan-12-semaines.md). */
@Component({
  selector: 'app-a-venir',
  template: `
    <h1 class="page-title">{{ titre() }}</h1>
    <p class="page-subtitle">Écran prévu en {{ semaine() }}.</p>
  `,
})
export class AVenir {
  readonly titre = input('');
  readonly semaine = input('');
}
