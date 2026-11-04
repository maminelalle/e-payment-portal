// Configuration de production : Keycloak est servi par le même domaine, sous /auth (voir nginx.conf.template).
export const environment = {
  oidc: {
    authority: `${window.location.origin}/auth/realms/epayment`,
    clientId: 'epayment-frontend',
  },
};
