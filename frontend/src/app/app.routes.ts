import { Routes } from '@angular/router';

import { anonymousOnlyGuard, homeRedirectGuard, roleGuard } from './core/guards';

export const routes: Routes = [
  // La racine reçoit aussi le retour de Keycloak, puis envoie chacun vers son espace.
  { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard], children: [] },
  {
    path: 'connexion',
    title: 'Connexion · E-Payment',
    canActivate: [anonymousOnlyGuard],
    loadComponent: () => import('./connexion/connexion').then((m) => m.Connexion),
  },
  // Pages publiques, sans connexion : état du service et vérification d'un reçu (QR code).
  {
    path: 'statut',
    title: 'État du service · E-Payment',
    loadComponent: () => import('./public/statut').then((m) => m.Statut),
  },
  {
    path: 'verification/recu/:reference',
    title: 'Vérification d’un reçu · E-Payment',
    loadComponent: () => import('./public/verification-recu').then((m) => m.VerificationRecu),
  },
  {
    path: 'admin',
    canMatch: [roleGuard('ADMIN')],
    loadChildren: () => import('./admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: 'abonne',
    canMatch: [roleGuard('ABONNE')],
    loadChildren: () => import('./abonne/abonne.routes').then((m) => m.ABONNE_ROUTES),
  },
  { path: '**', redirectTo: '' },
];
