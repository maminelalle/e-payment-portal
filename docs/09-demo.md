# 09 — Démo finale (≈ 10 minutes)

Recette HTTPS : https://localhost:8443 (pile `infra/docker-compose.prod.yml`, voir `07-production.md`).
Boîte mail de test : http://localhost:8025 (Mailpit).

## Préparation (la veille)

1. Démarrer la recette et Mailpit :
   `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod up -d` puis
   `docker compose -f infra/docker-compose.yml start mailpit`.
2. Données de démonstration (relançable, ne recrée pas ce qui existe) :
   ```sh
   cd frontend
   E2E_BASE_URL=https://localhost:8443 E2E_ADMIN_EMAIL=e2e-admin@epayment.mr E2E_ADMIN_PASSWORD='<mot de passe>' \
     npx playwright test --config playwright.demo.config.ts
   ```
   Comptes créés (mots de passe dans `frontend/e2e-demo/comptes-demo.txt`, non versionné) :

   | Abonné | Situation | Pour montrer |
   |--------|-----------|--------------|
   | Société Atlas Mauritanie SARL | contrat actif Bankily + Masrvi + Visa, 8 paiements dont 3 échecs | espace abonné complet, reçus, historique |
   | Boulangerie El Amane | contrat actif Bankily + Visa, 3 paiements | répartition des paiements |
   | Mohamed Lemine Ahmed (personne physique) | contrat **à signer** (Masrvi) | signature en direct |

3. Compte administrateur `admin@epayment.mr` : application d'authentification (TOTP) enregistrée sur le
   téléphone, connexion testée une fois.
4. Navigateur : deux fenêtres (admin / abonné en navigation privée), zoom 110 %, Mailpit dans un onglet.

## Déroulé

| # | Écran | Ce qu'on montre | Conception |
|---|-------|-----------------|------------|
| 1 | Connexion admin | Keycloak, mot de passe + code TOTP | §9 authentification renforcée |
| 2 | Tableau de bord | indicateurs du mois, abonnés / contrats / transactions par statut, répartition moyen / type / fournisseur ; clic sur « Échouée » → liste filtrée | §3.1 |
| 3 | Moyens de paiement | catalogue, « Tester les connexions », conditions ; Sedad non raccordé ne s'active pas | §3.3 |
| 4 | Abonnés → Atlas | coordonnées, contrats, **moyens autorisés**, dernières transactions | §3.2 |
| 5 | Contrats → CTR de Lemine | PDF figé + empreinte SHA-256, statut « en attente de signature » | §3.4 |
| 6 | Espace abonné (Lemine) | « Mon contrat » → Signer électroniquement → code reçu dans Mailpit → contrat actif, moyens ouverts aussitôt | §4.2, §5, §6 |
| 7 | Espace abonné (Atlas) | accueil (plafonds du mois), payer 2 500 MRU avec Bankily → reçu PDF avec QR → vérification publique du reçu | §4.3, §4.4 |
| 8 | Transactions (Atlas) | filtres, export Excel ; échecs « solde insuffisant » / « refusé » distincts du statut fournisseur | §4.4, §11 |
| 9 | Admin → contrat d'Atlas | **Créer un avenant** (retirer Visa) → envoi → signature par Atlas → l'original passe « Remplacé », historique des versions, Visa n'est plus proposé | §6.1, §11 |
| 10 | Profil abonné | coordonnées modifiées avec code email, sessions, préférences ; passage en arabe et mode sombre | §4.5 |
| 11 | Paramètres et audit | journal : connexions, signature, avenant, paiements, refus d'accès | §2.1, §9 |

Pour finir (1 min) : architecture (modules, adaptateurs fournisseurs, webhooks signés, NNI/NIF chiffrés),
qualité (tests backend, E2E Playwright, scan OWASP ZAP 0 échec, sauvegarde et restauration testées) et
tableau de conformité `08-conformite-conception.md`.

## Montants utiles (simulateur)

- Wallet : un montant en `,13` est refusé, en `,51` « solde insuffisant », en `,99` confirmé après 60 s.
- Carte : 4111 1111 1111 1111 acceptée · 4000 0000 0000 0002 refusée · 4000 0000 0000 9995 fonds insuffisants.

## Si quelque chose bloque

- Page de connexion qui tourne : vérifier que les conteneurs tournent (`docker ps`) — Keycloak démarre en ~1 min.
- Pas d'email de code : Mailpit arrêté (`docker compose -f infra/docker-compose.yml start mailpit`).
- Fournisseurs « En panne » juste après le démarrage : premier appel du simulateur, relancer « Tester les connexions ».
- Les tests automatiques ajoutent des abonnés « Société E2E … » : pour une recette sans eux, repartir d'une base
  vide (supprimer les volumes de la recette, recréer l'administrateur, relancer le script de démonstration).
