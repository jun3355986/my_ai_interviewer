#!/usr/bin/env bash

set -u

usage() {
  cat <<'EOF'
Usage: bash tests/smoke/p0_smoke.sh [--dry-run] [--help]

Options:
  --dry-run    Validate flow structure without network calls
  --help       Show this help

Environment:
  GATEWAY_BASE_URL   default http://localhost:9000
  SMOKE_USERNAME     default test_user
  SMOKE_PASSWORD     default Pass123!
  SMOKE_JOB_KEYWORD  default java
  SMOKE_MESSAGE      default I am ready for the interview.
  SMOKE_RESUME_FILE  optional local resume path
  SMOKE_DRY_RUN      set 1/true/yes/on to enable dry-run
  CURL_MAX_TIME      default 45
  SSE_MAX_TIME       default 45
EOF
}

is_true() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

DRY_RUN=false
if is_true "${SMOKE_DRY_RUN:-}"; then
  DRY_RUN=true
fi

for arg in "$@"; do
  case "$arg" in
    --help|-h)
      usage
      exit 0
      ;;
    --dry-run)
      DRY_RUN=true
      ;;
    *)
      echo "[ERROR] Unknown argument: $arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:9000}"
SMOKE_USERNAME="${SMOKE_USERNAME:-test_user}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Pass123!}"
SMOKE_JOB_KEYWORD="${SMOKE_JOB_KEYWORD:-java}"
SMOKE_MESSAGE="${SMOKE_MESSAGE:-I am ready for the interview.}"
CURL_MAX_TIME="${CURL_MAX_TIME:-45}"
SSE_MAX_TIME="${SSE_MAX_TIME:-45}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EVIDENCE_DIR="${ROOT_DIR}/.sisyphus/evidence"
RESULT_JSON="${EVIDENCE_DIR}/task-8-p0-results.json"
REPORT_MD="${EVIDENCE_DIR}/task-8-p0-smoke-report.md"

STEP01_FILE="${EVIDENCE_DIR}/task-8-step-01-health.txt"
STEP02_FILE="${EVIDENCE_DIR}/task-8-step-02-login.json"
STEP03_FILE="${EVIDENCE_DIR}/task-8-step-03-users-me.json"
STEP04_FILE="${EVIDENCE_DIR}/task-8-step-04-jobs.json"
STEP05_FILE="${EVIDENCE_DIR}/task-8-step-05-jobs-search.json"
STEP06_FILE="${EVIDENCE_DIR}/task-8-step-06-resume-upload.json"
STEP07_FILE="${EVIDENCE_DIR}/task-8-step-07-resume-parse.json"
STEP08_FILE="${EVIDENCE_DIR}/task-8-step-08-interview-chat.sse.txt"
STEP09_FILE="${EVIDENCE_DIR}/task-8-step-09-interview-resume.sse.txt"
STEP10_FILE="${EVIDENCE_DIR}/task-8-step-10-observability.txt"

TMP_DIR="$(mktemp -d)"
RESUME_FILE=""
TOKEN=""
RESUME_ID=""
SESSION_ID=""
EXIT_CODE=0

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] Required command not found: $1" >&2
    exit 2
  fi
}

sanitize_text() {
  printf '%s' "$1" | tr '\n\t' '  '
}

mark_step() {
  local step="$1"
  local name="$2"
  local passed="$3"
  local detail="$4"
  local artifact="$5"
  printf '%s' "${name}" > "${TMP_DIR}/step_${step}.name"
  printf '%s' "${passed}" > "${TMP_DIR}/step_${step}.passed"
  printf '%s' "$(sanitize_text "${detail}")" > "${TMP_DIR}/step_${step}.detail"
  printf '%s' "${artifact}" > "${TMP_DIR}/step_${step}.artifact"
}

json_code() {
  python3 - "$1" <<'PY'
import json
import sys
path = sys.argv[1]
with open(path, 'r', encoding='utf-8') as f:
    obj = json.load(f)
code = obj.get('code')
if code is None:
    raise SystemExit(3)
print(code)
PY
}

extract_token() {
  python3 - "$1" <<'PY'
import json
import sys
with open(sys.argv[1], 'r', encoding='utf-8') as f:
    obj = json.load(f)
data = obj.get('data') or {}
token = data.get('accessToken') or ''
print(token)
PY
}

extract_resume_id() {
  python3 - "$1" <<'PY'
import json
import sys
with open(sys.argv[1], 'r', encoding='utf-8') as f:
    obj = json.load(f)
data = obj.get('data') or {}
rid = data.get('id')
if rid is None:
    print('')
else:
    print(rid)
PY
}

extract_job_id() {
  python3 - "$1" <<'PY'
import json
import sys
with open(sys.argv[1], 'r', encoding='utf-8') as f:
    obj = json.load(f)
data = obj.get('data')
job_id = None
if isinstance(data, dict):
    records = data.get('records')
    if isinstance(records, list) and records:
        first = records[0]
        if isinstance(first, dict):
            job_id = first.get('id')
    elif 'id' in data:
        job_id = data.get('id')
elif isinstance(data, list) and data:
    first = data[0]
    if isinstance(first, dict):
        job_id = first.get('id')
print('' if job_id is None else job_id)
PY
}

inspect_sse() {
  python3 - "$1" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8', errors='replace') if path.exists() else ''
has_status = 'event: status' in text
has_chunk = 'event: chunk' in text
has_done = 'event: done' in text
has_any_event = 'event:' in text
session_id = ''
current_event = ''
for raw_line in text.splitlines():
    line = raw_line.strip()
    if line.startswith('event:'):
        current_event = line.split(':', 1)[1].strip()
    elif line.startswith('data:') and current_event == 'status':
        payload = line.split(':', 1)[1].strip()
        try:
            obj = json.loads(payload)
        except Exception:
            continue
        sid = obj.get('session_id') or obj.get('sessionId')
        if sid:
            session_id = str(sid)
            break

print(json.dumps({
    'session_id': session_id,
    'has_status': has_status,
    'has_chunk': has_chunk,
    'has_done': has_done,
    'has_any_event': has_any_event,
    'non_empty': bool(text.strip())
}))
PY
}

quote_url() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote_plus
print(quote_plus(sys.argv[1]))
PY
}

ensure_artifacts_exist() {
  [ -f "${STEP01_FILE}" ] || printf 'not executed due to earlier failure\n' > "${STEP01_FILE}"
  [ -f "${STEP02_FILE}" ] || printf '{"error":"not executed due to earlier failure"}\n' > "${STEP02_FILE}"
  [ -f "${STEP03_FILE}" ] || printf '{"error":"not executed due to earlier failure"}\n' > "${STEP03_FILE}"
  [ -f "${STEP04_FILE}" ] || printf '{"error":"not executed due to earlier failure"}\n' > "${STEP04_FILE}"
  [ -f "${STEP05_FILE}" ] || printf '{"error":"not executed due to earlier failure"}\n' > "${STEP05_FILE}"
  [ -f "${STEP06_FILE}" ] || printf '{"error":"not executed due to earlier failure"}\n' > "${STEP06_FILE}"
  [ -f "${STEP07_FILE}" ] || printf '{"error":"not executed due to earlier failure"}\n' > "${STEP07_FILE}"
  [ -f "${STEP08_FILE}" ] || printf 'not executed due to earlier failure\n' > "${STEP08_FILE}"
  [ -f "${STEP09_FILE}" ] || printf 'not executed due to earlier failure\n' > "${STEP09_FILE}"
  [ -f "${STEP10_FILE}" ] || printf 'not executed due to earlier failure\n' > "${STEP10_FILE}"
}

finalize_results() {
  ensure_artifacts_exist
  python3 - "${TMP_DIR}" "${RESULT_JSON}" "${GATEWAY_BASE_URL}" <<'PY'
import json
import os
import sys
from datetime import datetime, timezone

tmp_dir, output_file, gateway = sys.argv[1], sys.argv[2], sys.argv[3]
step_defs = [
    ('01', 'health'),
    ('02', 'login'),
    ('03', 'users_me'),
    ('04', 'jobs_list'),
    ('05', 'jobs_search'),
    ('06', 'resume_upload'),
    ('07', 'resume_parse'),
    ('08', 'interview_chat_sse'),
    ('09', 'interview_resume_sse'),
    ('10', 'observability'),
]

steps = []
for sid, default_name in step_defs:
    prefix = os.path.join(tmp_dir, f'step_{sid}')
    if os.path.exists(prefix + '.passed'):
        with open(prefix + '.passed', 'r', encoding='utf-8') as f:
            passed_raw = f.read().strip().lower()
        passed = passed_raw == 'true'
        with open(prefix + '.name', 'r', encoding='utf-8') as f:
            name = f.read().strip() or default_name
        with open(prefix + '.detail', 'r', encoding='utf-8') as f:
            detail = f.read().strip()
        with open(prefix + '.artifact', 'r', encoding='utf-8') as f:
            artifact = f.read().strip()
    else:
        passed = False
        name = default_name
        detail = 'not executed due to earlier failure'
        artifact = ''
    steps.append({
        'id': sid,
        'name': name,
        'passed': passed,
        'detail': detail,
        'artifact': artifact,
    })

p0_total = len(step_defs)
p0_passed = sum(1 for s in steps if s['passed'])
weighted_score = round((p0_passed / p0_total) * 100, 2)
result = {
    'generatedAt': datetime.now(timezone.utc).isoformat(),
    'gatewayBaseUrl': gateway,
    'p0': {
        'passed': p0_passed,
        'total': p0_total,
        'allPassed': p0_passed == p0_total,
    },
    'steps': steps,
    'overall': {
        'weightedScore': weighted_score,
        'weights': {
            'P0': 1.0,
        },
        'note': 'stub: only P0 is scored in this smoke run',
    },
}

with open(output_file, 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)
    f.write('\n')
PY

  if [ -f "${ROOT_DIR}/tests/smoke/passrate.py" ]; then
    python3 "${ROOT_DIR}/tests/smoke/passrate.py" --input "${RESULT_JSON}" --output "${REPORT_MD}" >/dev/null 2>&1 || true
  fi
}

fail_and_exit() {
  EXIT_CODE=1
  echo "[ERROR] $1" >&2
  finalize_results
  exit ${EXIT_CODE}
}

require_cmd curl
require_cmd python3

mkdir -p "${EVIDENCE_DIR}"

if [ -n "${SMOKE_RESUME_FILE:-}" ] && [ -f "${SMOKE_RESUME_FILE}" ]; then
  RESUME_FILE="${SMOKE_RESUME_FILE}"
elif [ -f "${ROOT_DIR}/fixtures/resume.pdf" ]; then
  RESUME_FILE="${ROOT_DIR}/fixtures/resume.pdf"
else
  RESUME_FILE="${TMP_DIR}/resume.txt"
  cat > "${RESUME_FILE}" <<EOF
Candidate: Smoke Test User
Email: smoke@example.com
Summary: Backend integration verification resume generated by tests/smoke/p0_smoke.sh.
Experience: 3 years in Java, Python, and gateway integration.
EOF
fi

echo "[INFO] GATEWAY_BASE_URL=${GATEWAY_BASE_URL}"
if [ "${DRY_RUN}" = "true" ]; then
  mark_step "01" "health" "true" "dry-run" "${STEP01_FILE}"
  mark_step "02" "login" "true" "dry-run" "${STEP02_FILE}"
  mark_step "03" "users_me" "true" "dry-run" "${STEP03_FILE}"
  mark_step "04" "jobs_list" "true" "dry-run" "${STEP04_FILE}"
  mark_step "05" "jobs_search" "true" "dry-run" "${STEP05_FILE}"
  mark_step "06" "resume_upload" "true" "dry-run" "${STEP06_FILE}"
  mark_step "07" "resume_parse" "true" "dry-run" "${STEP07_FILE}"
  mark_step "08" "interview_chat_sse" "true" "dry-run" "${STEP08_FILE}"
  mark_step "09" "interview_resume_sse" "true" "dry-run" "${STEP09_FILE}"
  mark_step "10" "observability" "true" "dry-run" "${STEP10_FILE}"
  finalize_results
  echo "[INFO] Dry-run finished. Evidence in ${EVIDENCE_DIR}"
  exit 0
fi

step01_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${STEP01_FILE}" -w "%{http_code}" "${GATEWAY_BASE_URL}/actuator/health" 2>"${TMP_DIR}/step01.err" || true)"
if [ "${step01_code}" != "200" ]; then
  mark_step "01" "health" "false" "health http=${step01_code}" "${STEP01_FILE}"
  fail_and_exit "Step 01 failed: gateway health endpoint unreachable."
fi
mark_step "01" "health" "true" "gateway health returned HTTP 200" "${STEP01_FILE}"

login_payload="${TMP_DIR}/login.json"
cat > "${login_payload}" <<EOF
{"username":"${SMOKE_USERNAME}","password":"${SMOKE_PASSWORD}"}
EOF
step02_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${STEP02_FILE}" -w "%{http_code}" -X POST "${GATEWAY_BASE_URL}/api/v1/auth/login" -H "Content-Type: application/json" --data-binary "@${login_payload}" 2>"${TMP_DIR}/step02.err" || true)"
if [ "${step02_code}" != "200" ]; then
  mark_step "02" "login" "false" "login http=${step02_code}" "${STEP02_FILE}"
  fail_and_exit "Step 02 failed: login endpoint blocked or credentials invalid."
fi
step02_result_code="$(json_code "${STEP02_FILE}" 2>/dev/null || true)"
if [ "${step02_result_code}" != "200" ]; then
  mark_step "02" "login" "false" "login result.code=${step02_result_code}" "${STEP02_FILE}"
  fail_and_exit "Step 02 failed: login result.code != 200."
fi
TOKEN="$(extract_token "${STEP02_FILE}" 2>/dev/null || true)"
if [ -z "${TOKEN}" ]; then
  mark_step "02" "login" "false" "missing accessToken" "${STEP02_FILE}"
  fail_and_exit "Step 02 failed: accessToken missing in login response."
fi
mark_step "02" "login" "true" "login succeeded and accessToken parsed" "${STEP02_FILE}"

step03_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${STEP03_FILE}" -w "%{http_code}" -X GET "${GATEWAY_BASE_URL}/api/v1/users/me" -H "Authorization: Bearer ${TOKEN}" 2>"${TMP_DIR}/step03.err" || true)"
if [ "${step03_code}" != "200" ]; then
  mark_step "03" "users_me" "false" "users/me http=${step03_code}" "${STEP03_FILE}"
  fail_and_exit "Step 03 failed: /users/me request failed."
fi
step03_result_code="$(json_code "${STEP03_FILE}" 2>/dev/null || true)"
if [ "${step03_result_code}" != "200" ]; then
  mark_step "03" "users_me" "false" "users/me result.code=${step03_result_code}" "${STEP03_FILE}"
  fail_and_exit "Step 03 failed: /users/me result.code != 200."
fi
mark_step "03" "users_me" "true" "users/me succeeded" "${STEP03_FILE}"

step04_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${STEP04_FILE}" -w "%{http_code}" -X GET "${GATEWAY_BASE_URL}/api/v1/jobs?current=1&size=10" -H "Authorization: Bearer ${TOKEN}" 2>"${TMP_DIR}/step04.err" || true)"
if [ "${step04_code}" != "200" ]; then
  mark_step "04" "jobs_list" "false" "jobs list http=${step04_code}" "${STEP04_FILE}"
  fail_and_exit "Step 04 failed: /jobs list request failed."
fi
step04_result_code="$(json_code "${STEP04_FILE}" 2>/dev/null || true)"
if [ "${step04_result_code}" != "200" ]; then
  mark_step "04" "jobs_list" "false" "jobs list result.code=${step04_result_code}" "${STEP04_FILE}"
  fail_and_exit "Step 04 failed: /jobs list result.code != 200."
fi
JOB_ID="$(extract_job_id "${STEP04_FILE}" 2>/dev/null || true)"
mark_step "04" "jobs_list" "true" "jobs list succeeded" "${STEP04_FILE}"

encoded_keyword="$(quote_url "${SMOKE_JOB_KEYWORD}")"
step05_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${STEP05_FILE}" -w "%{http_code}" -X GET "${GATEWAY_BASE_URL}/api/v1/jobs/search?keyword=${encoded_keyword}" -H "Authorization: Bearer ${TOKEN}" 2>"${TMP_DIR}/step05.err" || true)"
if [ "${step05_code}" != "200" ]; then
  mark_step "05" "jobs_search" "false" "jobs search http=${step05_code}" "${STEP05_FILE}"
  fail_and_exit "Step 05 failed: /jobs/search request failed."
fi
step05_result_code="$(json_code "${STEP05_FILE}" 2>/dev/null || true)"
if [ "${step05_result_code}" != "200" ]; then
  mark_step "05" "jobs_search" "false" "jobs search result.code=${step05_result_code}" "${STEP05_FILE}"
  fail_and_exit "Step 05 failed: /jobs/search result.code != 200."
fi
if [ -z "${JOB_ID}" ]; then
  JOB_ID="$(extract_job_id "${STEP05_FILE}" 2>/dev/null || true)"
fi
mark_step "05" "jobs_search" "true" "jobs search succeeded" "${STEP05_FILE}"

step06_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${STEP06_FILE}" -w "%{http_code}" -X POST "${GATEWAY_BASE_URL}/api/v1/resumes/upload" -H "Authorization: Bearer ${TOKEN}" -F "file=@${RESUME_FILE}" 2>"${TMP_DIR}/step06.err" || true)"
if [ "${step06_code}" != "200" ]; then
  mark_step "06" "resume_upload" "false" "resume upload http=${step06_code}" "${STEP06_FILE}"
  fail_and_exit "Step 06 failed: resume upload request failed."
fi
step06_result_code="$(json_code "${STEP06_FILE}" 2>/dev/null || true)"
if [ "${step06_result_code}" != "200" ]; then
  mark_step "06" "resume_upload" "false" "resume upload result.code=${step06_result_code}" "${STEP06_FILE}"
  fail_and_exit "Step 06 failed: resume upload result.code != 200."
fi
RESUME_ID="$(extract_resume_id "${STEP06_FILE}" 2>/dev/null || true)"
if [ -z "${RESUME_ID}" ]; then
  mark_step "06" "resume_upload" "false" "resumeId missing from data.id" "${STEP06_FILE}"
  fail_and_exit "Step 06 failed: resumeId not found in upload response."
fi
mark_step "06" "resume_upload" "true" "resume uploaded, resumeId=${RESUME_ID}" "${STEP06_FILE}"

step07_payload="${TMP_DIR}/parse.json"
cat > "${step07_payload}" <<EOF
{}
EOF
step07_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${STEP07_FILE}" -w "%{http_code}" -X POST "${GATEWAY_BASE_URL}/api/v1/resumes/${RESUME_ID}/parse" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" --data-binary "@${step07_payload}" 2>"${TMP_DIR}/step07.err" || true)"
if [ "${step07_code}" != "200" ]; then
  mark_step "07" "resume_parse" "false" "resume parse http=${step07_code}" "${STEP07_FILE}"
  fail_and_exit "Step 07 failed: resume parse request failed."
fi
step07_result_code="$(json_code "${STEP07_FILE}" 2>/dev/null || true)"
if [ "${step07_result_code}" != "200" ]; then
  mark_step "07" "resume_parse" "false" "resume parse result.code=${step07_result_code}" "${STEP07_FILE}"
  fail_and_exit "Step 07 failed: resume parse result.code != 200."
fi
mark_step "07" "resume_parse" "true" "resume parse succeeded" "${STEP07_FILE}"

step08_headers="${TMP_DIR}/step08.headers"
step08_payload="${TMP_DIR}/chat.json"
if [ -n "${JOB_ID}" ]; then
  cat > "${step08_payload}" <<EOF
{"sessionId":null,"message":"${SMOKE_MESSAGE}","resumeId":${RESUME_ID},"jobId":${JOB_ID}}
EOF
else
  cat > "${step08_payload}" <<EOF
{"sessionId":null,"message":"${SMOKE_MESSAGE}","resumeId":${RESUME_ID},"jobId":null}
EOF
fi
step08_code="$(curl -sS -N -m "${SSE_MAX_TIME}" -D "${step08_headers}" -o "${STEP08_FILE}" -w "%{http_code}" -X POST "${GATEWAY_BASE_URL}/api/v1/interviews/chat" -H "Authorization: Bearer ${TOKEN}" -H "Accept: text/event-stream" -H "Content-Type: application/json" --data-binary "@${step08_payload}" 2>"${TMP_DIR}/step08.err" || true)"
step08_ct="$(python3 - "${step08_headers}" <<'PY'
import sys
from pathlib import Path
headers = Path(sys.argv[1]).read_text(encoding='utf-8', errors='replace') if Path(sys.argv[1]).exists() else ''
for line in headers.splitlines():
    if line.lower().startswith('content-type:'):
        print(line.split(':',1)[1].strip().lower())
        break
PY
)"
if [ "${step08_code}" != "200" ]; then
  mark_step "08" "interview_chat_sse" "false" "chat sse http=${step08_code}" "${STEP08_FILE}"
  fail_and_exit "Step 08 failed: /interviews/chat SSE request failed."
fi
if [ "${step08_ct#*text/event-stream}" = "${step08_ct}" ]; then
  mark_step "08" "interview_chat_sse" "false" "chat sse content-type invalid" "${STEP08_FILE}"
  fail_and_exit "Step 08 failed: chat SSE content-type invalid."
fi
step08_check="$(inspect_sse "${STEP08_FILE}")"
SESSION_ID="$(python3 - "${step08_check}" <<'PY'
import json
import sys
obj = json.loads(sys.argv[1])
print(obj.get('session_id', ''))
PY
)"
step08_has_any="$(python3 - "${step08_check}" <<'PY'
import json
import sys
obj = json.loads(sys.argv[1])
print('true' if obj.get('has_any_event') else 'false')
PY
)"
step08_has_status="$(python3 - "${step08_check}" <<'PY'
import json
import sys
obj = json.loads(sys.argv[1])
print('true' if obj.get('has_status') else 'false')
PY
)"
step08_has_chunk="$(python3 - "${step08_check}" <<'PY'
import json
import sys
obj = json.loads(sys.argv[1])
print('true' if obj.get('has_chunk') else 'false')
PY
)"
step08_has_done="$(python3 - "${step08_check}" <<'PY'
import json
import sys
obj = json.loads(sys.argv[1])
print('true' if obj.get('has_done') else 'false')
PY
)"
if [ "${step08_has_any}" != "true" ]; then
  mark_step "08" "interview_chat_sse" "false" "chat sse produced no event frames" "${STEP08_FILE}"
  fail_and_exit "Step 08 failed: chat SSE stream had no events."
fi
step08_detail="chat sse output detected"
if [ "${step08_has_status}" = "true" ] && [ "${step08_has_chunk}" = "true" ] && [ "${step08_has_done}" = "true" ]; then
  step08_detail="chat sse includes status/chunk/done"
elif [ "${step08_has_status}" = "true" ] && [ "${step08_has_chunk}" = "true" ]; then
  step08_detail="chat sse includes status/chunk; done not observed before timeout"
fi
mark_step "08" "interview_chat_sse" "true" "${step08_detail}" "${STEP08_FILE}"

if [ -z "${SESSION_ID}" ]; then
  mark_step "09" "interview_resume_sse" "false" "sessionId missing from chat status event" "${STEP09_FILE}"
  fail_and_exit "Step 09 failed: cannot resume interview without sessionId."
fi

step09_headers="${TMP_DIR}/step09.headers"
step09_code="$(curl -sS -N -m "${SSE_MAX_TIME}" -D "${step09_headers}" -o "${STEP09_FILE}" -w "%{http_code}" -X POST "${GATEWAY_BASE_URL}/api/v1/interviews/${SESSION_ID}/resume" -H "Authorization: Bearer ${TOKEN}" -H "Accept: text/event-stream" 2>"${TMP_DIR}/step09.err" || true)"
step09_ct="$(python3 - "${step09_headers}" <<'PY'
import sys
from pathlib import Path
headers = Path(sys.argv[1]).read_text(encoding='utf-8', errors='replace') if Path(sys.argv[1]).exists() else ''
for line in headers.splitlines():
    if line.lower().startswith('content-type:'):
        print(line.split(':',1)[1].strip().lower())
        break
PY
)"
if [ "${step09_code}" != "200" ]; then
  mark_step "09" "interview_resume_sse" "false" "resume sse http=${step09_code}" "${STEP09_FILE}"
  fail_and_exit "Step 09 failed: /interviews/{sessionId}/resume request failed."
fi
if [ "${step09_ct#*text/event-stream}" = "${step09_ct}" ]; then
  mark_step "09" "interview_resume_sse" "false" "resume sse content-type invalid" "${STEP09_FILE}"
  fail_and_exit "Step 09 failed: resume SSE content-type invalid."
fi
step09_check="$(inspect_sse "${STEP09_FILE}")"
step09_has_any="$(python3 - "${step09_check}" <<'PY'
import json
import sys
obj = json.loads(sys.argv[1])
print('true' if obj.get('has_any_event') else 'false')
PY
)"
if [ "${step09_has_any}" != "true" ]; then
  mark_step "09" "interview_resume_sse" "false" "resume sse produced no event frames" "${STEP09_FILE}"
  fail_and_exit "Step 09 failed: resume SSE stream had no events."
fi
mark_step "09" "interview_resume_sse" "true" "resume sse output detected" "${STEP09_FILE}"

step10_probe_body="${TMP_DIR}/step10-probe.json"
step10_probe_code="$(curl -sS -m "${CURL_MAX_TIME}" -o "${step10_probe_body}" -w "%{http_code}" -X POST "${GATEWAY_BASE_URL}/api/v1/resumes/999999999/parse" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" --data-binary '{}' 2>"${TMP_DIR}/step10.err" || true)"
step10_probe_result="$(json_code "${step10_probe_body}" 2>/dev/null || true)"
{
  printf 'step10_probe_http=%s\n' "${step10_probe_code}"
  printf 'step10_probe_result_code=%s\n' "${step10_probe_result}"
  printf 'session_id=%s\n' "${SESSION_ID}"
  printf 'resume_id=%s\n' "${RESUME_ID}"
  if [ -n "${SMOKE_RESUME_FILE:-}" ]; then
    printf 'resume_source=%s\n' "${SMOKE_RESUME_FILE}"
  else
    printf 'resume_source=%s\n' "${RESUME_FILE}"
  fi
} > "${STEP10_FILE}"
if [ "${step10_probe_code}" = "200" ] && [ -n "${step10_probe_result}" ]; then
  mark_step "10" "observability" "true" "error mapping probe returned structured Result JSON" "${STEP10_FILE}"
else
  mark_step "10" "observability" "false" "error mapping probe unavailable (http=${step10_probe_code})" "${STEP10_FILE}"
  fail_and_exit "Step 10 failed: observability probe did not return structured result."
fi

finalize_results
echo "[INFO] Smoke completed. Evidence in ${EVIDENCE_DIR}"
exit 0
