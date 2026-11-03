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
- [x] Tableau des tâches : la checklist de ce fichier en tient lieu (projet de stage, voir D12)

### Semaine 2 (9–13 nov.) — Socle technique
- [x] Projet Spring Boot 4.1 / Java 21 avec modules Spring Modulith (voir D9)
- [x] Projet Angular 21 + PrimeNG 21, espaces `/admin` et `/abonne` (voir D9)
- [x] Docker Compose : PostgreSQL 16, Keycloak, stockage S3 (SeaweedFS, voir D10), Nginx, Mailpit
- [x] Migration Flyway V1 (app_user, role, subscriber)
- [x] CI GitHub Actions : build et tests backend + frontend
- [x] README : lancer le projet en local

### Semaine 3 (16–20 nov.) — Authentification et rôles
- [x] Realm Keycloak : rôles ADMIN / ABONNE, politique de mot de passe, protection force brute, événements
- [x] MFA pour les administrateurs (flux `browser-mfa-admin` : TOTP exigé pour le rôle ADMIN)
- [x] Backend : resource server JWT, contrôle des rôles par préfixe (`/api/admin/**`, `/api/abonne/**`), refus par défaut
- [x] Frontend : connexion OIDC (code + PKCE), guards par rôle, intercepteurs HTTP (jeton, 401/403)
- [x] Mise en page des 2 espaces (menu, en-tête, déconnexion)
- [x] Module Audit : audit_log + connexions (LOGIN), refus d'accès (ACCESS_DENIED), requêtes admin modifiantes
- [x] Test : un abonné ne peut pas appeler une API admin (`SecurityRulesTests`)
- [ ] Thème Keycloak aux couleurs du portail (page de connexion de la maquette) — reporté, non bloquant

### Semaine 4 (23–27 nov.) — Abonnés et catalogue
- [x] API abonnés : créer, modifier, activer, suspendre, désactiver (+ recherche, compteurs)
- [x] Création automatique du compte Keycloak + email d'invitation (lien 72 h, renvoi possible)
- [x] Écrans admin : liste filtrable et fiche abonné (panneau « Nouvel abonné » de la maquette A2)
- [x] Tables payment_method / payment_provider ; catalogue Bankily, Masrvi, Visa (migration V3)
- [x] Écran admin : catalogue des moyens (activation / désactivation)
- [x] Validation (NNI/NIF, téléphone, email unique)
- [x] **Fin du mois 1 : l'admin crée un abonné qui peut se connecter** — vérifié de bout en bout le 03/11 contre le vrai Keycloak (TOTP admin → création → email Mailpit → mot de passe → connexion abonné)

## Mois 2 — Signature, droits et démo

### Semaine 5 (30 nov.–4 déc.) — Contrats et PDF
- [x] Tables contract_template, contract, contract_payment_method (migration V4)
- [x] Modèle de contrat Thymeleaf « Contrat standard v1 » (7 articles, moyens et plafonds, filigrane BROUILLON)
- [x] Écran admin : créer un contrat (assistant 4 étapes de la maquette A4) + liste et fiche contrat avec aperçu PDF
- [x] Génération PDF (OpenHTMLtoPDF) et stockage S3 (SeaweedFS), empreinte SHA-256 revérifiée à chaque lecture
- [x] Statuts brouillon → en attente de signature ; contrat figé après envoi ; un seul contrat en cours par abonné
- [x] « Envoyer pour signature » + email « contrat disponible » (Mailpit) — recette de bout en bout le 03/11 (CTR-2026-0001)
- [ ] Pied de page du PDF dans la police du document — à faire dans une version 2 du modèle (cosmétique)

### Semaine 6 (7–11 déc.) — Signature électronique
- [x] « Mon contrat » : aperçu et téléchargement (maquette B2)
- [x] OTP par email (Mailpit), 5 min, 3 essais, renvoi après 60 s (5 envois max) ; interface `OtpSender`
- [x] Validation OTP et scellement PAdES BASELINE-B (EU DSS 6.5.1)
- [x] Table signature (V5) : horodatage, IP, navigateur, SHA-256 avant/après, statut
- [x] Archivage de la version signée dans le stockage S3 (l'original est conservé)
- [x] Statut « signé / en attente d'activation » — recette le 03/11 : CTR-2026-0002 signé, sceau vérifié (portée totale, signature cryptographique valide)

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
