#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env

print_section "Running Trivy filesystem scan"
bash "${TESTS_DIR}/security/trivy/scan-filesystem.sh"

print_section "Running prompt-injection tests"
cd "${ROOT_DIR}"
python3 "${TESTS_DIR}/security/prompt-injection/run_prompt_security_tests.py"
