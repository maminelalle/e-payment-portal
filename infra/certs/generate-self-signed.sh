#!/bin/sh
# Certificat auto-signé pour la RECETTE uniquement (le navigateur affichera un avertissement).
# En production : certificat Let's Encrypt (voir docs/07-production.md), copié ici sous les mêmes noms.
#   sh infra/certs/generate-self-signed.sh epayment.local
set -eu
host="${1:-localhost}"
dir="$(dirname "$0")"
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout "$dir/privkey.pem" -out "$dir/fullchain.pem" \
  -subj "/CN=$host" -addext "subjectAltName=DNS:$host,DNS:localhost,IP:127.0.0.1"
echo "Certificat créé pour $host dans $dir"
