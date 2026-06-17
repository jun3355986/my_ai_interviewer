#!/usr/bin/env bash

set -euo pipefail

USER_WEB_BASE_URL="${USER_WEB_BASE_URL:-http://localhost:8088}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Missing command: docker" >&2
  exit 127
fi

docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t "${USER_WEB_BASE_URL}" -r zap-user-web.html
