#!/usr/bin/env bash

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

failures=0

run_step() {
  local name="$1"
  shift

  echo
  echo "==> ${name}"
  if "$@"; then
    echo "PASS: ${name}"
  else
    local code=$?
    echo "FAIL: ${name} (exit ${code})"
    failures=$((failures + 1))
  fi
}

run_step "Smoke" bash "${SCRIPT_DIR}/run-smoke.sh"
run_step "API" bash "${SCRIPT_DIR}/run-api.sh"
run_step "E2E" bash "${SCRIPT_DIR}/run-e2e.sh"
run_step "Performance" bash "${SCRIPT_DIR}/run-performance.sh"
run_step "Security" bash "${SCRIPT_DIR}/run-security.sh"
run_step "Collect report" bash "${SCRIPT_DIR}/collect-report.sh"

if [[ "${failures}" -gt 0 ]]; then
  echo
  echo "Test automation finished with ${failures} failed group(s)."
  exit 1
fi

echo
echo "Test automation finished successfully."
