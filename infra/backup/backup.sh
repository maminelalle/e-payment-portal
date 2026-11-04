#!/bin/sh
set -eu

: "${BACKUP_DIR:?BACKUP_DIR doit pointer vers un dossier de sauvegarde}"
: "${DB_HOST:=localhost}"
: "${DB_PORT:=5433}"
: "${DB_NAME:=epayment}"
: "${DB_USER:=epayment}"
: "${PGPASSWORD:?PGPASSWORD doit contenir le mot de passe PostgreSQL}"
: "${S3_ENDPOINT:?S3_ENDPOINT doit être configuré}"
: "${S3_BUCKET:=epayment-documents}"
: "${S3_BACKUP_PREFIX:=backups/$(date -u +%Y-%m-%d)}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"
dump="$BACKUP_DIR/${DB_NAME}-${timestamp}.dump"

pg_dump --format=custom --no-owner --no-acl \
  --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" \
  --file "$dump" "$DB_NAME"

if command -v aws >/dev/null 2>&1; then
  aws --endpoint-url "$S3_ENDPOINT" s3 cp "$dump" "s3://${S3_BUCKET}/${S3_BACKUP_PREFIX}/$(basename "$dump")"
else
  echo "aws CLI introuvable : dump PostgreSQL conservé localement dans $dump" >&2
  exit 1
fi

find "$BACKUP_DIR" -type f -name '*.dump' -mtime +"${BACKUP_RETENTION_DAYS:-30}" -delete
