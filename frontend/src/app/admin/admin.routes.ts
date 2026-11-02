import { Routes } from '@angular/router';

import { AVenir } from '../shared/a-venir';
import { AdminLayout } from './admin-layout';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayout,
    children: [
      {
        path: '',
        title: 'Tableau de bord · E-Payment',
        component: AVenir,
        data: { titre: 'Tableau de bord', semaine: 'semaine 11' },
      },
      {
        path: 'abonnes',
        title: 'Abonnés · E-Payment',
        component: AVenir,
        data: { titre: 'Abonnés', semaine: 'semaine 4' },
      },
      {
        path: 'moyens',
        title: 'Moyens de paiement · E-Payment',
        component: AVenir,
        data: { titre: 'Moyens de paiement', semaine: 'semaine 4' },
      },
      {
        path: 'contrats',
        title: 'Contrats · E-Payment',
        component: AVenir,
        data: { titre: 'Contrats', semaine: 'semaine 5' },
      },
      {
        path: 'transactions',
        title: 'Transactions · E-Payment',
        component: AVenir,
        data: { titre: 'Transactions', semaine: 'semaine 10' },
      },
      {
        path: 'audit',
        title: 'Paramètres et audit · E-Payment',
        component: AVenir,
        data: { titre: 'Paramètres et audit', semaine: 'semaine 11' },
      },
    ],
  },
];
