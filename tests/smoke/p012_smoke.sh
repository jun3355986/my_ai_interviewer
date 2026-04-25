#!/bin/bash

set -u

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:9000}"
SMOKE_JOB_KEYWORD="${SMOKE_JOB_KEYWORD:-java}"

EVIDENCE_DIR=".sisyphus/evidence"
P0_RESULTS_FILE="$EVIDENCE_DIR/task-8-p0-results.json"
RESULTS_FILE="$EVIDENCE_DIR/task-8-p012-results.json"

mkdir -p "$EVIDENCE_DIR"

bash "tests/smoke/p0_smoke.sh" "$@" || true

python3 - "$P0_RESULTS_FILE" "$RESULTS_FILE" "$GATEWAY_BASE_URL" "$SMOKE_JOB_KEYWORD" <<'PY'
import json
import subprocess
import sys
from pathlib import Path


def read_json(path: Path):
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def curl_json(cmd):
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except Exception as exc:
        return False, {}, f"command error: {exc}"
    out = proc.stdout.strip()
    if not out:
        return False, {}, "empty response"
    try:
        data = json.loads(out)
    except Exception:
        return False, {}, out[:300]
    ok = data.get("code") == 200
    return ok, data, data.get("message", "ok" if ok else "unknown")


def write_text(path: Path, data):
    path.write_text(data, encoding="utf-8")


p0_path = Path(sys.argv[1])
out_path = Path(sys.argv[2])
base_url = sys.argv[3]
keyword = sys.argv[4]
evidence_dir = out_path.parent

p0_data = read_json(p0_path)
p0_steps = p0_data.get("steps", [])
p0_passed = int(p0_data.get("p0", {}).get("passed", 0))

token = ""
resume_id = ""
session_id = ""

login = read_json(evidence_dir / "task-8-step-02-login.json")
if login.get("code") == 200:
    token = str(login.get("data", {}).get("accessToken", ""))

upload = read_json(evidence_dir / "task-8-step-06-resume-upload.json")
if upload.get("code") == 200:
    resume_id = str(upload.get("data", {}).get("id", ""))

sse_path = evidence_dir / "task-8-step-08-interview-chat.sse.txt"
if sse_path.exists():
    for line in sse_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if line.startswith("data:"):
            try:
                payload = json.loads(line[5:].strip())
            except Exception:
                continue
            if isinstance(payload, dict):
                sid = payload.get("session_id") or payload.get("sessionId")
                if sid:
                    session_id = str(sid)
                    break

failed_triads = []


def add_triads_from_p0(steps):
    for step in steps:
        if step.get("passed"):
            continue
        sid = step.get("id", "")
        failed_triads.append(
            {
                "id": f"P0-{sid}",
                "command": f"bash tests/smoke/p0_smoke.sh (step {sid})",
                "cause": str(step.get("detail", "step failed")),
                "suggestion": "查看对应 artifact 并修复上游依赖（账号、token、resumeId、sessionId 或服务可用性）",
            }
        )


def run_step(step_id, name, cmd, artifact, skip_reason=None):
    if skip_reason:
        write_text(artifact, json.dumps({"code": 4999, "message": skip_reason}, ensure_ascii=False))
        failed_triads.append(
            {
                "id": step_id,
                "command": " ".join(cmd),
                "cause": skip_reason,
                "suggestion": "先修复前置步骤（登录、上传或会话创建），再重跑此命令",
            }
        )
        return {"id": step_id, "name": name, "passed": False, "detail": skip_reason, "artifact": str(artifact)}

    ok, data, msg = curl_json(cmd)
    write_text(artifact, json.dumps(data if data else {"message": msg}, ensure_ascii=False))
    if not ok:
        failed_triads.append(
            {
                "id": step_id,
                "command": " ".join(cmd),
                "cause": msg,
                "suggestion": "检查网关路由、鉴权 token 与下游服务日志，确认接口契约和数据前置条件",
            }
        )
    return {"id": step_id, "name": name, "passed": ok, "detail": "ok" if ok else msg, "artifact": str(artifact)}


headers = []
if token:
    headers = ["-H", f"Authorization: Bearer {token}"]

p1_steps = []
p2_steps = []

p1_steps.append(
    run_step(
        "11",
        "List Resumes",
        ["curl", "-s", *headers, f"{base_url}/api/v1/resumes"],
        evidence_dir / "task-8-step-11-list-resumes.json",
        skip_reason=None if token else "Skipped: no token",
    )
)

p1_steps.append(
    run_step(
        "12",
        "Get Session Info",
        ["curl", "-s", *headers, f"{base_url}/api/v1/interviews/{session_id}"],
        evidence_dir / "task-8-step-12-session-info.json",
        skip_reason=None if (token and session_id) else "Skipped: no session_id",
    )
)

p1_steps.append(
    run_step(
        "13",
        "Search Jobs",
        ["curl", "-s", *headers, f"{base_url}/api/v1/jobs/search?keyword={keyword}"],
        evidence_dir / "task-8-step-13-search-jobs.json",
        skip_reason=None if token else "Skipped: no token",
    )
)

p1_steps.append(
    run_step(
        "14",
        "View Evaluation",
        ["curl", "-s", *headers, f"{base_url}/api/v1/evaluations/{session_id}"],
        evidence_dir / "task-8-step-14-evaluation.json",
        skip_reason=None if (token and session_id) else "Skipped: no session_id",
    )
)

p2_steps.append(
    run_step(
        "15",
        "Unread Notifications",
        ["curl", "-s", *headers, f"{base_url}/api/v1/notifications/unread-count"],
        evidence_dir / "task-8-step-15-unread-notifications.json",
        skip_reason=None if token else "Skipped: no token",
    )
)

p2_steps.append(
    run_step(
        "16",
        "Set Default Resume",
        ["curl", "-s", "-X", "PUT", *headers, f"{base_url}/api/v1/resumes/{resume_id}/default"],
        evidence_dir / "task-8-step-16-set-default-resume.json",
        skip_reason=None if (token and resume_id) else "Skipped: no resume_id",
    )
)

add_triads_from_p0(p0_steps)

p1_passed = sum(1 for s in p1_steps if s["passed"])
p2_passed = sum(1 for s in p2_steps if s["passed"])

t_p0, t_p1, t_p2 = 10, 4, 2
numerator = p0_passed * 1.0 + p1_passed * 0.5 + p2_passed * 0.2
denominator = t_p0 * 1.0 + t_p1 * 0.5 + t_p2 * 0.2
weighted = round((numerator / denominator) * 100, 2) if denominator else 0.0
p0_gate = p0_passed == t_p0

status = "RED"
if p0_gate and weighted >= 90:
    status = "GREEN"
elif p0_gate and weighted >= 80:
    status = "YELLOW"

result = {
    "gatewayBaseUrl": base_url,
    "steps": p0_steps + p1_steps + p2_steps,
    "p0": {"passed": p0_passed, "total": t_p0, "allPassed": p0_gate},
    "p1": {"passed": p1_passed, "total": t_p1},
    "p2": {"passed": p2_passed, "total": t_p2},
    "overall": {
        "weightedScore": weighted,
        "target": 90,
        "formula": "(P0*1.0 + P1*0.5 + P2*0.2)/(T0*1.0 + T1*0.5 + T2*0.2)*100",
        "status": status,
    },
    "failedTriads": failed_triads,
}

out_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Wrote {out_path}")
PY

python3 "tests/smoke/passrate.py" --input "$RESULTS_FILE" --output "$EVIDENCE_DIR/task-8-p0-smoke-report.md"
