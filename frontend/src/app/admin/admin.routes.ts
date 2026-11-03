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
        loadComponent: () => import('./tableau-de-bord/tableau-de-bord').then((m) => m.TableauDeBord),
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
        loadComponent: () => import('./contrats/contrats').then((m) => m.Contrats),
      },
      {
        path: 'contrats/nouveau',
        title: 'Nouveau contrat · E-Payment',
        loadComponent: () => import('./contrats/contrat-assistant').then((m) => m.ContratAssistant),
      },
      {
        path: 'contrats/:id/modifier',
        title: 'Modifier le contrat · E-Payment',
        loadComponent: () => import('./contrats/contrat-assistant').then((m) => m.ContratAssistant),
      },
      {
        path: 'contrats/:id',
        title: 'Contrat · E-Payment',
        loadComponent: () => import('./contrats/contrat-fiche').then((m) => m.ContratFiche),
      },
      {
        path: 'transactions',
        title: 'Transactions · E-Payment',
        loadComponent: () => import('./transactions/transactions').then((m) => m.AdminTransactions),
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
