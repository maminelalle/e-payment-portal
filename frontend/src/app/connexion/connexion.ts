import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

import { Session } from '../core/session';
import { Preferences } from '../core/preferences';

@Component({
  selector: 'app-connexion',
  imports: [ButtonModule, RouterLink],
  templateUrl: './connexion.html',
  styleUrl: './connexion.scss',
})
export class Connexion {
  protected readonly session = inject(Session);
  protected readonly preferences = inject(Preferences);

  protected readonly atouts = [
    'Signature électronique du contrat',
    'Accès limité aux moyens prévus au contrat',
    'Historique et reçu pour chaque transaction',
  ];
}
