# 01 — Inventaire des écrans

Semaine 1 · Cadrage. Les maquettes de référence sont dans [maquettes/](maquettes/).

Une seule application Angular, deux espaces séparés par rôle :

| Espace  | Préfixe de route | Rôle Keycloak | Mise en page                    |
|---------|------------------|---------------|---------------------------------|
| Public  | `/connexion`     | —             | Écran partagé (visuel + formulaire) |
| Admin   | `/admin/**`      | `ADMIN`       | Menu latéral + en-tête          |
| Abonné  | `/abonne/**`     | `ABONNE`      | Barre de navigation horizontale (responsive mobile) |

Après connexion, l'utilisateur est redirigé vers `/admin` ou `/abonne` selon son rôle.

## Écran commun

| # | Écran | Route | Maquette | Semaine |
|---|-------|-------|----------|---------|
| C0 | Connexion (OIDC Keycloak, thème E-Payment) | `/connexion` | `Connexion@1x.png` | S3 |

## Espace Administrateur (6 écrans)

| # | Écran | Route | Contenu principal | Maquette | Semaine |
|---|-------|-------|-------------------|----------|---------|
| A1 | Tableau de bord | `/admin` | 4 indicateurs (abonnés actifs, contrats en attente, transactions du mois, montant encaissé), courbe du volume, répartition par moyen, contrats à traiter, dernières transactions | `A1` | S11 |
| A2 | Abonnés | `/admin/abonnes`, `/admin/abonnes/:id` | Liste filtrable (recherche, statut, type), panneau latéral « Nouvel abonné », fiche abonné (contrats, droits, transactions), actions activer / suspendre / désactiver | `A2` | S4 |
| A3 | Moyens de paiement | `/admin/moyens` | Catalogue Bankily / Masrvi / Visa : nom, logo, catégorie, fournisseur, statut, disponibilité technique | — | S4 |
| A4 | Contrats | `/admin/contrats`, `/admin/contrats/nouveau`, `/admin/contrats/:id` | Liste par statut ; assistant en 4 étapes (abonné et modèle → moyens et plafonds → conditions et dates → vérification) ; aperçu PDF, envoi pour signature, suivi, suspension / résiliation | `A4` | S5–S7 |
| A5 | Transactions | `/admin/transactions` | Historique global filtrable (abonné, moyen, statut, période), détail statut interne / statut fournisseur | — | S10 |
| A6 | Paramètres et audit | `/admin/audit` | Journal d'audit consultable et filtrable, modèles de contrat | — | S3 / S11 |

## Espace Abonné (5 écrans)

| # | Écran | Route | Contenu principal | Maquette | Semaine |
|---|-------|-------|-------------------|----------|---------|
| B1 | Accueil | `/abonne` | Bandeau statut du contrat, moyens autorisés (cartes « Payer avec … »), dernières transactions | `B1` (+ mobile) | S7 / S11 |
| B2 | Mon contrat | `/abonne/contrat` | Aperçu PDF, téléchargement, résumé, signature en 3 étapes (envoi OTP → saisie du code → confirmation) | `B2` | S6 |
| B3 | Moyens de paiement & paiement | `/abonne/moyens`, `/abonne/payer/:moyen`, `/abonne/paiement/:ref` | Uniquement les moyens du contrat actif ; saisie du montant ; résultat (réussi / échoué / en attente) avec reçu | `B3` | S7 / S10 |
| B4 | Transactions | `/abonne/transactions` | Historique filtrable, téléchargement des reçus PDF | — | S10 |
| B5 | Profil | `/abonne/profil` | Coordonnées, mot de passe, sessions actives (via Keycloak), préférences de notification | — | S11 |

## Charte graphique (relevée sur les maquettes)

| Élément | Valeur |
|---------|--------|
| Primaire | Bleu `#2563EB` (boutons, liens, état actif) ; bleu foncé `#1E3A8A` (panneau de connexion) |
| Fond | `#F8FAFC` ; cartes blanches, bordure `#E2E8F0`, rayon 12 px |
| Bankily | Pastille « BK » bleue `#2563EB` |
| Masrvi | Pastille « MS » verte `#0D9488` (V11 ; `#0F766E` paraissait gris dans les graphiques) |
| Visa | Pastille « VI » violette `#7C3AED` |
| Statuts | Réussie (vert), En attente (ambre), Échouée (rouge), Brouillon (gris), À activer (bleu) |
| Montants | Format `12 500,00 MRU`, dates `02/11/2026 à 11:42` (locale `fr`, fuseau `Africa/Nouakchott`) |
| Références | Abonné `ABN-0001`, contrat `CTR-2026-0001`, transaction `TRX-000148` |
