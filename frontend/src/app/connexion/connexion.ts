import { Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';

import { Session } from '../core/session';

@Component({
  selector: 'app-connexion',
  imports: [ButtonModule],
  templateUrl: './connexion.html',
  styleUrl: './connexion.scss',
})
export class Connexion {
  protected readonly session = inject(Session);

  protected readonly atouts = [
    'Signature électronique du contrat',
    'Accès limité aux moyens prévus au contrat',
    'Historique et reçu pour chaque transaction',
  ];
}
