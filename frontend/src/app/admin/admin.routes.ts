import { Routes } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';

import { AVenir } from '../shared/a-venir';
import { AdminLayout } from './admin-layout';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayout,
    providers: [MessageService, ConfirmationService],
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
        loadComponent: () => import('./abonnes/abonnes').then((m) => m.Abonnes),
      },
      {
        path: 'abonnes/:id',
        title: 'Fiche abonné · E-Payment',
        loadComponent: () => import('./abonnes/abonne-fiche').then((m) => m.AbonneFiche),
      },
      {
        path: 'moyens',
        title: 'Moyens de paiement · E-Payment',
        loadComponent: () => import('./moyens/moyens').then((m) => m.Moyens),
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
