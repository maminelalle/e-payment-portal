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
simulator/   Simulateur des fournisseurs de paiement (semaine 8)
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

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run          # API sur http://localhost:8080, migrations Flyway au démarrage
./mvnw verify                   # tests (Testcontainers : Docker doit tourner)
```

### 3. Frontend

```bash
cd frontend
npm install
npm start                       # http://localhost:4200 (ou http://localhost via Nginx)
npx ng test --watch=false
```

Les mots de passe ci-dessus sont réservés au développement local ; ils sont surchargeables par variables d'environnement (`DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`…).
