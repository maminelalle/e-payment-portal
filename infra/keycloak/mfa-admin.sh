#!/usr/bin/env bash
# Active ou désactive l'OTP SMS des administrateurs sur le Keycloak LOCAL (développement uniquement).
#   bash infra/keycloak/mfa-admin.sh off   # connexion admin par mot de passe seul
#   bash infra/keycloak/mfa-admin.sh on    # OTP SMS exigé pour le rôle ADMIN (comportement du realm livré)
# Le fichier epayment-realm.json n'est pas modifié : un nouvel import réactive toujours la MFA.
set -euo pipefail

MODE=${1:-}
KC=${KEYCLOAK_URL:-http://localhost:8180}
API=$KC/admin/realms/epayment

case "$MODE" in
  on) FLOW=browser-mfa-admin ;;
  off) FLOW=browser ;;
  *) echo "Usage : $0 on|off" >&2; exit 1 ;;
esac

TOKEN=$(curl -sf -d client_id=admin-cli -d "username=${KEYCLOAK_ADMIN:-admin}" -d "password=${KEYCLOAK_ADMIN_PASSWORD:-admin}" \
  -d grant_type=password "$KC/realms/master/protocol/openid-connect/token" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
AUTH="Authorization: Bearer $TOKEN"

# Flux de connexion du navigateur
curl -sf -X PUT -H "$AUTH" -H 'Content-Type: application/json' -d "{\"browserFlow\":\"$FLOW\"}" "$API"
echo "Flux de connexion : $FLOW"

if [ "$MODE" = off ]; then
  # Sans cela, un admin déjà enrôlé (ou en cours d'enrôlement) resterait bloqué sur l'écran TOTP.
  for id in $(curl -sf -H "$AUTH" "$API/roles/ADMIN/users" | grep -o '"id":"[^"]*"' | cut -d'"' -f4); do
    user=$(curl -sf -H "$AUTH" "$API/users/$id")
    name=$(printf '%s' "$user" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)
    curl -sf -X PUT -H "$AUTH" -H 'Content-Type: application/json' -d '{"requiredActions":[]}' "$API/users/$id"
    for cred in $(curl -sf -H "$AUTH" "$API/users/$id/credentials" | grep -o '"id":"[^"]*","type":"otp"' | cut -d'"' -f4); do
      curl -sf -X DELETE -H "$AUTH" "$API/users/$id/credentials/$cred"
    done
    echo "  $name : anciennes informations MFA retirées"
  done
  echo "MFA admin DÉSACTIVÉE en local. Pour la réactiver : bash $0 on"
else
  echo "MFA admin ACTIVÉE : l'OTP SMS Vonage sera demandé au rôle ADMIN."
fi
