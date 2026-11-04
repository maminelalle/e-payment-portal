# Guide administrateur

## Connexion

1. Ouvrir le portail et cliquer sur **Se connecter**.
2. Saisir `admin@epayment.mr` et le mot de passe configuré pour l'environnement.
3. En production, saisir le code TOTP demandé par Keycloak.

Ne pas utiliser la console d'administration Keycloak (`/admin`) pour se connecter au portail.

## Parcours courant

1. **Abonnés** : créer un abonné, vérifier ses coordonnées et renvoyer l'invitation si nécessaire.
2. **Contrats** : créer un contrat, sélectionner les moyens et les plafonds, puis l'envoyer pour signature.
3. **Transactions** : suivre les paiements, leur statut fournisseur et télécharger les reçus réussis.
4. **Paramètres et audit** : rechercher les connexions, déconnexions, signatures et actions administratives.

Un abonné ne peut payer qu'avec un moyen ouvert par son contrat actif. La suspension de l'abonné bloque
ses paiements et la désactivation ferme son accès.

## Vérifications de recette

- Un email d'invitation ou de signature doit apparaître dans Mailpit en développement.
- Après une signature, le contrat affiche le PDF scellé et le cachet visible.
- Une déconnexion doit apparaître dans l'audit avec l'action `LOGOUT`.
- Le bouton retour du navigateur ne doit pas restaurer une page protégée utilisable sans authentification.
