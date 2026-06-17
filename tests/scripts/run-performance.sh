#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env
require_command k6 "Install k6 from https://grafana.com/docs/k6/latest/set-up/install-k6/."

print_section "Running k6 gateway smoke"
cd "${ROOT_DIR}"
k6 run tests/performance/k6/load-gateway.js

print_section "Running k6 job-search smoke"
k6 run tests/performance/k6/load-job-search.js

if [[ "${K6_RUN_SSE:-0}" == "1" ]]; then
  print_section "Running k6 interview SSE smoke"
  k6 run tests/performance/k6/smoke-interview-sse.js
else
  echo "Skipped k6 interview SSE smoke. Set K6_RUN_SSE=1 to run it."
fi
