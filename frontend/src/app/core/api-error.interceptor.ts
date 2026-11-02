import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { Session } from './session';

/** Session expirée ou révoquée (401) : retour à la connexion. Accès refusé (403) : retour à son espace. */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const session = inject(Session);
  return next(req).pipe(
    catchError((error: unknown) => {
      // Pendant le démarrage (aucun utilisateur chargé), Session.init gère lui-même les erreurs.
      if (error instanceof HttpErrorResponse && req.url.startsWith('/api/') && session.user()) {
        if (error.status === 401) {
          session.expire();
          router.navigateByUrl('/connexion');
        } else if (error.status === 403) {
          router.navigateByUrl(session.homeUrl());
        }
      }
      return throwError(() => error);
    }),
  );
};
