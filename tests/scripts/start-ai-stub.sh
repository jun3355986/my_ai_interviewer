#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env

STUB_HOST="${AI_STUB_HOST:-127.0.0.1}"
STUB_PORT="${AI_STUB_PORT:-18000}"
STUB_DIR="${ROOT_DIR}/tests/stubs/python-ai"

echo "Starting Python AI SSE stub at http://${STUB_HOST}:${STUB_PORT}"
echo "Point Java interview service to: python.ai.base-url=http://${STUB_HOST}:${STUB_PORT}"

cd "${ROOT_DIR}/ai_interviewer"
exec uv run uvicorn app:app \
  --app-dir "${STUB_DIR}" \
  --host "${STUB_HOST}" \
  --port "${STUB_PORT}"
