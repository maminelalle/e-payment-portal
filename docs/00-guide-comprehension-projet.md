# 00 — Guide de compréhension du projet

Ce guide explique le Portail E-Payment de bout en bout : à quoi il sert, comment il est construit, ce que voit
chaque utilisateur, comment les données circulent et où trouver chaque chose dans le code. Pour le détail, il
renvoie aux autres documents de `docs/`.

## 1. En une phrase

Une organisation gère ses **abonnés**, leur fait **signer un contrat** électroniquement ; le contrat signé et actif
décide **seul** des moyens de paiement (wallets Bankily, Masrvi ; cartes Visa, Mastercard) que l'abonné peut utiliser,
avec quels plafonds et quelle commission.

```
Admin crée l'abonné ─► prépare le contrat ─► l'abonné signe (code email) ─► droits ouverts ─► paiements ─► suivi
```

## 2. Architecture

```
Navigateur ── HTTPS ──► Nginx ──┬─► /            portail Angular (fichiers statiques)
                                ├─► /api         backend Spring Boot ──► PostgreSQL (données)
                                │                       │             └─► stockage S3 (PDF, reçus, logos)
                                │                       └─► fournisseurs de paiement (simulateur) ◄── webhooks signés
                                ├─► /auth        Keycloak (connexion, mots de passe, MFA admin WhatsApp)
                                └─► /simulateur  pages de paiement carte simulées
```

| Brique | Technologie | Dossier |
|--------|-------------|---------|
| Interface | Angular 21 + PrimeNG, espaces `/admin` et `/abonne` | `frontend/` |
| API et règles métier | Java 21, Spring Boot 4.1, monolithe modulaire (Spring Modulith) | `backend/` |
| Base de données | PostgreSQL 16, migrations Flyway V1 → V17 | `backend/src/main/resources/db/migration` |
| Connexion | Keycloak 26 (OIDC), rôles `ADMIN` / `ABONNE`, extension MFA WhatsApp | `infra/keycloak/` |
| Documents | stockage S3 (SeaweedFS) | — |
| Fournisseurs | application Spring Boot séparée qui imite Bankily, Masrvi et la passerelle carte | `simulator/` |
| Exploitation | Docker Compose, Nginx (TLS, CSP), sauvegardes quotidiennes | `infra/` |

### Modules du backend (`backend/src/main/java/mr/epayment/portal/`)

| Module | Rôle |
|--------|------|
| `auth` | compte local relié à Keycloak, rôles, connexion / déconnexion, sessions |
| `subscriber` | abonnés, coordonnées (NNI/NIF chiffrés), changement de coordonnées par code |
| `catalog` | moyens de paiement, fournisseurs, logos, conditions |
| `contract` | modèles, contrats, PDF, avenants, historique des versions |
| `signature` | code à usage unique, sceau PAdES du PDF, preuves |
| `entitlement` | moteur des droits : ouverture, suspension, révocation, contrôle de chaque paiement |
| `gateway` | adaptateurs fournisseurs (Bankily, Masrvi, carte), webhooks HMAC, état des fournisseurs |
| `transaction` | paiements, statuts, commission, reçus, exports, rapprochement |
| `notification` | emails (Mailpit en développement), préférences |
| `dashboard` | tableaux de bord admin et abonné |
| `audit` | journal des actions sensibles |
| `document` | rendu HTML → PDF, stockage S3 |

Chaque module n'expose que ses services publics ; `ModularityTests` vérifie qu'aucun module n'en contourne un autre.

## 3. L'interface

### Espace administrateur (`/admin`)

| Écran | Ce qu'on y fait |
|-------|-----------------|
| Tableau de bord | indicateurs du mois (transactions, encaissé, commissions), abonnés / contrats / transactions par statut, répartition par moyen, type, fournisseur |
| Abonnés | créer, rechercher, filtrer ; fiche : coordonnées, contrats, moyens autorisés, transactions ; activer, suspendre, désactiver |
| Moyens de paiement | ajouter, modifier, activer / désactiver un moyen, logo, conditions, tester les connexions |
| Contrats | assistant de création, envoi pour signature, cycle de vie, avenants, historique, modèles |
| Transactions | historique de tous les abonnés, filtres, commission, reçus, export Excel/CSV |
| Paramètres et audit | journal d'audit filtrable |
| Recherche (Ctrl K) | abonné, contrat, transaction ou page en une saisie |

### Espace abonné (`/abonne`)

| Écran | Ce qu'on y fait |
|-------|-----------------|
| Accueil | situation du contrat, paiements et plafonds du mois, moyens, dernières transactions |
| Mon contrat | lire, télécharger et signer le contrat ou l'avenant |
| Moyens de paiement | moyens ouverts par le contrat, instructions et conditions, payer |
| Transactions | historique, filtres, reçus PDF (avec QR de vérification), export |
| Profil | coordonnées (téléphone, adresse avec code email), mot de passe, sessions, notifications |

Pages publiques : `/connexion`, `/statut` (disponibilité des moyens), `/verification/recu/<référence>` (ouverte par le QR des reçus).
Arabe (RTL) et mode sombre depuis l'en-tête.

## 4. Les flux métier

### 4.1 Du nouvel abonné au premier paiement

1. **Création** (admin) : l'abonné est enregistré (`PENDING`), un compte Keycloak est créé, une invitation part par
   email (lien 72 h pour choisir le mot de passe).
2. **Contrat** (admin) : modèle, moyens cochés avec plafonds par opération et par mois, dates, commission.
3. **Envoi** : le PDF est généré, **figé** et son empreinte SHA-256 enregistrée ; l'abonné est prévenu par email.
4. **Signature** (abonné) : il lit le contrat, demande un code (email, 5 min, 3 essais), le saisit ; le PDF est
   **scellé** (PAdES) et la preuve conservée (date, IP, navigateur, empreintes avant / après).
5. **Activation** : automatique juste après la signature ; un **droit** est ouvert par moyen inscrit ; l'abonné
   passe `ACTIVE`.
6. **Paiement** : à chaque demande, le serveur vérifie compte, droit, période, moyen actif, plafonds ; le
   fournisseur est appelé ; le résultat arrive par la réponse ou par un **webhook signé** ; le rapprochement
   (toutes les 2 min) rattrape un webhook perdu.

### 4.2 Statuts

| Contrat | Droits | Paiement possible |
|---------|--------|-------------------|
| `DRAFT`, `PENDING_SIGNATURE`, `SIGNED` | aucun | non |
| `ACTIVE` | `ACTIVE` | oui, pendant la période de validité |
| `SUSPENDED` | `SUSPENDED` | non |
| `EXPIRED`, `TERMINATED` | `EXPIRED`, `REVOKED` | non (historique conservé) |
| `REPLACED` (par un avenant) | `REVOKED` | non, l'avenant prend le relais |

Transaction : `CREATED` → `PENDING` → `SUCCEEDED` / `FAILED` / `CANCELLED` (`REFUNDED` prévu). Une transaction
terminée ne change plus ; le statut brut du fournisseur est gardé à côté.

### 4.3 Avenant

Sur un contrat actif, « Créer un avenant » prépare `CTR-…-A1` avec les mêmes moyens et plafonds. L'admin
l'ajuste et l'envoie ; l'original reste en vigueur jusqu'à la signature. À l'activation de l'avenant, dans la même
transaction : l'original passe `REPLACED`, ses droits sont révoqués, ceux de l'avenant ouverts.

### 4.4 Commission

Le taux du contrat est **figé sur chaque transaction** à sa création ; la commission (montant × taux, au centime)
est calculée quand le paiement réussit. Elle apparaît dans les listes, le reçu, l'export et le tableau de bord.

## 5. La base de données

13 entités de la conception, plus quelques tables ajoutées (détail : `02-modele-donnees.md`).

```
subscriber ─┬─< contract ─┬─< contract_payment_method >── payment_method >── payment_provider
            │             ├─< signature
            │             └─< contract (avenant, parent_contract_id)
            ├─< subscriber_entitlement (droit : abonné × contrat × moyen)
            ├─< payment_transaction (contrat qui l'a autorisée, commission)
            └─< notification
app_user ── subscriber ; audit_log (ajout seul)
```

Règles portées par la base : un seul contrat en vigueur par abonné (index unique partiel), un droit par
contrat et moyen (activation rejouable), une transaction par clé d'idempotence, un webhook par identifiant
(anti-rejeu), NNI / NIF chiffrés avec empreinte unique.

## 6. Sécurité en bref

- Mots de passe et MFA dans Keycloak ; MFA admin par **code WhatsApp** (D33).
- Droits vérifiés côté serveur à chaque appel et à chaque paiement.
- Codes à usage unique jamais stockés en clair ; PDF figés et revérifiés ; NNI / NIF chiffrés (AES-256-GCM).
- Aucune donnée de carte : la carte se saisit sur la page de la passerelle.
- Webhooks signés (HMAC) et horodatés ; journal d'audit des actions sensibles.
- Nginx : HTTPS, HSTS, CSP, limitation de débit ; scan OWASP ZAP sans échec.

## 7. Exemples concrets

**Paiement réussi** — Société Atlas paie 12 500 MRU avec Bankily ; contrat à 1,5 % :
`TRX-000017`, `SUCCEEDED`, commission 187,50 MRU, reçu `recus/TRX-000017.pdf` avec QR de vérification.

**Paiement refusé** — 3 750,51 MRU (le simulateur répond « solde insuffisant » aux montants en `,51`) :
`FAILED`, `failure_code = INSUFFICIENT_FUNDS`, pas de commission, pas de reçu.

**Moyen non prévu** — l'abonné tente Visa alors que son contrat ne l'inclut pas : refus `403 NOT_ENTITLED`
avant tout appel au fournisseur.

**Avenant** — retirer Visa du contrat `CTR-2026-0016` : avenant `CTR-2026-0016-A1` signé → l'original
`REPLACED`, Visa disparaît de « Moyens de paiement », les paiements Visa passés restent dans l'historique.

## 8. Lancer et tester

| Besoin | Où |
|--------|-----|
| Démarrer en local | `README.md` |
| Déployer, sauvegarder, configurer la MFA | `07-production.md` |
| Préparer et dérouler la démo | `09-demo.md` |
| Vérifier la conformité à la conception | `08-conformite-conception.md` |
| Comprendre une décision | `00-decisions.md` (D1 → D33) |
| Suivre l'avancement | `05-avancement.md` |

Tests : `backend/mvnw test` (78 tests, PostgreSQL et S3 réels), `frontend/npx ng test` (unitaires),
`frontend/npx playwright test` (22 scénarios de bout en bout sur la recette).
