#!/usr/bin/env bash
# Runs the visual tests in the official Playwright image (same browser build and fonts as CI),
# so the snapshots don't depend on the host OS. Extra arguments go to `playwright test`
# (e.g. --update-snapshots). PLATFORM=linux/amd64 emulates the CI architecture on Apple silicon.
# node_modules lives in a named volume per platform (Linux binaries).
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=$(node -p "require('@playwright/test/package.json').version")
PLATFORM="${PLATFORM:-}"
VOLUME="futspring-visual-node-modules-$(echo "${PLATFORM:-native}" | tr '/' '-')"
docker run --rm --ipc=host ${PLATFORM:+--platform "$PLATFORM"} \
  -v "$PWD":/work -v "$VOLUME":/work/node_modules -w /work \
  -e CI="${CI:-}" \
  "mcr.microsoft.com/playwright:v${VERSION}-noble" \
  bash -c "npm install -g bun@1 >/dev/null 2>&1 && bun install --frozen-lockfile && bunx playwright test $*"
