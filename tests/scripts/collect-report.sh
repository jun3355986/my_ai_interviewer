#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env

print_section "Collecting report artifacts"
mkdir -p "${TESTS_DIR}/reports/allure-results" \
  "${TESTS_DIR}/reports/allure-report" \
  "${TESTS_DIR}/reports/playwright" \
  "${TESTS_DIR}/reports/k6" \
  "${TESTS_DIR}/reports/smoke"

if [[ -d "${ROOT_DIR}/.sisyphus/evidence" ]]; then
  find "${ROOT_DIR}/.sisyphus/evidence" -maxdepth 1 -type f -name "task-8-*" -exec cp {} "${TESTS_DIR}/reports/smoke/" \;
fi

if command -v allure >/dev/null 2>&1 && [[ -d "${TESTS_DIR}/reports/allure-results" ]]; then
  allure generate "${TESTS_DIR}/reports/allure-results" -o "${TESTS_DIR}/reports/allure-report" --clean || true
else
  echo "Allure CLI not installed; skipped HTML report generation."
fi

echo "Reports directory: ${TESTS_DIR}/reports"
