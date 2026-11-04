#!/bin/sh
# Scan OWASP ZAP « baseline » (passif, sans attaque) de la recette.
#   sh infra/security/zap-baseline.sh https://localhost:8443
# Rapport HTML et JSON dans infra/security/reports/. Alertes acceptées : zap-rules.tsv
# (une ligne par règle : « identifiant<TAB>IGNORE|WARN|FAIL<TAB>justification »).
set -eu
target="${1:-https://localhost:8443}"
dir="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$dir/reports"
# Depuis le conteneur, « localhost » désigne le poste hôte.
inside=$(printf '%s' "$target" | sed 's#//localhost#//host.docker.internal#')
MSYS_NO_PATHCONV=1 docker run --rm -v "$dir:/zap/wrk:rw" --add-host host.docker.internal:host-gateway \
  ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
  -t "$inside" -c zap-rules.tsv -r reports/zap-baseline.html -J reports/zap-baseline.json -I -a
