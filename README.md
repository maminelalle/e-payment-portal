# Portail E-Payment

Portail web où **le contrat signé est la seule source des droits** : chaque abonné ne voit et n'utilise que les moyens de paiement (Bankily, Masrvi, Visa) inscrits dans son contrat actif.

- **Espace Administrateur** : abonnés, catalogue des moyens, contrats, transactions, audit.
- **Espace Abonné** : consulter et signer son contrat (OTP), payer avec les moyens autorisés, historique et reçus.

## Stack

| Couche | Choix |
|--------|-------|
| Frontend | Angular 20 + PrimeNG |
| Backend | Java 21, Spring Boot 3, Spring Modulith (monolithe modulaire) |
| Données | PostgreSQL 16 + Flyway |
| Authentification | Keycloak (OIDC) + Spring Security |
| Documents | MinIO (S3), Thymeleaf + OpenHTMLtoPDF, EU DSS (PAdES) |
| Emails (dev) | Mailpit |
| Paiements | Simulateur Bankily / Masrvi / Visa (webhooks HMAC) |
| Déploiement | Docker Compose + Nginx ; CI GitHub Actions |

## Structure du dépôt

```
backend/     API Spring Boot (à venir — semaine 2)
frontend/    Application Angular (à venir — semaine 2)
simulator/   Simulateur des fournisseurs de paiement (semaine 8)
infra/       Docker Compose, Keycloak, Nginx (semaine 2)
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

_Section complétée en semaine 2, avec le socle technique._
