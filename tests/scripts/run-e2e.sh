#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=tests/scripts/lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

load_test_env
require_command npm "Install Node.js and npm."

print_section "Running Playwright web E2E"
cd "${TESTS_DIR}/e2e/playwright"

if [[ ! -d node_modules ]]; then
  echo "Installing Playwright test dependencies in tests/e2e/playwright ..."
  npm install
fi

npx playwright test "$@"
