import { Routes } from '@angular/router';

// Les guards par rôle (ADMIN / ABONNE) et la connexion OIDC arrivent en semaine 3.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'connexion' },
  {
    path: 'connexion',
    title: 'Connexion · E-Payment',
    loadComponent: () => import('./connexion/connexion').then((m) => m.Connexion),
  },
  { path: 'admin', loadChildren: () => import('./admin/admin.routes').then((m) => m.ADMIN_ROUTES) },
  { path: 'abonne', loadChildren: () => import('./abonne/abonne.routes').then((m) => m.ABONNE_ROUTES) },
  { path: '**', redirectTo: 'connexion' },
];
