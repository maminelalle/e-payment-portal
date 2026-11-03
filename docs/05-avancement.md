# 05 — Avancement, mises à jour et reste à faire

Journal de suivi du projet, à mettre à jour à chaque étape. Le détail case par case est dans le
[plan sur 12 semaines](04-plan-12-semaines.md) ; les choix et leurs raisons dans les [décisions](00-decisions.md).

**Dernière mise à jour : 03/11/2026** — semaines 1 à 10 terminées, semaine 11 à commencer.

## Vue d'ensemble

| Semaine | Thème | État |
|---|---|---|
| 1 | Cadrage (écrans, maquettes, données, API simulées) | Terminée |
| 2 | Socle technique (Spring Boot, Angular, Docker, CI) | Terminée |
| 3 | Authentification, rôles, MFA, audit | Terminée (thème Keycloak reporté) |
| 4 | Abonnés et catalogue | Terminée — jalon du mois 1 atteint |
| 5 | Contrats et PDF | Terminée (pied de page du PDF à revoir, cosmétique) |
| 6 | Signature électronique (code + sceau PAdES) | Terminée |
| 7 | Moteur des droits, espace abonné | Terminée — jalon S7 atteint |
| 8 | Simulateur Bankily / Masrvi / Visa, webhooks | Terminée |
| 9 | Adaptateurs HTTP, logos des moyens | Terminée |
| 10 | Paiements, reçus, historiques, rapprochement | Terminée — jalon S10 atteint |
| 11 | Notifications et tableaux de bord | **À faire** |
| 12 | Recette et mise en ligne | **À faire** |

Tests automatisés : 55 backend, 8 simulateur, 11 frontend, tous au vert.

## Mises à jour (de la plus récente à la plus ancienne)

### 03/11/2026 — Signature visible
- Le contrat signé affiche un cachet dans le cadre de signature (dernière page) : signataire, email, date et heure,
  code vérifié, référence du contrat (décision D25). S'applique aux nouvelles signatures.

### 03/11/2026 — Semaine 10
- Reçu PDF des paiements réussis, téléchargeable par l'abonné et l'admin.
- Historique filtrable (statut, moyen, période, référence) côté abonné ; écran admin « Transactions » ;
  5 dernières transactions sur la fiche abonné.
- Rapprochement toutes les 2 minutes des paiements restés en cours.
- Jalon : un paiement réussi et un échoué sur chacun des 3 moyens (TRX-000003 à TRX-000008).
- Correction : le simulateur Masrvi réutilisait ses identifiants de notification après un redémarrage ;
  le portail les ignorait. Identifiants désormais aléatoires ; un identifiant réutilisé avec un autre
  contenu est audité `WEBHOOK_EVENT_ID_REUSED` (décision D24).

### 03/11/2026 — Semaines 8 et 9
- Simulateur des 3 fournisseurs (port 8090), webhooks signés HMAC, anti-rejeu.
- Adaptateurs HTTP réels vers le simulateur ; aucun numéro de carte ne passe par le portail.
- Logos des moyens envoyés par l'admin ; Sedad et Mastercard ajoutés au catalogue (désactivés).

### 03/11/2026 — Semaines 5 à 7
- Contrats en PDF, envoi pour signature, signature par code email + sceau PAdES.
- Activation automatique des droits après signature ; espace abonné ; démo du workflow complet.
- Correction de la boucle de retour sur la page de connexion.
- MFA admin désactivable en local (`infra/keycloak/mfa-admin.sh off|on`).

### 02–03/11/2026 — Semaines 1 à 4
- Cadrage, socle technique, Keycloak (rôles, MFA admin), audit, gestion des abonnés et du catalogue.

## Reste à faire

### Semaine 11 — Notifications et tableaux de bord
- [ ] Emails : contrat activé, paiement réussi, paiement échoué (« contrat disponible » existe déjà)
- [ ] Tableau de bord admin avec graphiques (maquette A1)
- [ ] Tableau de bord abonné
- [ ] Profil abonné (données Keycloak)
- [ ] Écran du journal d'audit (l'API `GET /api/admin/audit` existe déjà)

### Semaine 12 — Recette et mise en ligne
- [ ] Tests de bout en bout Playwright
- [ ] Scan de sécurité OWASP ZAP et corrections
- [ ] Configuration de production : Docker Compose, Nginx, HTTPS
- [ ] Sauvegardes quotidiennes PostgreSQL + stockage S3
- [ ] Guides administrateur et abonné (1 page chacun)
- [ ] Démo finale — **jalon : version pilote en ligne**

### Points secondaires
- [ ] Thème Keycloak aux couleurs du portail (page de connexion)
- [ ] Pied de page du PDF de contrat dans la police du document
- [ ] Certificat de scellement reconnu (voir « Limites connues »)
- [ ] CI GitHub Actions bloquée par la facturation du compte GitHub (action du propriétaire du compte, pas du code)

## Limites connues

### Signature « Inconnu » dans le lecteur PDF
Le cachet visible (D25) montre qui a signé, mais le panneau « Signature numérique » d'Edge ou Acrobat affiche « Identité du signataire : Inconnu » mais
« Document modifié : Non ». C'est le comportement attendu en développement :
- **le sceau est valide** : il garantit que le PDF n'a pas été modifié depuis la signature ;
- **l'identité est inconnue** parce que le certificat du portail est auto-signé et généré au démarrage
  (aucune clé privée dans le dépôt, décision D17). Il n'est dans aucun magasin de confiance ; le lecteur
  ne peut donc pas le rattacher à une autorité reconnue.

Pour obtenir une signature « valide » : fournir un certificat de cachet délivré par une autorité reconnue
(keystore PKCS#12 via `SIGNATURE_SEAL_KEYSTORE` et `SIGNATURE_SEAL_KEYSTORE_PASSWORD`). Pour une démo,
un certificat de test conservé d'un démarrage à l'autre et ajouté manuellement au magasin de confiance
du poste suffit.

L'identité de l'abonné qui a signé (email, code vérifié, date, IP) est enregistrée dans la table
`signature` et dans la raison du sceau ; elle n'est pas portée par un certificat personnel.
