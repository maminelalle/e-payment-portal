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
- [x] subscriber_entitlement (V6) ; activation automatique après signature (PDF scellé revérifié) ; abonné « en attente » activé
- [x] Idempotence (contrainte unique + `ON CONFLICT DO NOTHING`)
- [x] « Mes moyens de paiement » filtré par les droits (accueil B1 + page moyens + tiroir de paiement B3)
- [x] Contrôle serveur du droit sur chaque paiement (abonné actif, droit actif, période, catalogue, plafond)
- [x] Suspension / reprise / résiliation → droits suspendus, rouverts, révoqués (fiche contrat admin)
- [x] Job quotidien d'expiration + rattrapage des contrats signés (Scheduler + ShedLock, 00 h 05 Nouakchott)
- [x] Adaptateur de paiement simulé (mock) pour les 3 moyens, interface `PaymentProvider`
- [x] Tests d'intégration du workflow (`WorkflowTests`, Testcontainers) — 35 tests backend au vert
- [x] **Jalon : démo du workflow complet** — vérifié le 03/11 sur la vraie stack et à l'écran (Société Delta : création → contrat → signature → activation → paiement Bankily réussi, Masrvi refusé, plafond refusé)
- [ ] Plafond mensuel cumulé : le contrôle existe, le cumul réel arrive avec la table des transactions (semaine 10)

### Semaine 8 (21–25 déc.) — Simulateur des fournisseurs
- [x] Interface `PaymentProvider` (initiation) + modèle commun `ProviderWebhook` ; un traducteur par fournisseur
- [x] Application `simulator/` (port 8090) imitant Bankily, Masrvi (format français) et la passerelle Visa (page hébergée)
- [x] Scénarios : succès, échec (,13), solde insuffisant (,51), délai (,99), sans webhook (,98) ; cartes de test Visa
- [x] Webhooks signés HMAC-SHA256, réessais 10 s / 30 s / 60 s ; clé d'API et `Idempotency-Key` côté simulateur
- [x] Endpoint webhook du portail (`POST /api/webhooks/{bankily|masrvi|visa}`) : signature, fenêtre de 5 min, anti-rejeu (V7 `webhook_event`) — recette du 03/11 : 3 paiements simulés, 3 webhooks vérifiés et enregistrés

## Mois 3 — Paiements et mise en ligne

### Semaine 9 (28 déc.–1er janv.) — Adaptateurs
- [x] Adaptateurs Bankily et Masrvi (RestClient, délais 5 s / 15 s, Idempotency-Key = TRX)
- [x] Adaptateur Visa : session, redirection vers la page hébergée, page de retour, webhook
- [x] Aucun PAN / CVV ne transite par le portail — vérifié le 03/11 (base, webhooks, audit, journaux : 0 occurrence)
- [x] URL et clés en configuration (`epayment.providers.*`, mode `simulator` / `mock`)
- [x] Tests WireMock des 3 adaptateurs (`HttpPaymentProvidersTests`)
- [x] Logos des moyens dans le catalogue (envoi admin) ; Sedad et Mastercard au catalogue, désactivés

### Semaine 10 (4–8 janv.) — Paiements et transactions
- [x] payment_transaction (V9) : statut interne / statut fournisseur — avancé en semaine 9
- [x] Clé d'idempotence (en-tête `Idempotency-Key`, unique par abonné) — avancé en semaine 9
- [x] Écran abonné : payer et voir le résultat (suivi wallet, retour carte) — recette du 03/11 : Bankily et Visa réussis via le simulateur
- [x] Plafond mensuel cumulé (paiements réussis + en cours du mois)
- [x] Reçu PDF (paiement réussi uniquement, stocké `recus/TRX-….pdf`, téléchargeable par l'abonné et l'admin)
- [x] Historique abonné avec filtres (statut, moyen, période, référence) ; historique admin (toutes transactions, abonné affiché) et 5 dernières sur la fiche abonné
- [x] Job de rapprochement (toutes les 2 min, ShedLock) : redemande le statut des paiements restés en cours
- [x] **Jalon : un paiement réussi et un échoué sur chacun des 3 moyens** — recette du 03/11 : TRX-000003 Bankily réussi, TRX-000004 Bankily « solde insuffisant », TRX-000005 Masrvi réussi (rattrapé par le rapprochement, voir D24), TRX-000006 Masrvi refusé, TRX-000007 Visa réussi (411111******1111, reçu PDF), TRX-000008 Visa refusé

### Semaine 11 (11–15 janv.) — Notifications et tableaux de bord
- [x] Emails : contrat disponible, activé, paiement réussi / échoué — journalisés dans la table `notification` (V10) ; le corps des emails de code de signature n'est pas conservé
- [x] Tableau de bord admin (graphiques PrimeNG / Chart.js, maquette A1) : 4 indicateurs du mois comparés à la même durée du mois précédent, volume par jour (7/30/90 jours), répartition par moyen, contrats à traiter, dernières transactions
- [x] Tableau de bord abonné (accueil B1) : paiements du mois (montant, réussis, échoués) et jauge de chaque plafond mensuel
- [ ] Profil abonné (Keycloak)
- [x] Journal d'audit consultable (écran A6) : filtres acteur, ressource, action, résultat, période ; détail de chaque événement

### Semaine 12 (18–22 janv.) — Recette et mise en ligne
- [ ] Tests E2E Playwright
- [ ] Scan OWASP ZAP et corrections
- [ ] Serveur de production : Docker Compose, Nginx, HTTPS
- [ ] Sauvegardes quotidiennes PostgreSQL + MinIO
- [ ] Guides administrateur et abonné (1 page chacun)
- [ ] Démo finale
- [ ] **Jalon : version pilote en ligne**
