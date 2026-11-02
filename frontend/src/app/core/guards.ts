import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';

import { Role, Session } from './session';

/** Réserve un espace à un rôle. Sans session : page de connexion ; mauvais rôle : son propre espace. */
export function roleGuard(role: Role): CanMatchFn {
  return () => {
    const session = inject(Session);
    const router = inject(Router);
    if (!session.user()) {
      return router.parseUrl('/connexion');
    }
    return session.hasRole(role) ? true : router.parseUrl(session.homeUrl());
  };
}

/** Redirige la racine (et le retour de Keycloak) vers l'espace de l'utilisateur. */
export const homeRedirectGuard: CanActivateFn = () => inject(Router).parseUrl(inject(Session).homeUrl());

/** Un utilisateur déjà connecté ne revoit pas la page de connexion. */
export const anonymousOnlyGuard: CanActivateFn = () => {
  const session = inject(Session);
  return session.user() ? inject(Router).parseUrl(session.homeUrl()) : true;
};
