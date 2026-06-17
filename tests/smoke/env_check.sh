#!/usr/bin/env bash

set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:9000}"
USER_WEB_BASE_URL="${USER_WEB_BASE_URL:-http://localhost:8088}"
ADMIN_WEB_BASE_URL="${ADMIN_WEB_BASE_URL:-http://localhost:8090}"
PYTHON_AI_BASE_URL="${PYTHON_AI_BASE_URL:-http://localhost:8000}"
CURL_MAX_TIME="${CURL_MAX_TIME:-10}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

require_curl() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "Missing command: curl" >&2
    exit 127
  fi
}

check_http() {
  local name="$1"
  local url="$2"
  local expected="${3:-200}"
  local body_file
  body_file="$(mktemp)"

  local code
  code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${body_file}" -w "%{http_code}" "${url}" || true)"
  if [[ "${code}" != "${expected}" ]]; then
    echo "FAIL ${name}: expected HTTP ${expected}, got ${code}, url=${url}" >&2
    cat "${body_file}" >&2 || true
    rm -f "${body_file}"
    exit 1
  fi
  rm -f "${body_file}"
  echo "PASS ${name}: HTTP ${code}"
}

require_curl

check_http "gateway health" "${GATEWAY_BASE_URL}/actuator/health" "200"
check_http "python ai health" "${PYTHON_AI_BASE_URL}/health" "200"
check_http "user web" "${USER_WEB_BASE_URL}" "200"
check_http "admin web" "${ADMIN_WEB_BASE_URL}" "200"

if command -v docker >/dev/null 2>&1; then
  echo
  echo "Docker compose service snapshot:"
  docker compose -f "${ROOT_DIR}/ai_interview_backend/docker-compose.yml" ps || true
fi
