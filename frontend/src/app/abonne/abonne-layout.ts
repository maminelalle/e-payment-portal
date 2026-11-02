import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Session } from '../core/session';

@Component({
  selector: 'app-abonne-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './abonne-layout.html',
  styleUrl: './abonne-layout.scss',
})
export class AbonneLayout {
  protected readonly session = inject(Session);

  protected readonly menu = [
    { libelle: 'Accueil', lien: '/abonne', exact: true },
    { libelle: 'Mon contrat', lien: '/abonne/contrat', exact: false },
    { libelle: 'Moyens de paiement', lien: '/abonne/moyens', exact: false },
    { libelle: 'Transactions', lien: '/abonne/transactions', exact: false },
  ];
}
