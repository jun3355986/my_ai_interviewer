#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env

print_section "Running environment checks"
bash "${TESTS_DIR}/smoke/env_check.sh"

print_section "Running P0/P1/P2 smoke"
cd "${ROOT_DIR}"
bash "${TESTS_DIR}/smoke/p012_smoke.sh" "$@"

RESULTS_FILE="${ROOT_DIR}/.sisyphus/evidence/task-8-p012-results.json"
python3 - "${RESULTS_FILE}" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
if not path.exists():
    print(f"Missing smoke result file: {path}", file=sys.stderr)
    raise SystemExit(1)

data = json.loads(path.read_text(encoding="utf-8"))
p0 = data.get("p0", {})
overall = data.get("overall", {})
status = overall.get("status", "UNKNOWN")
weighted = overall.get("weightedScore", "UNKNOWN")

print(f"Smoke gate: P0 {p0.get('passed')}/{p0.get('total')}, weighted={weighted}, status={status}")

if not p0.get("allPassed") or status != "GREEN":
    print("Smoke gate failed. See .sisyphus/evidence/task-8-p0-smoke-report.md", file=sys.stderr)
    raise SystemExit(1)
PY
