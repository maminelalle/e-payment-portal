# 05 — Avancement, mises à jour et reste à faire

Journal de suivi du projet, à mettre à jour à chaque étape. Le détail case par case est dans le
[plan sur 12 semaines](04-plan-12-semaines.md) ; les choix et leurs raisons dans les [décisions](00-decisions.md).

**Dernière mise à jour : 04/11/2026** — semaine 11 terminée, semaine 12 à faire.

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
| 11 | Notifications et tableaux de bord | Terminée |
| 12 | Recette et mise en ligne | **À faire** |

Tests automatisés : 64 backend, 8 simulateur, 12 frontend, tous au vert.

## Mises à jour (de la plus récente à la plus ancienne)

### 04/11/2026 — Design system Claude Design et améliorations UX
- Charte du design system appliquée : IBM Plex Sans / Sans Arabic / Mono, bleu sceau `#1D44B8`, tokens clair et
  sombre (`frontend/src/styles/tokens.css`), classes `ep-…` (`styles/components.css`), preset PrimeNG recoloré.
  Les anciennes variables `--ep-*` restent comme alias.
- Audit de l'interface corrigé : pastilles de statut avec icône (jamais la couleur seule), en-têtes de tableaux en
  casse de phrase, références en mono isolées en LTR (`<bdi>`), montants en chiffres tabulaires, focus clavier visible,
  liens sans violet « visité », carte de moyen inactive lisible (seul le logo est grisé), « Retirer le logo » confirmé,
  journal d'audit lisible (libellé traduit, code en infobulle, identifiant raccourci + copier, `::1` → localhost),
  stepper du contrat conforme au composant `ContractStepper` (corrige l'affichage arabe), téléphones `+222 22 00 00 07`.
- En-tête commun admin / abonné : langue (sélecteur segmenté) · thème · notifications · utilisateur · déconnexion.
  Centre de notifications : emails reçus (abonné) ; fournisseurs en panne, transactions suspectes et contrats à traiter (admin).
- Admin : recherche globale **Ctrl+K** (abonnés, contrats, transactions, pages) ; état des fournisseurs en direct
  dans la barre latérale et sur l'écran des moyens (« Tester les connexions ») ; menu mobile sous 1024 px.
- Transactions : totaux calculés par le serveur sur les filtres (« 10 transactions · 161 500,00 MRU encaissés sur la période »),
  export CSV pour Excel (abonné et admin). API `GET …/totaux` et `GET …/export.csv`.
- Alertes de transactions suspectes (montant ≥ 3× la moyenne de l'abonné, 3 échecs en une heure) : `GET /api/admin/alertes`.
- Reçu PDF avec QR code de vérification (D26) ; page publique `/verification/recu/TRX-…`.
- Page publique de statut `/statut` (`GET /api/public/statut`) ; manifeste PWA (installation sur mobile).
- Messages d'erreur plus clairs quand le service de paiement ne répond pas.

### 04/11/2026 — Profil abonné
- Écran B5 : coordonnées en lecture seule, changement de mot de passe via Keycloak, sessions ouvertes
  (fermeture d'une session ou de toutes), préférences des emails de paiement et historique des notifications.
- API `GET /api/abonne/profil`, gestion des sessions Keycloak et préférences de notification ; le profil est accessible
  depuis la navigation de l'espace abonné.
- Déconnexion journalisée dans l'audit et sortie avec remplacement de l'URL ; les routes protégées redirigent
  vers l'authentification après déconnexion. Le PDF signé est rechargé immédiatement pour afficher le cachet visible.

### 03/11/2026 — Semaine 11 : tableau de bord abonné
- Accueil abonné : montant payé, paiements réussis et échoués du mois ; jauge de chaque plafond mensuel
  (paiements réussis + en cours, comme le contrôle du portail), alerte à 80 % et à 100 %.
  API `GET /api/abonne/tableau-de-bord`.

### 03/11/2026 — Semaine 11 : journal d'audit
- Écran « Paramètres et audit » : journal filtrable par acteur, ressource (TRX-…, CTR-…), action (libellés en français),
  résultat et période ; détail JSON de chaque événement. API `GET /api/admin/audit` enrichie + `GET /api/admin/audit/actions`.

### 03/11/2026 — Semaine 11 : tableau de bord admin
- Écran d'accueil admin (maquette A1) : abonnés actifs, contrats en attente, transactions et montant encaissé du mois
  (comparés à la même durée du mois précédent), courbe du volume par jour, répartition par moyen, contrats à traiter,
  dernières transactions. API `GET /api/admin/tableau-de-bord?jours=7|30|90`.
- Couleur Masrvi ajustée (`#0D9488`, V11) pour rester lisible dans les graphiques.

### 03/11/2026 — Semaine 11 : emails
- Nouveaux emails à l'abonné : contrat activé, paiement réussi (avec renvoi vers le reçu), paiement échoué (avec le motif).
  Un seul email par paiement, quel que soit le chemin (réponse immédiate, webhook, consultation, rapprochement).
- Chaque email est tracé dans la table `notification` (V10) : envoyé ou en échec, avec l'erreur.

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
- [x] Emails : contrat activé, paiement réussi, paiement échoué
- [x] Tableau de bord admin avec graphiques (maquette A1)
- [x] Tableau de bord abonné
- [x] Profil abonné (données Keycloak)
- [x] Écran du journal d'audit

### Semaine 12 — Recette et mise en ligne
- [~] Tests de bout en bout Playwright : 13 scénarios écrits ; public, admin (avec TOTP) et mobile validés sur la recette, scénarios abonné à lancer sur un compte avec contrat actif
- [ ] Scan de sécurité OWASP ZAP et corrections (script prêt : infra/security/zap-baseline.sh)
- [x] Configuration de production : Docker Compose, Nginx, HTTPS (pile complète testée en local sur https://localhost:8443)
- [~] Sauvegardes quotidiennes PostgreSQL + documents S3 + base Keycloak : service planifié écrit, restauration à tester
- [~] Guides administrateur et abonné (1 page chacun) : ébauches, nouveautés à ajouter
- [ ] Démo finale — **jalon : version pilote en ligne**

### Améliorations proposées, non réalisées (à arbitrer)
- [ ] Lien / QR code de paiement à envoyer à un client ; paiements récurrents ou programmés
- [ ] Relevés mensuels en PDF ; moyens et montants favoris
- [ ] Demande de nouveau moyen depuis le portail (avenant au contrat) ; support / réclamation sur une transaction
- [ ] Plusieurs utilisateurs par personne morale (comptable, caissier) ; rôles admin fins (super-admin, opérateur, auditeur)
- [ ] Commissions et facture mensuelle par abonné ; modèles d'email et de SMS modifiables ; invitation par import CSV
- [ ] API et webhooks pour les abonnés (clés générées depuis le portail) ; notifications push (service worker PWA)
- [ ] Connexion sans mot de passe (passkey, via Keycloak WebAuthn)
- [ ] Traduction arabe complète : l'actuelle traduit le texte affiché par dictionnaire ; une vraie i18n par clés reste à faire

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
