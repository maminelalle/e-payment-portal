# 08 — Conformité au document de conception

Relecture complète de `docs/references/Conception_Portail_E-Payment.docx` le 05/11/2026 : chaque exigence, où elle
est réalisée et ce qui la prouve. ✅ réalisé · ◐ réalisé dans le périmètre du stage (décision notée) · ⏳ hors
périmètre du stage (D12).

Tests cités : backend `backend/src/test/java/mr/epayment/portal/**` (JUnit, PostgreSQL et S3 réels via Testcontainers),
E2E `frontend/e2e/*.spec.ts` (Playwright sur la recette HTTPS).

## 1. Principe fondamental

| Exigence | État | Réalisation | Preuve |
|----------|------|-------------|--------|
| Les moyens visibles et utilisables sont déterminés par le contrat | ✅ | droits calculés depuis le contrat actif (`EntitlementEngine`) | `WorkflowTests`, `abonne.spec` |
| Droits activés automatiquement après vérification de la signature | ✅ | activation après signature, empreinte du PDF scellé revérifiée (D18) | `WorkflowTests`, `parcours.spec` |
| Jamais de paiement avec un moyen non prévu | ✅ | contrôle serveur à chaque paiement (`authorize`) | `WorkflowTests` (refus `NOT_ENTITLED`) |

## 2. Profils et principes d'accès

| Exigence | État | Réalisation | Preuve |
|----------|------|-------------|--------|
| Espaces Administrateur et Abonné | ✅ | `/admin`, `/abonne`, rôles Keycloak `ADMIN` / `ABONNE` | `SecurityRulesTests` |
| Admin selon ses permissions | ◐ | un rôle `ADMIN` ; rôles fins (opérateur, auditeur) après le stage | — |
| Abonné : uniquement ses propres données | ✅ | abonné toujours déduit du jeton côté serveur | `SubscriberProfileTests`, `SecurityRulesTests` |
| Droits contrôlés côté serveur, pas par masquage | ✅ | règles par URL + contrôle métier à chaque paiement | `SecurityRulesTests`, `WorkflowTests` |
| Actions sensibles journalisées | ✅ | `audit_log` (connexions, refus d'accès, actions admin, signatures, paiements, coordonnées…) consultable écran A6 | `SecurityRulesTests`, `admin.spec` |

## 3. Espace Administrateur

| Exigence | État | Réalisation | Preuve |
|----------|------|-------------|--------|
| 3.1 Abonnés total / actifs / suspendus | ✅ | tableau de bord, bloc « Abonnés » par statut | `AdminDashboardTests`, `admin.spec` |
| 3.1 Contrats brouillon, à signer, actifs, expirés, résiliés | ✅ | bloc « Contrats » (8 statuts, dont « Remplacé ») | `AdminDashboardTests` |
| 3.1 Nombre et montant des transactions | ✅ | indicateurs du mois, volume quotidien | `AdminDashboardTests` |
| 3.1 Répartition par wallet, carte et fournisseur | ✅ | « Répartition » : moyen / type / fournisseur | `AdminDashboardTests`, `admin.spec` |
| 3.1 Réussies, échouées, en attente, remboursées | ✅ | bloc « Transactions » par statut | `AdminDashboardTests` |
| 3.2 Créer, consulter, modifier, activer, suspendre, désactiver un abonné | ✅ | écran Abonnés + fiche ; compte Keycloak et invitation créés | `SubscriberAdminTests`, `parcours.spec` |
| 3.2 Consulter ses contrats, moyens autorisés, transactions | ✅ | fiche abonné : contrats, « Moyens de paiement autorisés », dernières transactions | `WorkflowTests` |
| 3.2 Champs : référence, type, nom, NNI/NIF, téléphone, email, adresse, statut | ✅ | `subscriber` ; NNI 10 chiffres, NIF 8 chiffres, téléphone +222 (H8) | `SubscriberAdminTests` |
| 3.3 Catalogue wallets et cartes (Bankily, Masrvi, Sedad, Visa, Mastercard) | ✅ | 5 moyens livrés (Sedad non raccordé, D28) | `CatalogAdminTests` |
| 3.3 Ajouter, modifier, désactiver un moyen | ✅ | « Ajouter un moyen », « Modifier », interrupteur, logo | `CatalogAdminTests`, `admin.spec` |
| 3.3 Paramètres d'intégration du fournisseur | ◐ | par configuration (URL, clé, secret webhook), jamais en base — `07-production.md` | `HttpPaymentProvidersTests` |
| 3.3 Conditions et limites | ✅ | conditions d'utilisation / règles d'acceptation ; plafonds au contrat | `CatalogAdminTests` |
| 3.3 Superviser la disponibilité | ✅ | « Tester les connexions », état dans le menu, page publique `/statut`, alerte cloche | `admin.spec`, `public.spec` |
| 3.4 Créer et gérer des modèles de contrat | ◐ | écran « Modèles » : versions, aperçu, proposer / archiver ; contenu par migration (D14, D31) | `ContractAdminTests`, `admin.spec` |
| 3.4 Associer à un abonné, choisir wallets et cartes | ✅ | assistant de contrat | `ContractAdminTests` |
| 3.4 Dates, frais / commission, plafonds | ✅ | assistant étapes 2-3 | `ContractAdminTests` |
| 3.4 Générer le PDF, envoyer pour signature | ✅ | OpenHTMLtoPDF, PDF figé + SHA-256, email | `ContractAdminTests` |
| 3.4 Suivre la signature, archiver la version signée et ses preuves | ✅ | fiche contrat, PDF scellé conservé avec l'original | `SignatureTests` |

## 4. Espace Abonné

| Exigence | État | Réalisation | Preuve |
|----------|------|-------------|--------|
| 4.1 Synthèse : infos, statut et dates du contrat, moyens, dernières transactions, notifications | ✅ | accueil abonné + cloche + plafonds du mois | `abonne.spec` |
| 4.2 Notification « contrat disponible » | ✅ | email « Votre contrat … est prêt à être signé » | `ContractAdminTests` |
| 4.2 Consulter et télécharger avant d'accepter | ✅ | « Mon contrat » : PDF affiché et téléchargeable | `parcours.spec` |
| 4.2 Signature, vérification, preuves | ✅ | code par email (5 min, 3 essais) + sceau PAdES, preuve en base (D17) | `SignatureTests`, `PadesSealerTests` |
| 4.2 Droits ouverts automatiquement après activation | ✅ | activation immédiate après signature (D18) | `WorkflowTests` |
| 4.3 Uniquement les moyens du contrat actif, avec nom, type, logo, statut, instructions | ✅ | « Moyens de paiement » | `WorkflowTests`, `abonne.spec` |
| 4.3 Conditions et instructions d'utilisation | ✅ | instructions + conditions affichées | `CatalogAdminTests` |
| 4.3 Payer, voir le résultat, télécharger le reçu | ✅ | tiroir de paiement, reçu PDF avec QR de vérification | `PaymentFlowTests`, `abonne.spec` |
| 4.4 Historique : référence, date, moyen, montant, statut, reçu | ✅ | « Transactions » + filtres + export Excel/CSV | `PaymentFlowTests`, `abonne.spec` |
| 4.5 Consulter ses informations | ✅ | « Mon profil » | `SubscriberProfileTests` |
| 4.5 Mettre à jour ses coordonnées avec vérification | ✅ | téléphone et adresse, code par email (D27) | `SubscriberProfileTests`, `abonne.spec` |
| 4.5 Changer le mot de passe | ✅ | parcours Keycloak « UPDATE_PASSWORD » | — (Keycloak) |
| 4.5 Consulter et révoquer sessions / appareils | ✅ | sessions ouvertes, « Fermer », « Me déconnecter partout » | `SubscriberProfileTests` |
| 4.5 Préférences de notification | ✅ | emails de paiement activables | `SubscriberProfileTests` |

## 5. Processus métier global

Création de l'abonné → préparation du contrat → envoi → signature → vérification → activation des droits →
paiements → suivi : ✅ de bout en bout, par l'écran, dans `parcours.spec` (et `WorkflowTests` côté API).

## 6. Statuts et droits

| Exigence | État | Réalisation | Preuve |
|----------|------|-------------|--------|
| Brouillon, à signer, signé : pas d'accès aux moyens | ✅ | aucun droit avant l'activation | `WorkflowTests` |
| Actif : accès pendant la période de validité | ✅ | période contrôlée à chaque paiement | `WorkflowTests` (`OUTSIDE_VALIDITY`) |
| Suspendu, expiré, résilié : droits désactivés / révoqués | ✅ | suspension, job quotidien d'expiration, résiliation | `WorkflowTests` |
| 6.1 Activation automatique, idempotente, traçable | ✅ | `INSERT … ON CONFLICT DO NOTHING`, audit `ENTITLEMENTS_OPENED` | `WorkflowTests` |
| 6.1 Modification des moyens par avenant | ✅ | avenant `CTR-…-A1`, original « Remplacé » (D29) | `WorkflowTests`, `parcours.spec` |
| 6.1 Contrôle backend à chaque accès et paiement | ✅ | Spring Security + `authorize` | `SecurityRulesTests`, `WorkflowTests` |
| 6.1 Règles en cas de plusieurs contrats | ✅ | un seul contrat en vigueur (H3), index unique | `WorkflowTests` |

## 7. Architecture

| Exigence | État | Réalisation |
|----------|------|-------------|
| Frontend Angular ou React | ✅ | Angular 21 + PrimeNG (D2, D9) |
| Backend Java / Spring Boot, API REST | ✅ | Spring Boot 4.1, monolithe modulaire Spring Modulith (D1) — `ModularityTests` |
| PostgreSQL | ✅ | PostgreSQL 16, Flyway V1 → V17 |
| Stockage documentaire sécurisé | ✅ | stockage S3 (SeaweedFS, D10), documents servis seulement après contrôle d'accès |
| 7.1 Modules Auth, Subscriber, Contract, E-Signature, Payment Methods, Entitlements, Gateway, Transactions, Notification, Audit | ✅ | un paquetage par module (`auth`, `subscriber`, `contract`, `signature`, `catalog`, `entitlement`, `gateway`, `transaction`, `notification`, `audit`) |
| 7.2 HTTPS et API REST sécurisées | ✅ | Nginx TLS, HSTS, CSP, jetons JWT — scan ZAP 0 échec |
| 7.2 Couche d'adaptation par fournisseur | ✅ | `PaymentProvider` : Bankily, Masrvi, passerelle carte — `HttpPaymentProvidersTests` (WireMock) |
| 7.2 Webhooks signés vérifiés | ✅ | HMAC + horodatage ± 5 min + anti-rejeu (D20) — `WebhookTests` |
| 7.2 Stockage avec contrôle d'accès et rétention | ✅ | politique de conservation dans `07-production.md` |

## 8. Modèle de données

Les 13 entités (User, Role/Permission, Subscriber, Contract_Template, Contract, Payment_Method, Contract_Payment_Method,
Signature, Subscriber_Entitlement, Payment_Provider, Transaction, Notification, Audit_Log) et les relations 8.1 :
✅ — voir `02-modele-donnees.md` (à jour de V17). Chaque transaction est rattachée au contrat qui l'a autorisée ; le taux et le montant de commission du contrat sont figés sur chaque transaction réussie.

## 9. Sécurité et conformité

| Exigence | État | Réalisation | Preuve |
|----------|------|-------------|--------|
| HTTPS / TLS partout | ✅ | Nginx, redirection HTTP → HTTPS, HSTS | scan ZAP |
| Mots de passe hachés, jamais en clair | ✅ | gérés par Keycloak, jamais vus par le portail | — |
| Rôles et permissions côté serveur | ✅ | — | `SecurityRulesTests` |
| Authentification renforcée admin et opérations sensibles | ✅ | TOTP obligatoire pour l'admin ; code email pour signer et changer ses coordonnées | `admin.spec` (TOTP calculé) |
| Signatures vérifiées, intégrité, identité, preuves | ✅ | D15, D17 | `SignatureTests` |
| Webhooks vérifiés, anti-rejeu | ✅ | D20 | `WebhookTests` |
| Idempotence des paiements | ✅ | `Idempotency-Key`, unicité (abonné, clé) | `PaymentFlowTests` |
| Données sensibles chiffrées | ✅ | NNI / NIF AES-256-GCM (D30) | `SubscriberAdminTests` |
| Secrets dans un coffre-fort | ◐ | variables d'environnement hors dépôt (`.env.prod`, ignoré) ; Vault après le stage | — |
| Logs sans donnée de carte ni secret | ✅ | aucun PAN en base ni en log (PAN masqué seulement), codes jamais journalisés | `HttpPaymentProvidersTests`, `WebhookTests` |
| Conservation, archivage, sauvegarde, restauration | ✅ | `07-production.md` ; sauvegarde quotidienne, restauration testée | recette du 04/11 |
| Pas de CVV ni de PAN complet ; passerelle certifiée | ✅ | saisie de la carte sur la page hébergée de la passerelle | `HttpPaymentProvidersTests` (requête sans carte), `WebhookTests` (PAN masqué) |
| 9.1 Points réglementaires à valider | ⏳ | à confirmer par l'organisation (D12) | — |

## 10. Découpage en 8 phases

Les 8 phases (authentification et abonnés, catalogue, contrats, signature, droits, intégrations, paiements,
notifications / audit / reporting) : ✅ — suivi semaine par semaine dans `05-avancement.md`.

## 11. Règles de gestion

| Exigence | État | Réalisation |
|----------|------|-------------|
| Pas d'accès avant signature validée et contrat actif | ✅ | `WorkflowTests` |
| Fuseau horaire défini | ✅ | `Africa/Nouakchott` (H4) pour dates de validité, jobs et graphiques |
| Avenants liés à l'original, historique des versions | ✅ | `parent_contract_id`, `amendment_number`, « Historique des versions » (D29) |
| Désactiver un droit n'efface pas l'historique | ✅ | droits et transactions jamais supprimés |
| Statut interne distinct du statut fournisseur | ✅ | `status` / `provider_status` |
| Paiements en attente réconciliés | ✅ | rapprochement toutes les 2 min (D24) — `PaymentFlowTests` |
| Remboursements, annulations, contestations | ⏳ | statut `REFUNDED` prévu ; parcours après le stage, selon les capacités des fournisseurs |
| Clés API et secrets isolés des données métier | ✅ | configuration seulement, `credentials_ref` en base |

## 12. Éléments à confirmer

Tranchés par les hypothèses H1 à H9 de `00-decisions.md` (projet de stage, D12) : rôle du portail (l'abonné paie),
comptes propres à chaque abonné, signature intégrée (OTP + PAdES), fournisseurs simulés, un contrat en vigueur,
fuseau, devise MRU. ⏳ validation par une organisation réelle.

## Bilan

Toutes les exigences fonctionnelles et techniques de la conception sont réalisées et testées. Restent, par décision
du périmètre de stage : rôles admin fins, coffre-fort de secrets, remboursements et contestations, raccordement aux
vraies API et validation réglementaire.
