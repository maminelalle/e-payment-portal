#!/bin/sh
# Sauvegarde complète : dump PostgreSQL (format custom) + copie des documents S3 (contrats, PDF signés, reçus).
# EXTRA_DB_NAMES : bases annexes à sauvegarder aussi (ex. « keycloak » : comptes et MFA).
# Copie externe facultative vers un second stockage S3 (OFFSITE_*) : à configurer en production.
set -eu

: "${BACKUP_DIR:?BACKUP_DIR doit pointer vers un dossier de sauvegarde}"
: "${DB_HOST:=localhost}"
: "${DB_PORT:=5433}"
: "${DB_NAME:=epayment}"
: "${DB_USER:=epayment}"
: "${PGPASSWORD:?PGPASSWORD doit contenir le mot de passe PostgreSQL}"
: "${S3_ENDPOINT:?S3_ENDPOINT doit être configuré}"
: "${S3_BUCKET:=epayment-documents}"
: "${BACKUP_RETENTION_DAYS:=30}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR/db" "$BACKUP_DIR/documents"
dumps=""

for db in "$DB_NAME" ${EXTRA_DB_NAMES:-}; do
  dump="$BACKUP_DIR/db/${db}-${timestamp}.dump"
  echo "[$(date)] Dump PostgreSQL $db -> $dump"
  pg_dump --format=custom --no-owner --no-acl \
    --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" \
    --file "$dump.part" "$db"
  mv "$dump.part" "$dump"
  # Contrôle : un dump illisible fait échouer la sauvegarde plutôt que d'être conservé en silence.
  pg_restore --list "$dump" > /dev/null
  dumps="$dumps $dump"
done

echo "[$(date)] Documents s3://$S3_BUCKET -> $BACKUP_DIR/documents"
aws --endpoint-url "$S3_ENDPOINT" s3 sync "s3://$S3_BUCKET" "$BACKUP_DIR/documents" --only-show-errors

if [ -n "${OFFSITE_S3_BUCKET:-}" ]; then
  echo "[$(date)] Copie externe -> s3://$OFFSITE_S3_BUCKET"
  for dump in $dumps; do
    AWS_ACCESS_KEY_ID="$OFFSITE_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$OFFSITE_SECRET_KEY" \
      aws ${OFFSITE_S3_ENDPOINT:+--endpoint-url "$OFFSITE_S3_ENDPOINT"} s3 cp "$dump" \
      "s3://$OFFSITE_S3_BUCKET/db/$(basename "$dump")" --only-show-errors
  done
  AWS_ACCESS_KEY_ID="$OFFSITE_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$OFFSITE_SECRET_KEY" \
    aws ${OFFSITE_S3_ENDPOINT:+--endpoint-url "$OFFSITE_S3_ENDPOINT"} s3 sync "$BACKUP_DIR/documents" \
    "s3://$OFFSITE_S3_BUCKET/documents" --only-show-errors
fi

find "$BACKUP_DIR/db" -type f -name '*.dump' -mtime +"$BACKUP_RETENTION_DAYS" -delete
echo "[$(date)] Sauvegarde terminée :$dumps"
