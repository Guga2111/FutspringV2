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
ssh "${VPS_USER}@${VPS_IP}" "mkdir -p ${REMOTE_DIR}/backups ${WEB_ROOT}"

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
ssh "${VPS_USER}@${VPS_IP}" bash << ENDSSH
set -euo pipefail
cd ${REMOTE_DIR}
chmod 600 .env

API_PORT=\$(grep '^API_PORT=' .env | cut -d= -f2- || true)
API_PORT=\${API_PORT:-8081}
PG_DUMP_URL=\$(grep '^PG_DUMP_URL=' .env | cut -d= -f2- || true)

echo "Loading Docker image ${VERSION}..."
docker load < ${ARCHIVE}

# Flyway migrates the database when the new container starts, so back it up first
# (migrations can be destructive, e.g. V2). Needs the Supabase session pooler (port 5432).
if [[ -n "\${PG_DUMP_URL}" ]]; then
  echo "Backing up the database..."
  docker run --rm postgres:17-alpine pg_dump "\${PG_DUMP_URL}" --schema=public --no-owner \
    | gzip > backups/${PROJECT}-${VERSION}.sql.gz
  if [[ \$(gzip -dc backups/${PROJECT}-${VERSION}.sql.gz | head -c 1 | wc -c) -eq 0 ]]; then
    echo "Backup is empty, aborting."
    rm -f backups/${PROJECT}-${VERSION}.sql.gz
    exit 1
  fi
  echo "Backup: backups/${PROJECT}-${VERSION}.sql.gz (\$(du -h backups/${PROJECT}-${VERSION}.sql.gz | cut -f1))"
  ls -1t backups/${PROJECT}-*.sql.gz | tail -n +11 | xargs -r rm -f
else
  echo "PG_DUMP_URL not set: skipping the database backup."
fi

# The old deploy ran a plain 'docker run' container that holds the API port
docker rm -f futspring-api 2>/dev/null || true

echo "Starting containers..."
docker compose -f docker-compose.prod.yml up -d --no-build --remove-orphans

# No health endpoint: an unauthenticated request to a protected route answers 401
# once the app is up, i.e. after Flyway migrated and Hibernate validated the schema
echo "Waiting for the API on port \${API_PORT}..."
status=""
for attempt in \$(seq 1 24); do
  status=\$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:\${API_PORT}/api/v1/peladas/my" || true)
  if [[ "\${status}" == "401" ]]; then
    echo "API is up (attempt \${attempt})."
    break
  fi
  sleep 5
done

if [[ "\${status}" != "401" ]]; then
  echo "API did not start after 2 minutes (last status: \${status})."
  docker compose -f docker-compose.prod.yml ps
  docker compose -f docker-compose.prod.yml logs --tail 100 ${SERVICE}
  exit 1
fi

docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail 30 ${SERVICE}

rm -f ${ARCHIVE}

# Keep only the last 3 versions
docker images ${IMAGE} --format "{{.Tag}}" | grep -v latest | sort -r | tail -n +4 | xargs -r -I {} docker rmi ${IMAGE}:{} 2>/dev/null || true
docker image prune -f

echo "${VERSION} deployed."
ENDSSH

echo "Deploy finished: ${VERSION}"
