#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TESTS_DIR="${ROOT_DIR}/tests"
LOCAL_ENV="${TESTS_DIR}/config/local.env"

load_test_env() {
  if [[ -f "${LOCAL_ENV}" ]]; then
    # shellcheck disable=SC1090
    set -a
    source "${LOCAL_ENV}"
    set +a
  fi

  export GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:9000}"
  export USER_WEB_BASE_URL="${USER_WEB_BASE_URL:-http://localhost:8088}"
  export ADMIN_WEB_BASE_URL="${ADMIN_WEB_BASE_URL:-http://localhost:8090}"
  export PYTHON_AI_BASE_URL="${PYTHON_AI_BASE_URL:-http://localhost:8000}"
  export SMOKE_USERNAME="${SMOKE_USERNAME:-admin}"
  export SMOKE_PASSWORD="${SMOKE_PASSWORD:-admin123}"
  export CURL_MAX_TIME="${CURL_MAX_TIME:-45}"
  export SSE_MAX_TIME="${SSE_MAX_TIME:-45}"
}

require_command() {
  local name="$1"
  local install_hint="$2"

  if ! command -v "${name}" >/dev/null 2>&1; then
    echo "Missing command: ${name}" >&2
    echo "Install hint: ${install_hint}" >&2
    return 127
  fi
}

print_section() {
  echo
  echo "==> $*"
}
