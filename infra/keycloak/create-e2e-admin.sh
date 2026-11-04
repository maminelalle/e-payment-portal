#!/bin/sh
# RECETTE uniquement : crée (ou réinitialise) un administrateur de test pour Playwright.
# La MFA reste active : à la première connexion, le test enrôle lui-même le TOTP.
#   sh infra/keycloak/create-e2e-admin.sh e2e-admin@epayment.mr 'Mot#DePasse2026'
# Nécessite la pile de production démarrée (conteneur epayment-prod-keycloak-1) et infra/.env.prod.
set -eu
email="${1:?email}"
password="${2:?mot de passe (politique : 12 caractères, majuscule, minuscule, chiffre, caractère spécial)}"
dir="$(cd "$(dirname "$0")/.." && pwd)"
admin_password=$(grep '^KEYCLOAK_ADMIN_PASSWORD=' "$dir/.env.prod" | cut -d= -f2-)
admin_user=$(grep '^KEYCLOAK_ADMIN=' "$dir/.env.prod" | cut -d= -f2-)

kc() { MSYS_NO_PATHCONV=1 docker exec epayment-prod-keycloak-1 /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080/auth --realm master --user "$admin_user" --password "$admin_password" >/dev/null

id=$(kc get users -r epayment -q "email=$email" -q exact=true --fields id --format csv --noquotes | head -1)
if [ -z "$id" ]; then
  kc create users -r epayment -s "username=$email" -s "email=$email" -s emailVerified=true -s enabled=true \
    -s firstName=E2E -s lastName=Admin >/dev/null
  id=$(kc get users -r epayment -q "email=$email" -q exact=true --fields id --format csv --noquotes | head -1)
fi
kc add-roles -r epayment --uid "$id" --rolename ADMIN
# Repart d'un état sans TOTP : le prochain test refait l'enrôlement.
for cred in $(kc get "users/$id/credentials" -r epayment --fields id,type --format csv --noquotes | grep ',otp$' | cut -d, -f1); do
  kc delete "users/$id/credentials/$cred" -r epayment
done
# Mot de passe identique au précédent : refusé par l'historique du realm, sans conséquence.
kc set-password -r epayment --userid "$id" --new-password "$password" 2>/dev/null || echo "Mot de passe inchangé."
echo "Administrateur de test prêt : $email"
