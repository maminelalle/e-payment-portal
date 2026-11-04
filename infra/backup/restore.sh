#!/bin/sh
# Restauration : base depuis un dump, puis documents vers le stockage S3.
#   restore.sh /backups/db/epayment-20261104T013000Z.dump
#   restore.sh /backups/db/keycloak-20261104T013000Z.dump keycloak
# ATTENTION : remplace le contenu actuel de la base (objets recréés avec --clean).
set -eu

dump="${1:?Indiquez le fichier .dump à restaurer}"
# Base cible facultative (ex. « keycloak ») ; par défaut la base du portail.
[ -n "${2:-}" ] && DB_NAME="$2"
: "${DB_HOST:=localhost}" "${DB_PORT:=5433}" "${DB_NAME:=epayment}" "${DB_USER:=epayment}"
: "${PGPASSWORD:?PGPASSWORD obligatoire}" "${S3_ENDPOINT:?S3_ENDPOINT obligatoire}" "${S3_BUCKET:=epayment-documents}"
: "${BACKUP_DIR:=/backups}"

echo "Restauration de $dump dans $DB_NAME@$DB_HOST"
pg_restore --clean --if-exists --no-owner --no-acl --single-transaction \
  --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" --dbname "$DB_NAME" "$dump"

if [ -d "$BACKUP_DIR/documents" ]; then
  aws --endpoint-url "$S3_ENDPOINT" s3 mb "s3://$S3_BUCKET" 2>/dev/null || true
  aws --endpoint-url "$S3_ENDPOINT" s3 sync "$BACKUP_DIR/documents" "s3://$S3_BUCKET" --only-show-errors
fi
echo "Restauration terminée."
