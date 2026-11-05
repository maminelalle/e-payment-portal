# Guide administrateur

## Connexion

1. Ouvrir le portail et cliquer sur **Se connecter**.
2. Saisir son email et son mot de passe (12 caractères minimum, avec majuscule, minuscule, chiffre et caractère spécial).
3. Saisir le code à 6 chiffres de l'application d'authentification (Google Authenticator, Microsoft Authenticator…).
   À la toute première connexion, scanner le QR code affiché par Keycloak pour l'enregistrer.

La console d'administration Keycloak n'est pas accessible depuis Internet : tout se fait depuis le portail.

## Parcours courant

1. **Abonnés** : créer l'abonné (personne physique ou morale). Il reçoit un email d'invitation pour choisir
   son mot de passe (lien valable 72 h, à renvoyer depuis sa fiche si besoin). Sa fiche montre ses contrats,
   ses **moyens de paiement autorisés** (plafonds, validité) et ses dernières transactions.
2. **Contrats** : créer le contrat, choisir les dates, la commission, les moyens ouverts et leurs plafonds
   (par opération et par mois), puis l'**envoyer pour signature**. Le contrat devient actif dès que l'abonné
   l'a signé avec le code reçu par email.
   Pour changer les moyens, plafonds ou dates d'un contrat actif : **Créer un avenant** sur sa fiche. L'avenant
   (CTR-…-A1) est prérempli ; ajustez-le, envoyez-le. Le contrat actuel reste en vigueur jusqu'à la signature ;
   à l'activation, il passe « Remplacé par avenant ». La fiche montre l'**historique des versions**.
   **Contrats → Modèles** : versions du modèle de contrat, aperçu PDF, choix des versions proposées dans
   l'assistant (une nouvelle version arrive avec une mise à jour du portail).
3. **Transactions** : suivre les paiements et leur statut fournisseur, filtrer par abonné, statut, moyen ou
   période, télécharger les reçus, et **Exporter (Excel, CSV)** la liste filtrée.
4. **Paramètres et audit** : rechercher les connexions, signatures et actions administratives.

Un abonné ne peut payer qu'avec un moyen ouvert par son contrat actif. La suspension de l'abonné bloque
ses paiements ; la désactivation ferme son accès.

## Outils du quotidien

- **Tableau de bord** : indicateurs du mois, abonnés et contrats par statut, transactions de la période par
  statut, répartition par moyen, par type (wallet ou carte) et par fournisseur. Cliquer sur un statut ouvre
  la liste correspondante déjà filtrée (par exemple les abonnés suspendus).
- **Recherche globale** : `Ctrl K` (ou le champ en haut de page) trouve un abonné, un contrat (`CTR-`),
  une transaction (`TRX-`) ou une page. Flèches pour choisir, Entrée pour ouvrir.
- **État des fournisseurs** : visible en bas du menu. **Moyens de paiement → Tester les connexions** relance
  la vérification de Bankily, Masrvi et de la passerelle Visa / Mastercard.
- **Moyens de paiement** : **Ajouter un moyen** (sur un fournisseur raccordé ; il est créé désactivé),
  **Modifier** son nom, sa pastille, sa couleur, ses instructions et ses conditions (visibles par l'abonné),
  l'activer ou le désactiver, changer son logo (PNG, JPEG ou WebP, 256 Ko, format carré conseillé : un logo
  très large devient petit dans la vignette). Un moyen dont le fournisseur n'est pas raccordé (Sedad) ne peut
  pas être activé.
- **Notifications** (cloche) : contrats à traiter, fournisseur indisponible, paiements échoués à répétition
  et montants inhabituels pour un abonné.
- **Mon profil** : modifier son nom et son email, changer son mot de passe.
- Page publique **/statut** : disponibilité des moyens de paiement, à communiquer aux abonnés.

## Vérifications de recette

- Les emails (invitation, code de signature, résultat de paiement) arrivent dans Mailpit en recette.
- Après une signature, le contrat affiche le PDF scellé avec le cachet visible.
- Un reçu PDF porte un QR code : il ouvre la page de vérification publique du reçu.
- Une déconnexion apparaît dans l'audit avec l'action `LOGOUT`.
