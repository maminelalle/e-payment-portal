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
  logLevel: LogLevel.Warn,
};
