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
