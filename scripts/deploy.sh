#!/bin/bash
# Builds the frontend and the API image and ships them to the VPS.
# Run by the CD workflow (.github/workflows/deploy.yml) on every push to main.
# Locally: create .env at the repo root (see core/.env.example) and run
#   VPS_IP=31.97.169.38 VITE_API_URL=https://futspring.luisgosampaio.com/api ./scripts/deploy.sh
set -euo pipefail

PROJECT="futspring"
IMAGE="futspring-api"
SERVICE="futspring-api"

VPS_IP="${VPS_IP:?VPS_IP is required}"
VPS_USER="${VPS_USER:-root}"
VITE_API_URL="${VITE_API_URL:?VITE_API_URL is required (e.g. https://futspring.luisgosampaio.com/api)}"
WEB_ROOT="${WEB_ROOT:-/var/www/futspring}"

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
CLIENT_DIR="${BASE_DIR}/client"
BACKEND_DIR="${BASE_DIR}/core"
ENV_FILE="${ENV_FILE:-${BASE_DIR}/.env}"
# Expanded by the remote login shell in SSH/scp paths.
# shellcheck disable=SC2088
REMOTE_DIR="~/projects/${PROJECT}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Env file not found: ${ENV_FILE}"
  echo "   Local: create it from core/.env.example with the production values."
  echo "   CI:    the CD workflow writes it from the GitHub secrets."
  exit 1
fi

if grep -q '^DDL_AUTO=' "${ENV_FILE}"; then
  echo "DDL_AUTO must not be set in production: the schema is owned by Flyway (ddl-auto=validate)."
  exit 1
fi

VERSION=$(date +%Y%m%d-%H%M%S)
ARCHIVE="${IMAGE}-${VERSION}.tar.gz"

echo "Deploying Futspring ${VERSION}..."

# ====================
# BUILD
# ====================
echo "Building frontend (VITE_API_URL=${VITE_API_URL})..."
cd "${CLIENT_DIR}"
bun install --frozen-lockfile
VITE_API_URL="${VITE_API_URL}" bun run build

echo "Building Docker image (linux/amd64)..."
cd "${BACKEND_DIR}"
docker build --platform linux/amd64 -t "${IMAGE}:${VERSION}" -t "${IMAGE}:latest" .

echo "Saving Docker image..."
cd "${BASE_DIR}"
docker save "${IMAGE}:${VERSION}" "${IMAGE}:latest" | gzip > "${ARCHIVE}"
trap 'rm -f "${BASE_DIR}/${ARCHIVE}"' EXIT
echo "Image size: $(du -h "${ARCHIVE}" | cut -f1)"

# ====================
# UPLOAD
# ====================
echo "Uploading to VPS..."
# Trusted deployment paths are expanded locally; the remote shell expands ~.
# shellcheck disable=SC2029
ssh "${VPS_USER}@${VPS_IP}" "mkdir -p ${REMOTE_DIR}/backups ${WEB_ROOT}"

# Host credentials and billing/activation evidence are provisioned separately.
# Failure (including missing tooling/config) blocks ALL publication and Flyway startup.
# Do not replace this with an optional local dump. Existing backups/ are preserved.
COMMIT=$(git -C "${BASE_DIR}" rev-parse HEAD)
[[ "${COMMIT}" =~ ^[a-f0-9]{40}$ ]] || { echo "Invalid commit"; exit 1; }
echo "Requiring a verified offsite backup before publication..."
# COMMIT is validated above; this expansion intentionally happens locally.
# shellcheck disable=SC2029
ssh "${VPS_USER}@${VPS_IP}" "python3 /opt/futspring-backup/backup.py pre-deploy --commit ${COMMIT}"

# Old hashed assets are kept (no --delete) so users with a stale index.html
# can still load the lazy page chunks they reference.
rsync -az "${CLIENT_DIR}/dist/assets/" "${VPS_USER}@${VPS_IP}:${WEB_ROOT}/assets/"
rsync -az --exclude assets "${CLIENT_DIR}/dist/" "${VPS_USER}@${VPS_IP}:${WEB_ROOT}/"

scp "${ARCHIVE}" "${VPS_USER}@${VPS_IP}:${REMOTE_DIR}/"
scp "${BACKEND_DIR}/docker-compose.prod.yml" "${VPS_USER}@${VPS_IP}:${REMOTE_DIR}/"
scp "${ENV_FILE}" "${VPS_USER}@${VPS_IP}:${REMOTE_DIR}/.env"

# ====================
# DEPLOY ON VPS
# ====================
echo "Deploying on VPS..."
# VERSION/IMAGE/paths expand locally; remote variables below are explicitly escaped.
# shellcheck disable=SC2087
ssh "${VPS_USER}@${VPS_IP}" bash << ENDSSH
set -euo pipefail
cd ${REMOTE_DIR}
chmod 600 .env

echo "Loading Docker image ${VERSION}..."
docker load < ${ARCHIVE}

# Shared with the observability stack (ops/observability), which scrapes the API on it
docker network create observability >/dev/null 2>&1 || true

# The image runs as uid 10001; idempotent
mkdir -p /opt/futspring-uploads /opt/futspring-finance-receipts /opt/futspring-finance-qr
chown -R 10001:10001 /opt/futspring-uploads /opt/futspring-finance-receipts /opt/futspring-finance-qr

echo "Starting containers..."
docker compose -f docker-compose.prod.yml up -d --no-build --remove-orphans

# Waits up to 2 minutes for a container's healthcheck; prints its logs and fails otherwise
wait_healthy() {
  local container="\$1" service="\$2" health=""
  echo "Waiting for \${container} to be healthy..."
  for attempt in \$(seq 1 24); do
    health=\$(docker inspect -f '{{.State.Health.Status}}' "\${container}" 2>/dev/null || true)
    if [[ "\${health}" == "healthy" ]]; then
      echo "\${container} is healthy (attempt \${attempt})."
      return 0
    fi
    sleep 5
  done
  echo "\${container} was not healthy after 2 minutes (last status: \${health})."
  docker compose -f docker-compose.prod.yml ps
  docker compose -f docker-compose.prod.yml logs --tail 100 "\${service}"
  exit 1
}

# Redis (refresh-token sessions, AOF on): its healthcheck is an authenticated PING
wait_healthy futspring_redis futspring-redis
# The image's HEALTHCHECK probes readiness on the internal management port: healthy means Flyway migrated
# and Hibernate validated the schema. Redis is not part of readiness
wait_healthy futspring_api ${SERVICE}

docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail 30 ${SERVICE}

rm -f ${ARCHIVE}

# Keep only the last 3 versions
docker images ${IMAGE} --format "{{.Tag}}" | grep -v latest | sort -r | tail -n +4 | xargs -r -I {} docker rmi ${IMAGE}:{} 2>/dev/null || true
docker image prune -f

echo "${VERSION} deployed."
ENDSSH

echo "Deploy finished: ${VERSION}"
