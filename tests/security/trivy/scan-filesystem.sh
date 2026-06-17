#!/usr/bin/env bash

set -euo pipefail

if ! command -v trivy >/dev/null 2>&1; then
  echo "Missing command: trivy" >&2
  echo "Install hint: https://trivy.dev/latest/getting-started/installation/" >&2
  exit 127
fi

trivy fs \
  --scanners vuln,secret,misconfig \
  --severity HIGH,CRITICAL \
  --ignore-unfixed \
  --exit-code 1 \
  .
