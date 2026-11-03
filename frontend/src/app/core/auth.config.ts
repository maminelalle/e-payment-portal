import { LogLevel, OpenIdConfiguration } from 'angular-auth-oidc-client';

import { environment } from '../../environments/environment';

/** Client public Keycloak, flux « authorization code + PKCE », jeton envoyé uniquement à /api. */
export const authConfig: OpenIdConfiguration = {
  authority: environment.oidc.authority,
  clientId: environment.oidc.clientId,
  redirectUrl: window.location.origin,
  postLogoutRedirectUri: `${window.location.origin}/connexion`,
  responseType: 'code',
  scope: 'openid profile email',
  silentRenew: true,
  useRefreshToken: true,
  renewTimeBeforeTokenExpiresInSeconds: 30,
  secureRoutes: ['/api/'],
  // La bibliothèque ne navigue pas elle-même après le retour de Keycloak : sinon elle redirige vers « / »
  // pendant Session.init, avant le chargement du profil, et l'utilisateur retombe sur /connexion.
  // C'est la route racine (homeRedirectGuard) qui oriente vers l'espace du rôle une fois la session prête.
  triggerAuthorizationResultEvent: true,
  // Le portail utilise des jetons de rafraîchissement de session, sans accès hors ligne.
  disableRefreshTokenOfflineAccessScopeWarning: true,
  logLevel: LogLevel.Warn,
};
