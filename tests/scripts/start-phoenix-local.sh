#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python_project="$repo_root/ai_interviewer"
local_env="$repo_root/tests/config/phoenix.local.env"

if [[ -f "$local_env" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$local_env"
  set +a
fi

# The lab is local-only. Disable Phoenix's optional product analytics unless a
# user deliberately overrides it in their ignored local environment file.
export PHOENIX_TELEMETRY_ENABLED="${PHOENIX_TELEMETRY_ENABLED:-false}"
export PHOENIX_AGENTS_DISABLE_WEB_ACCESS="${PHOENIX_AGENTS_DISABLE_WEB_ACCESS:-true}"
export PHOENIX_AGENTS_DISABLE_BASH="${PHOENIX_AGENTS_DISABLE_BASH:-true}"
export PHOENIX_ALLOWED_PROVIDERS="${PHOENIX_ALLOWED_PROVIDERS:-NONE}"

if [[ ! -x "$python_project/.venv/bin/phoenix" ]]; then
  echo "Phoenix is not installed in $python_project/.venv. Run: cd ai_interviewer && uv sync --group dev" >&2
  exit 1
fi

if lsof -nP -iTCP:6006 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Phoenix already appears to be listening on http://127.0.0.1:6006" >&2
  exit 1
fi

echo "Starting local Phoenix at http://127.0.0.1:6006"
echo "Keep this terminal open. Stop it with Ctrl+C when the lab is finished."
exec "$python_project/.venv/bin/phoenix" serve
