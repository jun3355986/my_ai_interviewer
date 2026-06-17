#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env
require_command python3 "Install Python 3, then install pytest in your active environment."

if ! python3 -c "import pytest" >/dev/null 2>&1; then
  echo "Missing Python package: pytest" >&2
  echo "Install hint: python3 -m pip install pytest" >&2
  exit 127
fi

export RUN_LIVE_API_TESTS="${RUN_LIVE_API_TESTS:-1}"

print_section "Running pytest API tests"
cd "${ROOT_DIR}"
python3 -m pytest tests/api/pytest "$@"
