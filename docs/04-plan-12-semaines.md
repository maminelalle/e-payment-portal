# 04 — Plan sur 12 semaines (2 nov. 2026 → 22 janv. 2027)

Version suivie de la [checklist d'origine](references/Checklist%2012%20semaines.docx), adaptée à GitHub. Cocher au fil de l'eau.

Jalons : **S7** workflow complet démontrable · **S10** paiements simulés réalistes · **S12** version pilote en ligne.

## Mois 1 — Socle, abonnés et contrats

### Semaine 1 (2–6 nov.) — Cadrage
- [x] Lister les écrans : 6 admin, 5 abonné → [01-ecrans.md](01-ecrans.md)
- [x] Maquettes des écrans clés → [maquettes/](maquettes/)
- [x] Schéma de la base (13 entités) → [02-modele-donnees.md](02-modele-donnees.md)
- [x] Contrat d'API simulé de chaque fournisseur → [03-api-fournisseurs-simules.md](03-api-fournisseurs-simules.md)
- [x] Dépôt GitHub initialisé
- [ ] Tableau des tâches (GitHub Projects / issues)

### Semaine 2 (9–13 nov.) — Socle technique
- [x] Projet Spring Boot 4.1 / Java 21 avec modules Spring Modulith (voir D9)
- [x] Projet Angular 21 + PrimeNG 21, espaces `/admin` et `/abonne` (voir D9)
- [x] Docker Compose : PostgreSQL 16, Keycloak, stockage S3 (SeaweedFS, voir D10), Nginx, Mailpit
- [x] Migration Flyway V1 (app_user, role, subscriber)
- [x] CI GitHub Actions : build et tests backend + frontend
- [x] README : lancer le projet en local

### Semaine 3 (16–20 nov.) — Authentification et rôles
- [ ] Realm Keycloak : rôles ADMIN / ABONNE, politique de mot de passe
- [ ] MFA pour les administrateurs
- [ ] Backend : resource server JWT, contrôle des rôles par endpoint
- [ ] Frontend : connexion OIDC, guards, intercepteur HTTP
- [ ] Mise en page des 2 espaces (menu, en-tête, déconnexion)
- [ ] Module Audit : audit_log + connexions et actions admin
- [ ] Test : un abonné ne peut pas appeler une API admin

### Semaine 4 (23–27 nov.) — Abonnés et catalogue
- [ ] API abonnés : créer, modifier, activer, suspendre, désactiver
- [ ] Création automatique du compte Keycloak + email d'invitation
- [ ] Écrans admin : liste filtrable et fiche abonné
- [ ] Tables payment_method / payment_provider ; catalogue Bankily, Masrvi, Visa
- [ ] Écran admin : catalogue des moyens
- [ ] Validation (NNI/NIF, téléphone, email unique)
- [ ] **Fin du mois 1 : l'admin crée un abonné qui peut se connecter**

## Mois 2 — Signature, droits et démo

### Semaine 5 (30 nov.–4 déc.) — Contrats et PDF
- [ ] Tables contract_template, contract, contract_payment_method
- [ ] Modèle de contrat Thymeleaf
- [ ] Écran admin : créer un contrat (assistant 4 étapes)
- [ ] Génération PDF (OpenHTMLtoPDF) et stockage MinIO
- [ ] Statuts brouillon → en attente de signature
- [ ] « Envoyer pour signature » + email

### Semaine 6 (7–11 déc.) — Signature électronique
- [ ] « Mon contrat » : aperçu et téléchargement
- [ ] OTP par email (Mailpit), 5 min, 3 essais ; interface `OtpSender`
- [ ] Validation OTP et scellement PAdES (EU DSS)
- [ ] Table signature : horodatage, IP, SHA-256, statut
- [ ] Archivage de la version signée dans MinIO
- [ ] Statut « signé / en attente d'activation »

### Semaine 7 (14–18 déc.) — Moteur des droits et Espace Abonné
- [ ] subscriber_entitlement ; activation automatique après signature
- [ ] Idempotence (contrainte unique)
- [ ] « Mes moyens de paiement » filtré par les droits
- [ ] Contrôle serveur du droit sur chaque paiement
- [ ] Suspension / résiliation → révocation
- [ ] Job quotidien d'expiration (Scheduler + ShedLock)
- [ ] Adaptateur de paiement simulé (mock) pour les 3 moyens
- [ ] Tests d'intégration du workflow (Testcontainers)
- [ ] **Jalon : démo du workflow complet**

### Semaine 8 (21–25 déc.) — Simulateur des fournisseurs
- [ ] Interface `PaymentProvider`
- [ ] Application `simulator/` imitant Bankily, Masrvi, Visa
- [ ] Scénarios : succès, échec, solde insuffisant, délai
- [ ] Webhooks signés HMAC
- [ ] Endpoint webhook du portail : signature + anti-rejeu

## Mois 3 — Paiements et mise en ligne

### Semaine 9 (28 déc.–1er janv.) — Adaptateurs
- [ ] Adaptateurs Bankily et Masrvi (WebClient)
- [ ] Adaptateur Visa : page hébergée, retour, webhook
- [ ] Aucun PAN / CVV ne transite par le portail
- [ ] URL et clés en configuration
- [ ] Tests WireMock des 3 adaptateurs

### Semaine 10 (4–8 janv.) — Paiements et transactions
- [ ] payment_transaction : statut interne / statut fournisseur
- [ ] Clé d'idempotence
- [ ] Écran abonné : payer et voir le résultat
- [ ] Reçu PDF
- [ ] Historique (abonné et admin) avec filtres
- [ ] Job de rapprochement
- [ ] **Jalon : un paiement réussi et un échoué sur chacun des 3 moyens**

### Semaine 11 (11–15 janv.) — Notifications et tableaux de bord
- [ ] Emails : contrat disponible, activé, paiement réussi / échoué
- [ ] Tableau de bord admin (graphiques PrimeNG)
- [ ] Tableau de bord abonné
- [ ] Profil abonné (Keycloak)
- [ ] Journal d'audit consultable

### Semaine 12 (18–22 janv.) — Recette et mise en ligne
- [ ] Tests E2E Playwright
- [ ] Scan OWASP ZAP et corrections
- [ ] Serveur de production : Docker Compose, Nginx, HTTPS
- [ ] Sauvegardes quotidiennes PostgreSQL + MinIO
- [ ] Guides administrateur et abonné (1 page chacun)
- [ ] Démo finale
- [ ] **Jalon : version pilote en ligne**
