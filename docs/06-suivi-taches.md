# Suivi des tâches et prochaines étapes

Dernière mise à jour : 04/11/2026.

## Réalisé

- Profil abonné B5 : coordonnées, changement de mot de passe via Keycloak, sessions et notifications.
- Déconnexion : événement `LOGOUT` enregistré dans l'audit avant la fermeture de session Keycloak.
- Retour navigateur après déconnexion : session locale supprimée, URL remplacée par `/connexion` et routes protégées
  refusées sans authentification.
- Signature : scellement PAdES avec cachet visible dans le PDF signé ; l'interface recharge maintenant le PDF signé
  immédiatement après confirmation.

## En cours

- Tests E2E navigateur et scan OWASP ZAP sur un environnement de recette.
- Déploiement HTTPS et restauration périodique des sauvegardes sur l'environnement de production.

## À faire

- Exécuter les tests E2E Playwright sur l'environnement de recette.
- Réaliser le scan OWASP ZAP et corriger les résultats pertinents.
- Déployer la configuration de production : Docker Compose, Nginx et HTTPS.
- Planifier le script de sauvegarde et tester une restauration PostgreSQL/S3.
- Effectuer la démo finale et publier la version pilote.

## Validation locale

- Build frontend Angular : réussi.
- Tests frontend : 12 réussis.
- Tests backend d’intégration : 64 réussis avec Testcontainers.
- Tests du simulateur : 8 réussis.
- Package backend Maven et build frontend : réussis.
- Recette navigateur réalisée : connexion admin via le realm `epayment`, tableau de bord affiché, déconnexion,
  retour navigateur maintenu sur `/connexion` et événement `LOGOUT` vérifié dans `audit_log`.
- Lot UX : thème sombre, français/arabe avec RTL, centre de notifications, progression du contrat, alerte
  d'expiration et états de chargement/erreur ajoutés à l'espace abonné.
- Traduction arabe réactive : le bouton `عربي` traduit maintenant immédiatement les libellés, menus,
  boutons, champs et attributs d'accessibilité de tous les écrans rendus, sans rechargement. Le retour à `FR`
  restaure les textes français et les nouvelles pages sont également traduites après navigation.
- Design UX : la progression du contrat est une carte autonome avec étapes reliées, cercles d'état et
  adaptation RTL. Les préférences langue/thème sont aussi disponibles depuis la page de connexion.
- Profil administrateur : modification du nom et de l'adresse email depuis `/admin/profil`, changement de
  mot de passe via Keycloak et synchronisation avec le compte local.
- Étape 1 du guide d'audit UI : pagination normalisée via `toPage`, stepper accessible masqué à l'état actif,
  logos de moyens avec repli initiales, menu abonné responsive et corrections RTL ciblées.
