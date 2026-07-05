#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env

if [[ $# -lt 1 ]]; then
  echo "Usage: bash tests/scripts/replay-interview.sh <trace.jsonl> [extra replay args...]" >&2
  exit 2
fi

cd "${ROOT_DIR}"
exec python3 "${TESTS_DIR}/scripts/replay-interview.py" "$@"
