# Mise en production

Tout le portail tourne en conteneurs Docker derrière un seul point d'entrée HTTPS (Nginx). Fichiers :

| Fichier | Rôle |
|---|---|
| `infra/docker-compose.prod.yml` | PostgreSQL, Keycloak, SeaweedFS (S3), simulateur, backend, Nginx + application, sauvegardes |
| `infra/.env.prod.example` | Toutes les variables à renseigner (copie en `infra/.env.prod`, jamais commitée) |
| `frontend/Dockerfile`, `frontend/nginx.conf.template` | Build Angular + Nginx (TLS, en-têtes de sécurité, reverse proxy) |
| `backend/Dockerfile`, `simulator/Dockerfile` | Images Java 21, utilisateur non root |
| `infra/keycloak/prod/epayment-realm.json` | Realm de production, généré par `node infra/keycloak/make-prod-realm.mjs` |
| `infra/backup/` | Image de sauvegarde quotidienne, `backup.sh`, `restore.sh` |

## Organisation des URL

Un seul domaine, par exemple `https://epayment.example.mr` :

| Chemin | Service |
|---|---|
| `/` | Application Angular |
| `/api/` | Backend (`/api/public/` limité à 30 requêtes par minute et par IP) |
| `/auth/` | Keycloak (connexion, MFA) ; la console `/auth/admin/` n'est **pas** exposée |
| `/simulateur/` | Simulateur des fournisseurs (page de paiement par carte) |
| `/actuator/health` | Sonde de santé ; le reste d'Actuator renvoie 404 |

Seuls les ports 80 et 443 sont publiés ; HTTP redirige vers HTTPS. Base, Keycloak, S3 et backend ne sont
joignables que sur le réseau Docker interne.

## Premier déploiement

1. Serveur Linux avec Docker et Docker Compose, nom de domaine pointant vers le serveur, ports 80 et 443 ouverts.
2. `cp infra/.env.prod.example infra/.env.prod`, puis remplacer chaque `a-changer`
   (`openssl rand -base64 32` pour les secrets).
3. Certificat TLS dans `infra/certs/` sous les noms `fullchain.pem` et `privkey.pem` :
   - production : Let's Encrypt, par exemple `certbot certonly --standalone -d epayment.example.mr`
     (Nginx arrêté), puis copier `/etc/letsencrypt/live/<domaine>/fullchain.pem` et `privkey.pem` ;
     renouvellement : `certbot renew` puis `docker compose … restart nginx` (tâche cron mensuelle) ;
   - recette : `sh infra/certs/generate-self-signed.sh <domaine>` (avertissement du navigateur).
4. Certificat de scellement PKCS#12 fourni par l'organisation dans `infra/secrets/`, et
   `SIGNATURE_SEAL_KEYSTORE=/run/secrets/epayment/<fichier>.p12`. Sans lui, le certificat auto-signé de
   développement est utilisé (signature valide mais identité « Inconnu », voir l'avancement).
5. Démarrage : `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod up -d --build`.
6. Première connexion avec `KEYCLOAK_FIRST_ADMIN_EMAIL` et le mot de passe temporaire : Keycloak impose
   un nouveau mot de passe puis l'enrôlement TOTP.

Le realm de production ne contient **aucun compte de démonstration** ; ses secrets, URL et paramètres SMTP
sont des placeholders `${VAR}` remplacés par Keycloak à l'import (au premier démarrage seulement : ensuite,
les modifications se font dans Keycloak).

## Variables

Voir `infra/.env.prod.example`, commenté. Les secrets applicatifs obligatoires :

- `DB_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_BACKEND_SECRET`
- `SIGNATURE_OTP_SECRET` (clé HMAC des codes de signature)
- `RECEIPT_VERIFICATION_SECRET` (clé HMAC du QR code de vérification des reçus)
- `S3_ACCESS_KEY`, `S3_SECRET_KEY`
- clés d'API et secrets de webhook des fournisseurs (identiques côté portail et simulateur)
- SMTP : `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM_ADDRESS`

Changer `RECEIPT_VERIFICATION_SECRET` invalide les QR codes des reçus déjà émis.

## Sécurité côté Nginx

- TLS 1.2 et 1.3, HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`.
- Application : `Content-Security-Policy` stricte (scripts du domaine uniquement), `X-Frame-Options: DENY`.
- `index.html` jamais mis en cache ; fichiers versionnés d'Angular en cache un an.
- IP réelle transmise au backend (`X-Forwarded-For`) et à Keycloak (`KC_PROXY_HEADERS=xforwarded`).

## Sauvegardes et restauration

Le service `backup` lance `backup.sh` chaque jour à `BACKUP_TIME` (heure de Nouakchott) :

1. dump PostgreSQL au format custom, vérifié par `pg_restore --list` ;
2. copie des documents S3 (contrats, PDF signés, reçus) ;
3. si `OFFSITE_S3_BUCKET` est renseigné, copie vers un stockage externe (**recommandé** : une sauvegarde
   sur le même serveur ne protège pas d'une perte du serveur) ;
4. suppression des dumps de plus de `BACKUP_RETENTION_DAYS` jours.

Sauvegarde immédiate : `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod exec backup backup.sh`.

Restauration (remplace la base actuelle) :

```sh
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod stop backend
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod exec backup \
  restore.sh /backups/db/epayment-<date>.dump
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod start backend
```

La base `keycloak` (comptes, MFA) est sauvegardée en même temps (`EXTRA_DB_NAMES`) ; la restaurer avec
`restore.sh /backups/db/keycloak-<date>.dump keycloak` (Keycloak arrêté).

## Contrôles avant mise en ligne

- `backend/mvnw verify` avec Docker disponible.
- `frontend/npm ci` puis `npx ng test --watch=false` et `npx ng build`.
- Tests de bout en bout Playwright sur la recette.
- Vérification manuelle du flux Keycloak, de la signature PAdES et d'un paiement de chaque moyen.
- Scan OWASP ZAP sur l'URL de recette, puis correction et nouvelle analyse des alertes retenues.
- Sauvegarde puis restauration testées sur un environnement séparé.

## Tests de bout en bout (Playwright)

Scénarios dans `frontend/e2e/` : pages publiques (statut, vérification de reçu, langue arabe, routes protégées),
espace abonné (accueil, paiement Bankily et reçu, export CSV, arabe et mode sombre), espace admin (tableau de
bord, état des fournisseurs, recherche Ctrl+K, compteurs, audit) et absence de défilement horizontal sur mobile.

```sh
cd frontend
npx playwright install chromium          # une seule fois
# Développement (comptes de démonstration, MFA admin désactivée : infra/keycloak/mfa-admin.sh off)
npm run e2e
# Recette HTTPS : administrateur de test créé dans Keycloak (la MFA reste active, le test calcule le TOTP)
sh ../infra/keycloak/create-e2e-admin.sh e2e-admin@epayment.mr '<mot de passe>'
E2E_BASE_URL=https://<recette> E2E_ADMIN_EMAIL=e2e-admin@epayment.mr E2E_ADMIN_PASSWORD='<mot de passe>' \
  npx playwright test e2e/public.spec.ts e2e/admin.spec.ts e2e/responsive.spec.ts
```

Les scénarios abonné demandent un abonné avec un contrat actif (`E2E_ABONNE_EMAIL`, `E2E_ABONNE_PASSWORD`).
Rapport HTML : `frontend/e2e-report/`.

## Scan de sécurité (OWASP ZAP)

```sh
sh infra/security/zap-baseline.sh https://<recette>
```

Scan passif (aucune attaque) ; rapports dans `infra/security/reports/`. Une alerte acceptée en connaissance de
cause est ajoutée à `infra/security/zap-rules.tsv` avec sa justification.
