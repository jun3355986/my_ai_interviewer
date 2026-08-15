#!/usr/bin/env python3
"""Run a redacted full durable Interview flow through the local Gateway.

The runner deliberately records transport/state evidence, not candidate answers,
JWTs, or question text. It is intended for a local Day 3 FDE baseline run.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from interview_replay import ReplayError, login


ROOT_DIR = Path(__file__).resolve().parents[2]
DEFAULT_REPORT_DIR = ROOT_DIR / "tests" / "reports" / "durable-replay"
TERMINAL_ATTEMPT_STATUSES = {"COMPLETED", "FAILED", "INTERRUPTED", "CANCELLED", "DISCARDED"}


def _request(
    method: str,
    url: str,
    *,
    token: str,
    payload: dict[str, Any] | None,
    timeout: int,
) -> tuple[dict[str, Any], bool]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            raw = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as error:
        raise ReplayError(f"request transport failed: {error.reason}") from error

    try:
        envelope = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ReplayError(f"response was not JSON (HTTP {status})") from error
    if not isinstance(envelope, dict):
        raise ReplayError(f"response envelope was not an object (HTTP {status})")
    if status != 200 or envelope.get("code") != 200 or not isinstance(envelope.get("data"), dict):
        raise ReplayError(f"request failed with HTTP {status}: {envelope.get('message') or 'unknown error'}")
    return dict(envelope["data"]), bool(envelope.get("traceId"))


def _new_turn_id(prefix: str) -> str:
    return f"day3-{prefix}-{uuid.uuid4().hex[:24]}"


def _wait_for_attempt(
    base_url: str,
    token: str,
    turn_id: str,
    timeout: int,
) -> tuple[dict[str, Any], int, bool]:
    started = time.monotonic()
    saw_trace = False
    while True:
        attempt, trace_present = _request(
            "GET",
            f"{base_url}/api/v1/interviews/turn-attempts/{turn_id}",
            token=token,
            payload=None,
            timeout=timeout,
        )
        saw_trace = saw_trace or trace_present
        status = str(attempt.get("status") or "")
        if status in TERMINAL_ATTEMPT_STATUSES:
            return attempt, int((time.monotonic() - started) * 1000), saw_trace
        if time.monotonic() - started >= timeout:
            raise ReplayError("turn attempt did not reach a terminal state before timeout")
        time.sleep(0.5)


def _answer_for_stage(stage: str | None) -> str:
    normalized = (stage or "").lower()
    if "technical" in normalized:
        return "我会先说明关键约束、给出可验证的实现方案，并补充异常和边界处理。"
    if "project" in normalized:
        return "我会按背景、职责、方案、结果和复盘来说明，并明确我亲自负责的部分。"
    return "我会先给出结论，再说明依据、取舍和可以验证的结果。"


def _database_readback(branch_id: str, container_name: str | None) -> dict[str, Any]:
    """Read only aggregate Day 3 evidence from the local Compose database."""
    if not container_name:
        return {"available": False}
    sql = f"""
        SELECT
          session.status,
          COALESCE(session.stage, 'NONE'),
          COALESCE((
            SELECT attempt.status
            FROM t_interview_turn_attempt attempt
            WHERE attempt.session_id = session.id
            ORDER BY attempt.created_at DESC
            LIMIT 1
          ), 'NONE'),
          (SELECT count(*) FROM t_interview_message message WHERE message.session_id = session.id),
          (SELECT count(*) FROM t_score_record score WHERE score.session_id = session.id),
          (SELECT count(*) FROM t_evaluation evaluation WHERE evaluation.session_id = session.id),
          COALESCE((
            SELECT trace.status
            FROM t_ai_trace trace
            WHERE trace.session_id = session.id
            ORDER BY trace.created_at DESC
            LIMIT 1
          ), 'NONE'),
          COALESCE((
            SELECT trace.error_code
            FROM t_ai_trace trace
            WHERE trace.session_id = session.id
            ORDER BY trace.created_at DESC
            LIMIT 1
          ), 'NONE'),
          COALESCE((
            SELECT trace.duration_ms::text
            FROM t_ai_trace trace
            WHERE trace.session_id = session.id
            ORDER BY trace.created_at DESC
            LIMIT 1
          ), 'NONE'),
          (SELECT count(*) FROM t_ai_trace_step step
            JOIN t_ai_trace trace ON trace.id = step.trace_id
            WHERE trace.session_id = session.id)
        FROM t_interview_session session
        WHERE session.id = '{branch_id}'
        LIMIT 1
    """
    try:
        completed = subprocess.run(
            [
                "docker",
                "exec",
                container_name,
                "sh",
                "-c",
                'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "$1"',
                "sh",
                sql,
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=15,
        )
    except (OSError, subprocess.TimeoutExpired):
        return {"available": False, "queryFailed": True}
    if completed.returncode != 0:
        return {"available": False, "queryFailed": True}
    values = completed.stdout.strip().split("|")
    if len(values) != 10:
        return {"available": False, "queryFailed": True}
    return {
        "available": True,
        "branchStatus": int(values[0]),
        "stage": values[1],
        "attemptStatus": values[2],
        "messageCount": int(values[3]),
        "scoreCount": int(values[4]),
        "evaluationCount": int(values[5]),
        "aiTraceStatus": values[6],
        "aiTraceErrorCode": values[7],
        "aiTraceDurationMs": None if values[8] == "NONE" else int(values[8]),
        "aiTraceStepCount": int(values[9]),
    }


def run_full_flow(args: argparse.Namespace) -> dict[str, Any]:
    base_url = args.gateway_base_url.rstrip("/")
    token = args.access_token or login(base_url, args.username, args.password, args.timeout)
    started_at = datetime.now(timezone.utc).isoformat()
    steps: list[dict[str, Any]] = []

    start_payload = {"turnId": _new_turn_id("start")}
    start_started = time.monotonic()
    start, start_trace = _request(
        "POST",
        f"{base_url}/api/v1/interviews/start-attempts",
        token=token,
        payload=start_payload,
        timeout=args.timeout,
    )
    branch_id = str(start.get("branchId") or "")
    attempt = start.get("attempt")
    if not branch_id or not isinstance(attempt, dict) or not attempt.get("turnId"):
        raise ReplayError("durable start response omitted branchId or turnId")
    pending_turn_id = str(attempt["turnId"])
    steps.append(
        {
            "step": 0,
            "kind": "start",
            "requestDurationMs": int((time.monotonic() - start_started) * 1000),
            "gatewayTraceIdPresent": start_trace,
        }
    )

    for step_number in range(1, args.max_turns + 1):
        completed, wait_duration_ms, poll_trace = _wait_for_attempt(
            base_url,
            token,
            pending_turn_id,
            args.timeout,
        )
        attempt_status = str(completed.get("status") or "")
        if attempt_status != "COMPLETED":
            steps.append(
                {
                    "step": step_number,
                    "kind": "turn",
                    "attemptStatus": attempt_status,
                    "attemptErrorCode": completed.get("errorCode"),
                    "attemptWaitDurationMs": wait_duration_ms,
                    "gatewayTraceIdPresent": poll_trace,
                }
            )
            return {
                "ok": False,
                "generatedAt": started_at,
                "branchId": branch_id,
                "steps": steps,
                "database": _database_readback(branch_id, args.postgres_container),
                "error": f"turn attempt finished as {attempt_status}",
            }

        transcript_started = time.monotonic()
        transcript, transcript_trace = _request(
            "GET",
            f"{base_url}/api/v1/interviews/branches/{branch_id}/transcript",
            token=token,
            payload=None,
            timeout=args.timeout,
        )
        messages = transcript.get("messages")
        if not isinstance(messages, list) or not messages:
            raise ReplayError("completed attempt did not produce a canonical transcript")
        tail = messages[-1]
        if not isinstance(tail, dict):
            raise ReplayError("canonical transcript tail was malformed")
        stage = str(transcript.get("stage") or "")
        branch_status = transcript.get("status")
        steps.append(
            {
                "step": step_number,
                "kind": "turn",
                "attemptStatus": attempt_status,
                "stage": stage,
                "branchStatus": branch_status,
                "attemptWaitDurationMs": wait_duration_ms,
                "transcriptDurationMs": int((time.monotonic() - transcript_started) * 1000),
                "gatewayTraceIdPresent": poll_trace or transcript_trace,
                "canonicalMessageCount": len(messages),
                "tailMessageType": tail.get("messageType"),
                "tailExpectsResponse": tail.get("expectsResponse") is True,
            }
        )

        if branch_status == 2 or stage.lower() == "concluded":
            evaluation_started = time.monotonic()
            evaluation, evaluation_trace = _request(
                "POST",
                f"{base_url}/api/v1/evaluations/{branch_id}",
                token=token,
                payload=None,
                timeout=args.timeout,
            )
            report = {
                "overallScore": evaluation.get("overallScore"),
                "technicalScore": evaluation.get("technicalScore"),
                "communicationScore": evaluation.get("communicationScore"),
                "logicScore": evaluation.get("logicScore"),
                "experienceScore": evaluation.get("experienceScore"),
                "summaryPresent": bool(evaluation.get("summary")),
                "recommendation": evaluation.get("recommendation"),
                "durationMs": int((time.monotonic() - evaluation_started) * 1000),
                "gatewayTraceIdPresent": evaluation_trace,
            }
            return {
                "ok": True,
                "generatedAt": started_at,
                "branchId": branch_id,
                "steps": steps,
                "evaluation": report,
                "database": _database_readback(branch_id, args.postgres_container),
            }

        tail_id = tail.get("id")
        branch_version = transcript.get("branchVersion")
        if not isinstance(tail_id, int) or not isinstance(branch_version, int):
            raise ReplayError("canonical transcript omitted tail id or branch version")
        pending_turn_id = _new_turn_id(f"turn{step_number}")
        _request(
            "POST",
            f"{base_url}/api/v1/interviews/branches/{branch_id}/turn-attempts",
            token=token,
            payload={
                "turnId": pending_turn_id,
                "candidateAnswer": _answer_for_stage(stage),
                "expectedBranchVersion": branch_version,
                "expectedTailMessageId": tail_id,
            },
            timeout=args.timeout,
        )

    raise ReplayError(f"interview did not conclude within maxTurns={args.max_turns}")


def write_report(report: dict[str, Any], report_dir: Path) -> Path:
    report_dir.mkdir(parents=True, exist_ok=True)
    output = report_dir / f"durable-day3-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return output


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Replay a full redacted durable interview flow")
    parser.add_argument("--gateway-base-url", default=os.getenv("GATEWAY_BASE_URL", "http://localhost:9000"))
    parser.add_argument("--username", default=os.getenv("SMOKE_USERNAME", "admin"))
    parser.add_argument("--password", default=os.getenv("SMOKE_PASSWORD", "admin123"))
    parser.add_argument("--access-token", default=os.getenv("REPLAY_ACCESS_TOKEN"))
    parser.add_argument("--timeout", type=int, default=int(os.getenv("DURABLE_REPLAY_TIMEOUT", "45")))
    parser.add_argument("--max-turns", type=int, default=15)
    parser.add_argument(
        "--postgres-container",
        default=os.getenv("DURABLE_REPLAY_POSTGRES_CONTAINER", "ai-interviewer-postgres"),
        help="Local PostgreSQL Compose container for aggregate readback; pass an empty value to skip.",
    )
    parser.add_argument("--report-dir", type=Path, default=DEFAULT_REPORT_DIR)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    report: dict[str, Any]
    try:
        report = run_full_flow(args)
    except ReplayError as error:
        report = {"ok": False, "generatedAt": datetime.now(timezone.utc).isoformat(), "error": str(error)}
    output = write_report(report, args.report_dir)
    print(f"Durable replay report: {output}")
    if report["ok"]:
        print(f"[PASS] full durable flow completed in {len(report['steps'])} turn(s)")
        return 0
    print(f"[FAIL] {report['error']}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
