#!/bin/bash
# Stage/update reviewed tooling ONLY. Does not configure credentials or enable/start a unit.
set -euo pipefail
umask 077
[[ $EUID -eq 0 ]] || { echo 'Execute como root no host Linux.'; exit 1; }
BASE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
command -v python3 >/dev/null
command -v docker >/dev/null
aws --version 2>&1 | grep -q '^aws-cli/2\.'
docker image inspect postgres:17-alpine >/dev/null # pre-stage approved image; never pull during a backup
install -d -m 700 /opt/futspring-backup /etc/futspring-backup /etc/futspring-backup/postgres /var/lib/futspring-backup
install -d -m 755 /var/lib/node_exporter/textfile_collector
for file in common.py backup.py restore-drill.py source-watch.sh inspect.sql preflight.sql sequences.sql snapshot.sql; do
  install -m 700 "$BASE/$file" "/opt/futspring-backup/$file"
done
install -m 644 "$BASE"/systemd/futspring-backup.{service,timer} /etc/systemd/system/
systemctl daemon-reload
echo 'Ferramentas instaladas sem ativação. Consulte o runbook e o bloqueio financeiro.'
