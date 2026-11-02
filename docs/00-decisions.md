# 00 — Décisions et hypothèses

Registre des décisions de cadrage. Une ligne par décision ; on ajoute, on ne réécrit pas.

## Hypothèses métier (à faire confirmer par l'organisation)

| # | Sujet | Hypothèse retenue | Statut |
|---|-------|-------------------|--------|
| H1 | Rôle du portail | L'abonné **paie** (pas d'encaissement pour ses clients) | À confirmer |
| H2 | Modèle des moyens | Compte propre à chaque abonné (son wallet, sa carte) | À confirmer |
| H3 | Contrats multiples | Un seul contrat actif par abonné ; un avenant remplace le contrat | À confirmer |
| H4 | Fuseau horaire | `Africa/Nouakchott` (UTC+0) | Retenu |
| H5 | Devise | MRU ; devises carte selon la passerelle | Retenu |
| H6 | Signature | Intégrée : OTP (email via Mailpit pour la démo, SMS ensuite) + scellement PAdES (EU DSS) | Retenu |
| H7 | Moyens de la démo | Bankily, Masrvi, Visa ; Sedad et Mastercard hors démo | Retenu |

## Décisions techniques

| # | Date | Décision | Raison |
|---|------|----------|--------|
| D1 | 2026-11-02 | Monolithe modulaire Spring Boot 3 + Spring Modulith, Java 21 | Un seul déploiement, modules isolés |
| D2 | 2026-11-02 | Angular 20 + PrimeNG, une seule application, espaces `/admin` et `/abonne` | Back-office riche en formulaires et tableaux |
| D3 | 2026-11-02 | Keycloak (OIDC) pour l'authentification, MFA admin | Sessions, mots de passe, MFA sans développement |
| D4 | 2026-11-02 | PostgreSQL 16 + Flyway ; idempotence par contraintes d'unicité | Fiabilité transactionnelle |
| D5 | 2026-11-02 | **GitHub** (et non GitLab) : dépôt `maminelalle/e-payment-portal`, CI **GitHub Actions**, tableau GitHub Projects | Le dépôt du projet est hébergé sur GitHub |
| D6 | 2026-11-02 | Monorepo : `backend/`, `frontend/`, `simulator/`, `infra/`, `docs/` | Un seul dépôt pour un développeur seul |
| D7 | 2026-11-02 | Fournisseurs simulés par une application Spring Boot séparée (`simulator/`) qui envoie des webhooks HMAC | Avancer sans dépendre des accès sandbox |
| D8 | 2026-11-02 | Le JDK local est le 24 ; le code cible Java 21 (`release 21`) et la CI utilise Java 21 | Conforme au choix Java 21 LTS |

## Actions externes à lancer dès la semaine 1

- [ ] Demander les accès sandbox Bankily, Masrvi et passerelle Visa (délai hors de notre contrôle).
- [ ] Identifier un opérateur SMS local pour l'OTP (après les 3 mois).
- [ ] Faire relire les hypothèses H1–H3 par l'organisation.
