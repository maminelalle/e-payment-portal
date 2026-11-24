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

### 24/11/2026 — Rapport de stage
- Développement arrêté ; rapport rédigé : `docs/rapport/Rapport_de_stage_Portail_E-Payment.docx` (et son PDF), 40 pages,
  6 chapitres, 27 figures, 14 tableaux, résumé FR / abstract EN, sommaire automatique, annexes.
- Figures : captures réelles de la recette (`frontend/e2e-demo/captures.spec.ts`, données de démonstration), pages des
  PDF produits par le portail, schémas (architecture, modèle de données, workflow, états du contrat, paiement,
  planning) dont la source est `docs/rapport/sources/schemas.html`.
- Rapport de pré-embauche chez **Smart MS SA** (Direction des Opérations et Supports, département CET / P. Développement,
  poste Ingénieur de Développement et Intégration), encadrant Edje ; logo Smart en page de garde.
- Reste surligné en jaune (facultatif) : activités, date de création, effectif et organigramme de Smart MS SA (§ 1.1).
- Régénérer : `node docs/rapport/sources/generer-rapport.cjs` (le paquet npm `docx` doit être installé), puis mettre à
  jour le sommaire dans Word.

### 23/11/2026 — Relevé mensuel PDF (et facture des commissions)
- Nouveau document `releve.html` (StatementService) : pour un mois donné, toutes les transactions de l'abonné
  (réussies et échouées), le total encaissé, le détail par moyen de paiement et les commissions dues au titre du
  contrat (article 4) ; il sert aussi de facture mensuelle des commissions.
- Abonné : bouton « Relevé PDF » avec choix du mois (12 derniers) sur la page Transactions
  (`GET /api/abonne/paiements/releve?mois=AAAA-MM`).
- Admin : même bouton sur la fiche abonné (`GET /api/admin/transactions/releve?abonne=…&mois=…`, réservé à l'admin).
- Mois mal formé ou futur refusé (400) ; textes traduits en arabe.
- Vérifié : backend (test du contenu PDF, droits et erreurs), frontend 14/14, E2E 23/23 sur la recette (nouveau
  scénario de téléchargement du relevé).

### 23/11/2026 — Points secondaires
- Page de connexion Keycloak aux couleurs du portail : thème `epayment` (`infra/keycloak/themes`), fond clair,
  pastille « EP », carte blanche, bouton et liens dans la couleur de la marque ; intégré à l'image Keycloak et
  activé dans les deux realms (`loginTheme`).
- PDF du contrat : pied de page dans la même police que le document (migration V18) ; un test vérifie qu'aucune
  police Times ne reste dans l'aperçu (il échouait avant le correctif).
- Arabe : 23 messages de notification à texte fixe traduits (« Profil mis à jour », « Export impossible »…).

### 23/11/2026 — Dernières retouches d'interface
- Moyens de l'abonné : plafonds présentés « Par opération / Par mois ».
- Liste des abonnés : colonne « Contrat » (référence et statut du contrat courant, une seule requête par page) ;
  avertissement si le téléphone saisi appartient déjà à un abonné (non bloquant : un numéro peut être partagé).
- Transactions sur téléphone : une carte par transaction (libellé / valeur), plus de défilement horizontal.
- Arabe dans l'espace admin : 130 textes fixes ajoutés au dictionnaire (menus, écrans, filtres, formulaires,
  contrats, modèles) ; les libellés des cartes mobiles sont traduits aussi.
- Vérifié : backend 78/78, frontend 14/14, E2E 22/22 sur la recette ; captures FR / AR, bureau et mobile.

### 23/11/2026 — MFA WhatsApp fiabilisée, commissions au tableau de bord
- Commission (D32) : indicateur « Commissions ce mois » au tableau de bord admin (comparé au mois précédent).
- MFA admin WhatsApp (D33), corrections de l'authentificateur Keycloak :
  - mauvais code : le formulaire revient avec les essais restants (avant : page d'erreur, connexion à refaire) ;
  - pas de nouveau WhatsApp si la page est rechargée dans la minute (coût et spam) ;
  - empreinte du code avec un sel propre à la connexion (ne dépend plus de \`VONAGE_API_SECRET\`) ;
  - attributs \`phoneNumber\` et \`mfaChannel\` déclarés au profil utilisateur, modifiables par un administrateur
    Keycloak seulement : ils étaient ignorés par Keycloak, le code partait toujours vers \`VONAGE_ADMIN_PHONE\`.
- Canal email pour le développement et les tests : la pile de dev utilise l'image Keycloak avec l'extension et
  envoie le code dans Mailpit (avant : realm de dev pointant vers un authentificateur absent de son image) ; le
  compte de test E2E reçoit son code par email et Playwright le lit dans Mailpit.
- Vérifié sur la recette : 22 E2E verts avec la nouvelle MFA ; mauvais code puis bon code ; un seul email malgré un
  rechargement. Backend 78/78, frontend 14/14.

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
- MFA admin par OTP WhatsApp Vonage Messages, avec authenticator Keycloak personnalisé, JWT d'application, numéro E.164 et variables secrètes hors dépôt ; désactivable en local (`infra/keycloak/mfa-admin.sh off|on`).

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
- [x] Tests de bout en bout Playwright : 16 scénarios, tous verts sur la recette HTTPS (dont le parcours complet nouvel abonné → signature → paiement)
- [x] Scan de sécurité OWASP ZAP : 0 échec, 2 avertissements acceptés et justifiés (infra/security/zap-rules.tsv)
- [x] Configuration de production : Docker Compose, Nginx, HTTPS (pile complète testée en local sur https://localhost:8443)
- [x] Sauvegardes quotidiennes PostgreSQL + documents S3 + base Keycloak ; restauration testée (base temporaire, mêmes nombres de lignes)
- [x] Guides administrateur et abonné (1 page chacun), à jour des nouveautés
- [~] Démo finale — **jalon : version pilote en ligne** : scénario prêt (docs/09-demo.md), données de démonstration créées sur la recette (3 abonnés, 11 paiements), tableau de conformité à la conception (docs/08-conformite-conception.md) ; reste à la présenter puis fusionner semaine-12 dans main
- [x] Correctifs de recette (04/11) :
  - connexion bloquée après réimport du realm : le compte local est rattaché au nouvel identifiant Keycloak si l'email est vérifié (audit ACCOUNT_RELINKED) ;
  - changement de nom de l'admin : l'ancien nom de famille Keycloak est vidé, les erreurs du serveur sont affichées ;
  - export Excel/CSV : indicateur de chargement et message d'erreur ;
  - favicon remplacé par le logo E-Payment (icon.svg).
  Vérifié dans le navigateur : profil, export, test des fournisseurs, connexion abonné.
- [x] Défauts trouvés par la recette (04/11) :
  - traduction automatique : remettait le premier texte affiché à chaque changement (bouton « Payer 125,00 MRU », résultats Ctrl+K), même en français ;
  - production : feuille de styles globale bloquée par la CSP (chargement différé d'Angular) ; recherche Ctrl+K : réponse d'une ancienne saisie ignorée ;
  - nginx : limite de débit de la connexion sur les seuls POST (réponse 429), en-têtes de sécurité sur toutes les réponses ;
  - sauvegarde : image reconstruite, la base Keycloak est bien incluse.

### Écarts avec le document de conception (relecture du 05/11) — tous traités, voir docs/08-conformite-conception.md
- [x] Tableau de bord admin (§3.1) : abonnés par statut (total, actifs, suspendus), contrats par statut (brouillon → résilié), transactions par statut (réussies, échouées, en attente, remboursées) et par fournisseur — fait le 05/11 : 3 blocs de statuts (chaque ligne ouvre la liste filtrée), répartition par moyen / type / fournisseur ; tests backend et E2E
- [x] Avenant (§6.1, §11, H3) : remplacer un contrat actif par un nouveau, lié à l'original, avec historique des versions — fait le 05/11 (D29) : « Créer un avenant » sur un contrat actif (brouillon prérempli, CTR-…-A1), signature par l'abonné, à l'activation l'original passe « Remplacé » et ses droits sont révoqués dans la même transaction ; suspension reportée ; PDF « Avenant n°… au contrat … » ; historique des versions sur la fiche ; plafonds mensuels continus ; tests backend (2) et E2E de bout en bout
- [x] Catalogue (§3.3) : ajouter un moyen et modifier sa description, ses instructions et ses conditions — fait le 05/11 (D28) : ajout sur un fournisseur raccordé (créé désactivé), modification du nom, de la pastille, de la couleur et des textes, conditions d'utilisation / règles d'acceptation affichées à l'abonné, activation refusée si le fournisseur n'est pas raccordé (Sedad). Trouvé et corrigé au passage : les boutons « Créer l'abonné » et « Enregistrer » (fiche abonné) ne faisaient rien (attribut form ignoré par p-button) ; le parcours E2E crée désormais l'abonné par l'écran
- [x] Profil abonné (§4.5) : mettre à jour ses coordonnées (téléphone, adresse) avec vérification — fait le 05/11 (D27) : code par email (5 min, 3 essais, 1 demande par minute), audit avant/après, avis de sécurité par email. Trouvé et corrigé au passage : débordement de l'en-tête abonné sur téléphone, choix de langue absent sur mobile, icônes remplacées par des carrés en arabe
- [x] Modèles de contrat (§3.4) — fait le 05/11 (D31) : écran « Modèles » (versions, nombre de contrats, aperçu PDF avec données d'exemple, versions proposées / archivées, au moins une proposée) ; le contenu reste livré par migration (D14)
- [x] Chiffrement des données sensibles en base (§9 : NNI/NIF) — fait le 05/11 (D30) : AES-256-GCM + empreinte HMAC pour l'unicité, clé DATA_ENCRYPTION_KEY, données existantes chiffrées au démarrage (14 sur la recette)
- Hors périmètre du stage (D12, « après les 3 mois ») : vraies API fournisseurs, remboursements et contestations, coffre-fort de secrets, rôles admin fins

### Améliorations proposées, non réalisées (à arbitrer)
- [ ] Lien / QR code de paiement à envoyer à un client ; paiements récurrents ou programmés
- [x] Relevés mensuels en PDF (abonné et admin) — fait le 23/11
- [ ] Moyens et montants favoris
- [ ] Demande de nouveau moyen depuis le portail (avenant au contrat) ; support / réclamation sur une transaction
- [ ] Plusieurs utilisateurs par personne morale (comptable, caissier) ; rôles admin fins (super-admin, opérateur, auditeur)
- [x] Commission contractuelle sur chaque paiement réussi (taux et montant figés sur la transaction, reçus, historiques, totaux et exports)
- [x] Facture mensuelle par abonné : le relevé mensuel détaille les commissions dues du mois — fait le 23/11
- [ ] Modèles d'email et de SMS modifiables ; invitation par import CSV
- [ ] API et webhooks pour les abonnés (clés générées depuis le portail) ; notifications push (service worker PWA)
- [ ] Connexion sans mot de passe (passkey, via Keycloak WebAuthn)
- [~] Traduction arabe : espaces abonné et admin couverts (texte fixe, par dictionnaire) ; les messages dynamiques (notifications, erreurs du serveur) restent en français, une vraie i18n par clés reste à faire

### Points secondaires
- [x] Thème Keycloak aux couleurs du portail (page de connexion)
- [x] Pied de page du PDF de contrat dans la police du document
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
