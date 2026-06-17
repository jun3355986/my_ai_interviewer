#!/usr/bin/env bash

set -euo pipefail

ADMIN_WEB_BASE_URL="${ADMIN_WEB_BASE_URL:-http://localhost:8090}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Missing command: docker" >&2
  exit 127
fi

docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t "${ADMIN_WEB_BASE_URL}" -r zap-admin-web.html
