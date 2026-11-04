// Génère prod/epayment-realm.json à partir du realm de développement.
// À relancer après toute modification de epayment-realm.json : node infra/keycloak/make-prod-realm.mjs
// Les valeurs ${VAR} sont remplacées par Keycloak au démarrage, à partir des variables d'environnement.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const realm = JSON.parse(readFileSync(join(here, 'epayment-realm.json'), 'utf8'));

realm.sslRequired = 'all';
realm.smtpServer = {
  host: '${MAIL_HOST}',
  port: '${MAIL_PORT}',
  from: '${MAIL_FROM_ADDRESS}',
  fromDisplayName: 'Portail E-Payment',
  auth: '${MAIL_AUTH}',
  user: '${MAIL_USER}',
  password: '${MAIL_PASSWORD}',
  ssl: 'false',
  starttls: '${MAIL_STARTTLS}',
};

for (const client of realm.clients) {
  if (client.clientId === 'epayment-backend') client.secret = '${KEYCLOAK_BACKEND_SECRET}';
  if (client.clientId === 'epayment-frontend') {
    client.rootUrl = '${PORTAL_URL}';
    client.redirectUris = ['${PORTAL_URL}/*'];
    client.webOrigins = ['${PORTAL_URL}'];
    client.attributes['post.logout.redirect.uris'] = '${PORTAL_URL}/*';
  }
}

// Pas de comptes de démonstration : un seul administrateur, mot de passe temporaire à changer
// à la première connexion (le TOTP est ensuite exigé pour le rôle ADMIN).
realm.users = realm.users
  .filter((u) => u.serviceAccountClientId || u.realmRoles?.includes('ADMIN'))
  .map((u) =>
    u.serviceAccountClientId
      ? u
      : {
          ...u,
          username: '${KEYCLOAK_FIRST_ADMIN_EMAIL}',
          email: '${KEYCLOAK_FIRST_ADMIN_EMAIL}',
          firstName: 'Administrateur',
          lastName: 'E-Payment',
          credentials: [{ type: 'password', value: '${KEYCLOAK_FIRST_ADMIN_PASSWORD}', temporary: true }],
        },
  );

writeFileSync(join(here, 'prod', 'epayment-realm.json'), JSON.stringify(realm, null, 2) + '\n');
console.log('prod/epayment-realm.json généré');
