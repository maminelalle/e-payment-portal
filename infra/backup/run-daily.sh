#!/bin/sh
# Lance backup.sh chaque jour à BACKUP_TIME (heure de Nouakchott). Un échec est journalisé, la boucle continue.
set -u
: "${BACKUP_TIME:=02:30}"

while true; do
  now=$(date +%s)
  target=$(date -d "$(date +%Y-%m-%d) $BACKUP_TIME" +%s 2>/dev/null || date -D '%Y-%m-%d %H:%M' -d "$(date +%Y-%m-%d) $BACKUP_TIME" +%s)
  [ "$target" -le "$now" ] && target=$((target + 86400))
  echo "Prochaine sauvegarde dans $(( (target - now) / 60 )) minutes ($BACKUP_TIME)."
  sleep $((target - now))
  backup.sh || echo "[$(date)] ÉCHEC de la sauvegarde" >&2
done
