#!/usr/bin/env bash

set -euo pipefail

if ! command -v trivy >/dev/null 2>&1; then
  echo "Missing command: trivy" >&2
  echo "Install hint: https://trivy.dev/latest/getting-started/installation/" >&2
  exit 127
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Missing command: docker" >&2
  exit 127
fi

images="$(docker compose -f ai_interview_backend/docker-compose.yml images -q | sort -u)"
if [[ -z "${images}" ]]; then
  echo "No docker compose images found. Build the stack first:"
  echo "cd ai_interview_backend && docker compose build"
  exit 1
fi

while IFS= read -r image; do
  [[ -z "${image}" ]] && continue
  echo "Scanning image ${image}"
  trivy image \
    --scanners vuln,secret,misconfig \
    --severity HIGH,CRITICAL \
    --ignore-unfixed \
    --exit-code 1 \
    "${image}"
done <<< "${images}"
