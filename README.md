# Portail E-Payment

Portail web où **le contrat signé est la seule source des droits** : chaque abonné ne voit et n'utilise que les moyens de paiement (Bankily, Masrvi, Visa) inscrits dans son contrat actif.

- **Espace Administrateur** : abonnés, catalogue des moyens, contrats, transactions, audit.
- **Espace Abonné** : consulter et signer son contrat (OTP), payer avec les moyens autorisés, historique et reçus.

## Stack

| Couche | Choix |
|--------|-------|
| Frontend | Angular 21 + PrimeNG 21 |
| Backend | Java 21, Spring Boot 4.1, Spring Modulith 2.1 (monolithe modulaire) |
| Données | PostgreSQL 16 + Flyway |
| Authentification | Keycloak 26 (OIDC) + Spring Security |
| Documents | Stockage S3 (SeaweedFS en local), Thymeleaf + OpenHTMLtoPDF, EU DSS (PAdES) |
| Emails (dev) | Mailpit |
| Paiements | Simulateur Bankily / Masrvi / Visa (webhooks HMAC) |
| Déploiement | Docker Compose + Nginx ; CI GitHub Actions |

## Structure du dépôt

```
backend/     API Spring Boot (modules : auth, subscriber, catalog, contract, signature,
             entitlement, gateway, transaction, notification, audit)
frontend/    Application Angular (espaces /admin et /abonne)
simulator/   Simulateur des fournisseurs Bankily, Masrvi, passerelle Visa (port 8090)
infra/       Docker Compose, realm Keycloak, Nginx, stockage S3
docs/        Cadrage, modèle de données, contrats d'API, maquettes
```

## Documentation

| Document | Contenu |
|----------|---------|
| [docs/00-decisions.md](docs/00-decisions.md) | Hypothèses métier et décisions techniques |
| [docs/01-ecrans.md](docs/01-ecrans.md) | Inventaire des écrans et charte graphique |
| [docs/02-modele-donnees.md](docs/02-modele-donnees.md) | Modèle de données (13 entités) |
| [docs/03-api-fournisseurs-simules.md](docs/03-api-fournisseurs-simules.md) | Contrat d'API du simulateur |
| [docs/04-plan-12-semaines.md](docs/04-plan-12-semaines.md) | Plan et avancement |
| [docs/references/](docs/references/) | Conception, étude et checklist d'origine |
| [docs/maquettes/](docs/maquettes/) | Maquettes des écrans |

## Lancer le projet en local

### Prérequis

- Docker Desktop
- JDK 21 ou plus récent (le code cible Java 21)
- Node.js 24 (≥ 24.15 pour passer plus tard à Angular 22)

### 1. Services d'infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d
```

| Service | URL | Identifiants (dev) |
|---------|-----|--------------------|
| Portail (via Nginx) | http://localhost | — |
| Keycloak | http://localhost:8180 | console : `admin` / `admin` |
| Mailpit (emails de test) | http://localhost:8025 | — |
| PostgreSQL | `localhost:5433`, base `epayment` | `epayment` / `epayment` |
| Stockage S3 (SeaweedFS) | http://localhost:8333 | `epayment-dev` / `epayment-dev-secret` |

Comptes de démonstration du realm `epayment` :

| Rôle | Email | Mot de passe |
|------|-------|--------------|
| ADMIN | `admin@epayment.mr` | `Admin#Demo2026` |
| ABONNE | `contact@exemple.mr` | `Abonne#Demo2026` |
| ABONNE | `atlas@exemple.mr` (Société Atlas, ABN-0001, créée via le portail) | `Atlas#Demo2026` |

`contact@exemple.mr` n'existe que dans Keycloak (compte de test de la connexion) ; les vrais abonnés se créent depuis l'écran **Abonnés** de l'admin.

### Créer un abonné (parcours complet)

1. Admin → **Abonnés** → **Nouvel abonné** : le portail crée la fiche (statut « en attente ») et le compte Keycloak (rôle ABONNE).
2. L'email d'invitation « Mettre à jour votre compte » arrive dans Mailpit (http://localhost:8025) ; le lien (valable 72 h) permet de définir le mot de passe.
3. L'abonné se connecte sur http://localhost. L'admin l'**active** depuis sa fiche ; **Suspendre** bloque la connexion et ferme ses sessions.

Le realm livré exige un code TOTP pour le rôle ADMIN (Google Authenticator, FreeOTP, Microsoft Authenticator…). Pour tester plus vite **en local**, on peut le couper puis le remettre (le fichier du realm n'est pas modifié) :

```bash
bash infra/keycloak/mfa-admin.sh off   # admin : mot de passe seul (TOTP existants retirés)
bash infra/keycloak/mfa-admin.sh on    # TOTP exigé à nouveau (pour la démo)
```

Le realm n'est importé qu'au premier démarrage. Après modification de `infra/keycloak/epayment-realm.json`, supprimer le realm `epayment` dans la console puis `docker restart epayment-keycloak-1`.

### API et sécurité

| Préfixe | Accès |
|---------|-------|
| `/api/me`, `/api/me/connexion` | Tout utilisateur connecté (profil, journalisation de la connexion) |
| `/api/admin/**` | Rôle `ADMIN` |
| `/api/abonne/**` | Rôle `ABONNE` |
| Tout autre chemin `/api/**` | Refusé |

Refus d'accès, connexions et requêtes admin modifiantes sont tracés dans `audit_log` (consultable via `GET /api/admin/audit`).

| API d'administration | Rôle |
|----------------------|------|
| `GET/POST /api/admin/abonnes`, `GET/PUT /api/admin/abonnes/{id}` | Liste filtrable (`q`, `statut`, `type`), création, fiche, modification |
| `POST /api/admin/abonnes/{id}/activer` · `/suspendre` · `/desactiver` · `/invitation` | Cycle de vie et renvoi de l'invitation |
| `GET /api/admin/abonnes/resume` | Compteurs par statut |
| `GET /api/admin/moyens`, `PATCH /api/admin/moyens/{code}/statut` | Catalogue des moyens de paiement |
| `GET/POST /api/admin/contrats`, `GET/PUT/DELETE /api/admin/contrats/{id}` | Contrats : liste (`statut`, `abonne`, `q`), brouillon, modification et suppression du brouillon |
| `GET /api/admin/contrats/modeles`, `GET /api/admin/contrats/{id}/pdf`, `POST /api/admin/contrats/{id}/envoyer` | Modèles, PDF (aperçu « BROUILLON » ou document envoyé), envoi pour signature |

| API de l'espace abonné | Rôle |
|------------------------|------|
| `GET /api/abonne/contrat`, `GET /api/abonne/contrat/pdf` | Mon contrat (204 si aucun) et son PDF (version scellée une fois signé) |
| `POST /api/abonne/contrat/signature` | Envoie le code de signature par email (5 min, renvoi après 60 s) |
| `POST /api/abonne/contrat/signature/{id}/confirmation` | Vérifie le code (3 essais), scelle le PDF (PAdES) ; le contrat est ensuite activé automatiquement |
| `GET /api/abonne/moyens` | Mes moyens de paiement : uniquement ceux ouverts par le contrat en vigueur |
| `POST /api/abonne/paiements` | Paiement (simulé) : droit et plafond revérifiés ; 403 si le moyen n'est pas au contrat, 422 si plafond dépassé |

Côté admin, le cycle de vie d'un contrat signé : `POST /api/admin/contrats/{id}/activer` · `/suspendre` · `/reprendre` · `/resilier`, et `GET /api/admin/contrats/{id}/droits`.

Paiements simulés (démo) : un montant se terminant par ,13 est refusé, ,51 « solde insuffisant », ,99 « en attente », sinon réussi.

Les erreurs suivent le format ProblemDetail (RFC 9457) ; les erreurs de validation listent les champs dans `errors`.

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run          # API sur http://localhost:8080, migrations Flyway au démarrage
./mvnw verify                   # tests (Testcontainers : Docker doit tourner)
```

### 3. Simulateur des fournisseurs de paiement

```bash
cd simulator
./mvnw spring-boot:run          # http://localhost:8090 — webhooks envoyés à http://localhost:8080/api/webhooks/*
./mvnw verify
```

| API simulée | Base | Clé d'API (dev) |
|-------------|------|-----------------|
| Bankily | `/bankily/api/v1/payments` | `bankily-dev-api-key` |
| Masrvi | `/masrvi/v2/transactions` | `masrvi-dev-api-key` |
| Passerelle Visa | `/visa-gateway/api/v1/checkout-sessions`, page `/visa-gateway/pay/{id}` | `visa-dev-api-key` |

Scénarios : montant en ,13 refusé · ,51 solde insuffisant · ,99 confirmé après 60 s · ,98 sans webhook. Cartes de test : `4111 1111 1111 1111` acceptée, `4000 0000 0000 0002` refusée, `4000 0000 0000 9995` fonds insuffisants, `4000 0000 0000 3220` 3-D Secure (différé). Les paiements sont en mémoire : un redémarrage les efface.

Le portail reçoit les webhooks sur `POST /api/webhooks/{bankily|masrvi|visa}` (sans jeton : signature HMAC, horodatage ±5 min, anti-rejeu) et les enregistre dans `webhook_event`.

### 4. Frontend

```bash
cd frontend
npm install
npm start                       # http://localhost:4200 (ou http://localhost via Nginx)
npx ng test --watch=false
```

Les mots de passe ci-dessus sont réservés au développement local ; ils sont surchargeables par variables d'environnement (`DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`…).
