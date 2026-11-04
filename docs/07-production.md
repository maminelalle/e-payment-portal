# Préparation de production

## Variables obligatoires

Configurer les variables d'environnement sans les committer :

- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `KEYCLOAK_URL`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_BACKEND_SECRET`
- `SIGNATURE_SEAL_KEYSTORE`, `SIGNATURE_SEAL_KEYSTORE_PASSWORD`
- `SIGNATURE_OTP_SECRET` (clé HMAC des codes de signature)
- `RECEIPT_VERIFICATION_SECRET` (clé HMAC du QR code de vérification des reçus)
- clés et secrets des fournisseurs de paiement
- `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`

Le certificat de scellement de production doit être un keystore PKCS#12 fourni par l'organisation.
Le certificat auto-signé est réservé au développement.

## HTTPS et proxy

Le proxy public doit terminer TLS, rediriger HTTP vers HTTPS et transmettre uniquement les en-têtes
proxy provenant de ce proxy. Le backend ne doit pas être exposé directement sur Internet.
Prévoir les certificats dans un volume ou un secret géré par l'orchestrateur, jamais dans Git.

## Sauvegardes

Le script `infra/backup/backup.sh` crée un dump PostgreSQL compressé et le copie vers le stockage S3.
Lancer ce script avec un compte S3 dédié, une politique limitée à l'écriture du préfixe de sauvegarde et
une planification quotidienne. Tester régulièrement une restauration sur un environnement séparé.

## Contrôles avant mise en ligne

- `backend/mvnw verify` avec Docker disponible.
- `frontend/npm ci` puis `frontend/npx ng test --watch=false` et `frontend/npx ng build`.
- Vérification manuelle du flux Keycloak, de la signature PAdES et d'un paiement de chaque moyen.
- Scan OWASP ZAP sur l'URL de recette, puis correction et nouvelle analyse des alertes retenues.
