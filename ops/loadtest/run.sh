#!/usr/bin/env bash
# Local load test of the API image, with the production limits. Never run it against production: the VPS has
# 2 vCPU shared by every project.
#
#   ops/loadtest/run.sh pr1                      # builds core/, runs 50 / 200 / 500 connections for 60 s each
#   SKIP_BUILD=1 IMAGE=futspring-api:x ops/loadtest/run.sh pr2
#   JFR=1 ops/loadtest/run.sh pr3                # also records JFR and summarizes jdk.VirtualThreadPinned
#
# Needs Docker, oha (brew install oha), python3, and for JFR=1 a local JDK 21+ (jfr on the PATH or in JAVA_HOME).
# Writes ops/loadtest/results/<label>-<date>.md.
set -euo pipefail

LABEL="${1:?usage: run.sh <label>, e.g. pr1}"
CONCURRENCY="${CONCURRENCY:-50 200 500}"
DURATION="${DURATION:-60s}"
IMAGE="${IMAGE:-futspring-api:loadtest}"
PORT="${PORT:-18081}"
JFR="${JFR:-0}"

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${BASE_DIR}/ops/loadtest/results"
RESULT="${OUT_DIR}/${LABEL}-$(date +%Y-%m-%d).md"
WORK="$(mktemp -d)"
NET="futspring-loadtest"
PG="futspring-loadtest-pg"
API="futspring-loadtest-api"
API_URL="http://127.0.0.1:${PORT}"
SEED_EMAIL="leal@futspring.com"   # DataInitializer (dev profile)
SEED_PASSWORD="senha123"

command -v oha >/dev/null || { echo "oha not found: brew install oha"; exit 1; }

cleanup() {
  docker rm -f "${API}" "${PG}" >/dev/null 2>&1 || true
  docker network rm "${NET}" >/dev/null 2>&1 || true
  rm -rf "${WORK}"
}
trap cleanup EXIT
cleanup
mkdir -p "${WORK}" "${OUT_DIR}"

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  echo "Building ${IMAGE}..."
  docker build -q -t "${IMAGE}" "${BASE_DIR}/core" >/dev/null
fi

echo "Starting Postgres 17 and the API (--cpus=1 --memory=1g, dev seed)..."
docker network create "${NET}" >/dev/null
docker run -d --name "${PG}" --network "${NET}" \
  -e POSTGRES_DB=futspringdb -e POSTGRES_USER=futspring -e POSTGRES_PASSWORD=futspring postgres:17-alpine >/dev/null
until docker exec "${PG}" pg_isready -U futspring -d futspringdb >/dev/null 2>&1; do sleep 1; done

# Same JVM options as the image, plus the flight recording when asked
JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:AOTCache=/app/app.aot"
if [[ "${JFR}" == "1" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -XX:StartFlightRecording=filename=/tmp/loadtest.jfr,settings=profile,dumponexit=true"
fi
docker run -d --name "${API}" --network "${NET}" --cpus=1 --memory=1g -p "127.0.0.1:${PORT}:8080" \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DB_URL="jdbc:postgresql://${PG}:5432/futspringdb" -e DB_USERNAME=futspring -e DB_PASSWORD=futspring \
  -e JWT_SECRET="loadtest-secret-0123456789-0123456789-0123456789" \
  -e JAVA_TOOL_OPTIONS="${JAVA_OPTS}" \
  "${IMAGE}" >/dev/null

for _ in $(seq 1 60); do
  [[ "$(curl -s -o /dev/null -w '%{http_code}' "${API_URL}/readyz" || true)" == "200" ]] && break
  sleep 2
done
[[ "$(curl -s -o /dev/null -w '%{http_code}' "${API_URL}/readyz" || true)" == "200" ]] \
  || { echo "API not ready"; docker logs --tail 80 "${API}"; exit 1; }
STARTUP=$(docker logs "${API}" 2>&1 | grep -oE "Started FutSpringApplication in [0-9.]+ seconds" | grep -oE "[0-9.]+" || echo "?")

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1"; }

TOKEN=$(curl -sf -X POST "${API_URL}/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${SEED_EMAIL}\",\"password\":\"${SEED_PASSWORD}\"}" | json "d['token']")
AUTH="Authorization: Bearer ${TOKEN}"
PELADA_ID=$(curl -sf -H "${AUTH}" "${API_URL}/api/v1/peladas/my" | json "d[0]['id']")
DAILY_ID=$(curl -sf -H "${AUTH}" "${API_URL}/api/v1/peladas/${PELADA_ID}/dailies" \
  | json "next(x['id'] for x in d if x['status'] == 'FINISHED')")

# The fixed mix: oha picks one of these at random for every request
cat > "${WORK}/urls.txt" << EOF
${API_URL}/api/v1/peladas/my
${API_URL}/api/v1/peladas/${PELADA_ID}
${API_URL}/api/v1/dailies/${DAILY_ID}
${API_URL}/api/v1/peladas/${PELADA_ID}/ranking
${API_URL}/api/v1/peladas/${PELADA_ID}/attendance
EOF

threads() {
  docker exec "${API}" curl -s localhost:9091/actuator/metrics/jvm.threads.live \
    | python3 -c "import sys,json; print(int(json.load(sys.stdin)['measurements'][0]['value']))" 2>/dev/null || echo "?"
}

echo "Warming up (15 s)..."
oha --no-tui -z 15s -c 20 -H "${AUTH}" --urls-from-file "${WORK}/urls.txt" >/dev/null

ROWS=""
for c in ${CONCURRENCY}; do
  echo "Running ${c} connections for ${DURATION}..."
  # sample the live platform threads while the level runs
  ( for _ in $(seq 1 6); do sleep 8; threads; done > "${WORK}/threads-${c}.txt" ) &
  sampler=$!
  oha --no-tui --output-format json -z "${DURATION}" -c "${c}" -H "${AUTH}" \
    --urls-from-file "${WORK}/urls.txt" > "${WORK}/oha-${c}.json"
  wait "${sampler}" || true
  peak=$(grep -E '^[0-9]+$' "${WORK}/threads-${c}.txt" | sort -n | tail -1 || true)
  ROWS+=$(python3 - "${WORK}/oha-${c}.json" "${c}" "${peak:-?}" << 'PY'
import json, sys
d = json.load(open(sys.argv[1]))
s, p = d["summary"], d.get("latencyPercentiles", {})
codes = d.get("statusCodeDistribution", {})
total = sum(codes.values())
non2xx = sum(v for k, v in codes.items() if not k.startswith("2"))
# requests still in flight when the duration ends are aborted by oha, not failed by the API
errors = sum(v for k, v in d.get("errorDistribution", {}).items() if "deadline" not in k)
ms = lambda v: f"{v * 1000:.0f}" if v is not None else "?"
print(f"| {sys.argv[2]} | {s['requestsPerSec']:.0f} | {ms(p.get('p50'))} | {ms(p.get('p95'))} | {ms(p.get('p99'))} "
      f"| {total} | {non2xx} | {errors} | {sys.argv[3]} |")
PY
)$'\n'
done

PINNED="not recorded (run with JFR=1)"
if [[ "${JFR}" == "1" ]]; then
  docker stop -t 30 "${API}" >/dev/null
  docker cp "${API}:/tmp/loadtest.jfr" "${WORK}/loadtest.jfr"
  JFR_BIN="$(command -v jfr || echo "${JAVA_HOME:-}/bin/jfr")"
  PINNED=$("${JFR_BIN}" print --json --events jdk.VirtualThreadPinned "${WORK}/loadtest.jfr" | python3 -c "
import sys, json, re
events = json.load(sys.stdin)['recording']['events']
def ms(e):
    m = re.match(r'PT([0-9.]+)S', e['values']['duration'])
    return float(m.group(1)) * 1000 if m else 0.0
long_ = [ms(e) for e in events if ms(e) > 20]
print(f'{len(events)} events, {len(long_)} longer than 20 ms' + (f' (max {max(long_):.0f} ms)' if long_ else ''))
")
fi

{
  echo "# Load test: ${LABEL} ($(date +%Y-%m-%d))"
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| Commit | \`$(git -C "${BASE_DIR}" rev-parse --short HEAD)\`$(git -C "${BASE_DIR}" diff --quiet || echo ' (dirty)') |"
  echo "| Image | \`${IMAGE}\` |"
  echo "| Limits | \`--cpus=1 --memory=1g\`, Postgres 17 with the dev seed, same Docker host |"
  echo "| Mix | random among \`GET /peladas/my\`, \`/peladas/{id}\`, \`/dailies/{id}\`, \`/peladas/{id}/ranking\`, \`/peladas/{id}/attendance\` |"
  echo "| Duration | ${DURATION} per level, after a 15 s warm-up at 20 connections |"
  echo "| Startup | ${STARTUP} s |"
  echo "| JFR pinning | ${PINNED} |"
  echo
  echo "| Connections | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Requests | Non-2xx | Client errors | Live threads (peak) |"
  echo "|---|---|---|---|---|---|---|---|---|"
  printf '%s' "${ROWS}"
} > "${RESULT}"

cat "${RESULT}"
echo
echo "Saved to ${RESULT}"
