# 00 — Décisions et hypothèses

Registre des décisions de cadrage. Une ligne par décision ; on ajoute, on ne réécrit pas.

## Hypothèses métier

| # | Sujet | Hypothèse retenue | Statut |
|---|-------|-------------------|--------|
| H1 | Rôle du portail | L'abonné **paie** (pas d'encaissement pour ses clients) | Retenu (D12) |
| H2 | Modèle des moyens | Compte propre à chaque abonné (son wallet, sa carte) | Retenu (D12) |
| H3 | Contrats multiples | Un seul contrat actif par abonné ; un avenant remplace le contrat | Retenu (D12) |
| H4 | Fuseau horaire | `Africa/Nouakchott` (UTC+0) | Retenu |
| H5 | Devise | MRU ; devises carte selon la passerelle | Retenu |
| H6 | Signature | Intégrée : OTP (email via Mailpit pour la démo, SMS ensuite) + scellement PAdES (EU DSS) | Retenu |
| H7 | Moyens de la démo | Bankily, Masrvi, Visa ; Sedad et Mastercard hors démo | Retenu |
| H8 | Identifiants | NNI : 10 chiffres ; NIF : 8 chiffres (format de la maquette) ; téléphone : +222 suivi de 8 chiffres commençant par 2, 3 ou 4 | Retenu (à ajuster si besoin) |
| H9 | Cycle de vie abonné | Créé « en attente » (peut se connecter pour définir son mot de passe) ; l'admin l'active ; suspension réversible ; désactivation définitive. L'email (identifiant de connexion) n'est pas modifiable | Retenu |

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
| D9 | 2026-11-02 | **Spring Boot 4.1** (au lieu de 3) et Spring Modulith 2.1 ; **Angular 21 + PrimeNG 21** (au lieu de 20) | Spring Boot 3 n'a plus de support open source depuis mi-2026 et Angular 20 sort de LTS en novembre 2026. Angular 22 exige Node ≥ 24.15 (poste : 24.11) : passage à 22 par `ng update` après mise à jour de Node ; Angular 21 est en LTS jusqu'en mai 2027 |
| D10 | 2026-11-02 | Stockage objet local : **SeaweedFS** (API S3) au lieu de MinIO ; le backend utilise un client S3 standard | MinIO ne publie plus d'images Docker communautaires ; l'API S3 garde le choix interchangeable (MinIO, Garage, S3 cloud) |
| D11 | 2026-11-02 | Une seule instance PostgreSQL en dev, avec deux bases : `epayment` et `keycloak` | Moins de conteneurs sur le poste |

| D12 | 2026-11-03 | **Projet de stage, non officiel** : pas de demande d'accès aux vraies APIs Bankily / Masrvi / Visa ni d'opérateur SMS. Le simulateur (`simulator/`) est la cible finale ; les hypothèses H1–H3 sont retenues telles quelles | Aucune organisation réelle derrière le projet ; l'architecture par adaptateurs reste prête pour de vraies APIs |
| D16 | 2026-11-03 | MFA admin désactivable **en local uniquement** (`infra/keycloak/mfa-admin.sh off|on`) ; le realm livré la garde active | Tests manuels plus rapides sans affaiblir la configuration de démo et de production |
| D14 | 2026-11-03 | Module technique `document` (stockage S3 + rendu Thymeleaf → PDF) partagé par contrats, signature et reçus ; les modèles de contrat viennent des migrations, jamais d'une saisie libre (Thymeleaf exécute du code) | Une seule implémentation PDF ; pas d'injection de modèle |
| D15 | 2026-11-03 | Un contrat envoyé est figé : PDF stocké + empreinte SHA-256, revérifiée avant chaque lecture. Les emails partent après validation de la transaction, en arrière-plan ; une panne mail n'annule rien | Preuve d'intégrité ; robustesse |
| D13 | 2026-11-03 | Comptes abonnés créés par le backend via l'API d'administration Keycloak (client confidentiel `epayment-backend`, compte de service limité à la gestion des utilisateurs) ; l'invitation est l'email Keycloak « définir votre mot de passe » | Aucun mot de passe ne transite par le portail ; Keycloak gère le lien et son expiration |
