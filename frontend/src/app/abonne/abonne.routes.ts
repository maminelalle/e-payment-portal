import { Routes } from '@angular/router';
import { MessageService } from 'primeng/api';

import { AVenir } from '../shared/a-venir';
import { AbonneLayout } from './abonne-layout';

export const ABONNE_ROUTES: Routes = [
  {
    path: '',
    component: AbonneLayout,
    providers: [MessageService],
    children: [
      {
        path: '',
        title: 'Accueil · E-Payment',
        component: AVenir,
        data: { titre: 'Accueil', semaine: 'semaine 7' },
      },
      {
        path: 'contrat',
        title: 'Mon contrat · E-Payment',
        loadComponent: () => import('./mon-contrat/mon-contrat').then((m) => m.MonContrat),
      },
      {
        path: 'moyens',
        title: 'Moyens de paiement · E-Payment',
        component: AVenir,
        data: { titre: 'Moyens de paiement', semaine: 'semaine 7' },
      },
      {
        path: 'transactions',
        title: 'Transactions · E-Payment',
        component: AVenir,
        data: { titre: 'Transactions', semaine: 'semaine 10' },
      },
      {
        path: 'profil',
        title: 'Profil · E-Payment',
        component: AVenir,
        data: { titre: 'Profil', semaine: 'semaine 11' },
      },
    ],
  },
];
