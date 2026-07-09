#!/usr/bin/env python3
"""Replay interview SSE traces against the gateway or interview service."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT_DIR = Path(__file__).resolve().parents[2]
DEFAULT_REPORT_DIR = ROOT_DIR / "tests" / "reports" / "replay"


class ReplayError(RuntimeError):
    """Raised when a trace or replay result is invalid."""


@dataclass
class SseEvent:
    name: str
    data: dict[str, Any]
    raw: str


@dataclass
class TraceStep:
    step: int
    action: str
    message: str = ""
    session_id: str | None = None
    session_ref: str | None = None
    resume_content: str | None = None
    job_requirements: str | None = None
    candidate_name: str | None = None
    resume_id: int | None = None
    job_id: int | None = None
    expect_events: list[str] = field(default_factory=list)
    expect_stage: str | None = None


def parse_sse_events(body: str) -> list[SseEvent]:
    events: list[SseEvent] = []
    current_event = ""
    current_data: list[str] = []
    current_raw: list[str] = []

    def flush() -> None:
        nonlocal current_event, current_data, current_raw
        if not current_event and not current_data:
            current_raw = []
            return
        raw = "\n".join(current_raw)
        data_text = "\n".join(current_data).strip()
        payload: dict[str, Any]
        if data_text:
            try:
                parsed = json.loads(data_text)
            except json.JSONDecodeError:
                parsed = {"_raw": data_text}
            payload = parsed if isinstance(parsed, dict) else {"value": parsed}
        else:
            payload = {}
        events.append(SseEvent(current_event or "message", payload, raw))
        current_event = ""
        current_data = []
        current_raw = []

    for raw_line in body.splitlines():
        line = raw_line.rstrip("\r")
        if line == "":
            flush()
            continue
        current_raw.append(line)
        if line.startswith("event:"):
            current_event = line.split(":", 1)[1].strip()
        elif line.startswith("data:"):
            current_data.append(line.split(":", 1)[1].strip())

    flush()
    return events


def _require_int(value: Any, field_name: str, line_number: int) -> int:
    if not isinstance(value, int):
        raise ReplayError(f"line {line_number}: {field_name} must be an integer")
    return value


def _optional_int(value: Any, field_name: str, line_number: int) -> int | None:
    if value is None:
        return None
    if not isinstance(value, int):
        raise ReplayError(f"line {line_number}: {field_name} must be an integer")
    return value


def _optional_str(value: Any, field_name: str, line_number: int) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ReplayError(f"line {line_number}: {field_name} must be a string")
    return value


def _string_list(value: Any, field_name: str, line_number: int) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ReplayError(f"line {line_number}: {field_name} must be a string list")
    return value


def load_trace(path: str | Path) -> list[TraceStep]:
    trace_path = Path(path)
    if not trace_path.exists():
        raise ReplayError(f"trace file not found: {trace_path}")

    steps: list[TraceStep] = []
    for line_number, raw_line in enumerate(trace_path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ReplayError(f"line {line_number}: invalid JSON: {exc}") from exc
        if not isinstance(obj, dict):
            raise ReplayError(f"line {line_number}: trace step must be a JSON object")

        steps.append(
            TraceStep(
                step=_require_int(obj.get("step"), "step", line_number),
                action=_optional_str(obj.get("action"), "action", line_number) or "chat",
                message=_optional_str(obj.get("message"), "message", line_number) or "",
                session_id=_optional_str(obj.get("sessionId"), "sessionId", line_number),
                session_ref=_optional_str(obj.get("sessionRef"), "sessionRef", line_number),
                resume_content=_optional_str(obj.get("resumeContent"), "resumeContent", line_number),
                job_requirements=_optional_str(obj.get("jobRequirements"), "jobRequirements", line_number),
                candidate_name=_optional_str(obj.get("candidateName"), "candidateName", line_number),
                resume_id=_optional_int(obj.get("resumeId"), "resumeId", line_number),
                job_id=_optional_int(obj.get("jobId"), "jobId", line_number),
                expect_events=_string_list(obj.get("expectEvents"), "expectEvents", line_number),
                expect_stage=_optional_str(obj.get("expectStage"), "expectStage", line_number),
            )
        )

    if not steps:
        raise ReplayError(f"trace file is empty: {trace_path}")
    return steps


def validate_step_result(step: TraceStep, events: list[SseEvent]) -> list[str]:
    errors: list[str] = []
    names = [event.name for event in events]
    for expected_event in step.expect_events:
        if expected_event not in names:
            errors.append(f"missing expected event: {expected_event}")

    if step.expect_stage:
        stage = extract_stage(events)
        if stage != step.expect_stage:
            errors.append(f"expected stage {step.expect_stage} but got {stage or '<missing>'}")

    error_events = [event for event in events if event.name == "error"]
    for event in error_events:
        message = event.data.get("message") or event.data.get("code") or event.raw
        errors.append(f"received error event: {message}")

    return errors


def _first_event_value(events: list[SseEvent], keys: tuple[str, ...]) -> str | None:
    for event in events:
        for key in keys:
            value = event.data.get(key)
            if value:
                return str(value)
    return None


def extract_session_id(events: list[SseEvent]) -> str | None:
    return _first_event_value(events, ("session_id", "sessionId", "python_session_id", "pythonSessionId"))


def extract_java_session_id(events: list[SseEvent]) -> str | None:
    return _first_event_value(events, ("java_session_id", "javaSessionId"))


def extract_stage(events: list[SseEvent]) -> str | None:
    for event in reversed(events):
        value = event.data.get("stage") or event.data.get("next_stage")
        if value:
            return str(value)
    return None


def event_stage(event: SseEvent) -> str | None:
    value = event.data.get("stage") or event.data.get("next_stage")
    return str(value) if value else None


def build_session_timeline(step: TraceStep, events: list[SseEvent], duration_ms: int) -> list[dict[str, Any]]:
    timeline: list[dict[str, Any]] = []
    for index, event in enumerate(events, start=1):
        timeline.append(
            {
                "step": step.step,
                "index": index,
                "event": event.name,
                "durationMs": duration_ms,
                "javaSessionId": extract_java_session_id([event]),
                "pythonSessionId": extract_session_id([event]),
                "stage": event_stage(event),
            }
        )
    return timeline


def _display(value: Any) -> str:
    return str(value) if value not in (None, "") else "<missing>"


def format_failure_timeline(report: dict[str, Any]) -> str:
    lines: list[str] = []
    for step in report.get("steps", []):
        if not step.get("errors"):
            continue
        if not lines:
            lines.append("Session timeline for failed replay steps:")
        errors = "; ".join(str(error) for error in step.get("errors", []))
        lines.append(
            f"  step={step.get('step')} http={step.get('status')} "
            f"durationMs={_display(step.get('durationMs'))} "
            f"javaSessionId={_display(step.get('javaSessionId'))} "
            f"pythonSessionId={_display(step.get('pythonSessionId'))} "
            f"stage={_display(step.get('stage'))} errors={errors}"
        )
        for item in step.get("timeline", []):
            lines.append(
                f"    event={_display(item.get('event'))} "
                f"durationMs={_display(item.get('durationMs'))} "
                f"javaSessionId={_display(item.get('javaSessionId'))} "
                f"pythonSessionId={_display(item.get('pythonSessionId'))} "
                f"stage={_display(item.get('stage'))}"
            )
    return "\n".join(lines)


def _json_request(url: str, payload: dict[str, Any], headers: dict[str, str], timeout: int) -> tuple[int, str, str]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            content_type = response.headers.get("content-type", "")
            return response.status, content_type, body
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        return exc.code, exc.headers.get("content-type", ""), body


def login(gateway_base_url: str, username: str, password: str, timeout: int) -> str:
    url = f"{gateway_base_url.rstrip('/')}/api/v1/auth/login"
    payload = {"username": username, "password": password}
    status, _, body = _json_request(
        url,
        payload,
        {"Content-Type": "application/json"},
        timeout,
    )
    if status >= 400:
        raise ReplayError(f"login failed with HTTP {status}: {body[:300]}")
    try:
        obj = json.loads(body)
    except json.JSONDecodeError as exc:
        raise ReplayError(f"login response is not JSON: {body[:300]}") from exc
    token = ((obj.get("data") or {}) if isinstance(obj, dict) else {}).get("accessToken")
    if not token:
        raise ReplayError("login response did not include data.accessToken")
    return str(token)


def build_chat_payload(step: TraceStep, session_id: str | None) -> dict[str, Any]:
    return {
        "sessionId": session_id,
        "message": step.message,
        "resumeId": step.resume_id,
        "jobId": step.job_id,
        "resumeContent": step.resume_content,
        "jobRequirements": step.job_requirements,
        "candidateName": step.candidate_name,
    }


def build_resume_payload(session_id: str | None) -> dict[str, Any]:
    return {"session_id": session_id, "sessionId": session_id}


def replay_trace(
    steps: list[TraceStep],
    gateway_base_url: str,
    chat_path: str,
    resume_path: str,
    token: str | None,
    timeout: int,
) -> dict[str, Any]:
    normalized_path = chat_path if chat_path.startswith("/") else f"/{chat_path}"
    normalized_resume_path = resume_path if resume_path.startswith("/") else f"/{resume_path}"
    chat_endpoint = f"{gateway_base_url.rstrip('/')}{normalized_path}"
    headers = {
        "Accept": "text/event-stream",
        "Content-Type": "application/json",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    previous_session_id: str | None = None
    results: list[dict[str, Any]] = []
    failed = False

    for step in steps:
        if step.action not in {"chat", "resume"}:
            raise ReplayError(f"step {step.step}: unsupported action: {step.action}")
        session_id = previous_session_id if step.session_ref == "previous" else step.session_id
        if step.action == "resume":
            if not session_id:
                raise ReplayError(f"step {step.step}: resume action requires sessionId or sessionRef")
            path = normalized_resume_path.replace("{sessionId}", session_id)
            endpoint = f"{gateway_base_url.rstrip('/')}{path}"
            payload = {} if "{sessionId}" in normalized_resume_path else build_resume_payload(session_id)
        else:
            endpoint = chat_endpoint
            payload = build_chat_payload(step, session_id)
        started = time.monotonic()
        status, content_type, body = _json_request(endpoint, payload, headers, timeout)
        duration_ms = int((time.monotonic() - started) * 1000)
        events = parse_sse_events(body)
        errors = []
        if status != 200:
            errors.append(f"expected HTTP 200 but got {status}")
        if "text/event-stream" not in content_type.lower():
            errors.append(f"expected text/event-stream content type but got {content_type or '<missing>'}")
        errors.extend(validate_step_result(step, events))
        next_session_id = extract_session_id(events)
        python_session_id = next_session_id or session_id
        java_session_id = extract_java_session_id(events)
        timeline = build_session_timeline(step, events, duration_ms)
        if next_session_id:
            previous_session_id = next_session_id
        failed = failed or bool(errors)

        results.append(
            {
                "step": step.step,
                "action": step.action,
                "status": status,
                "durationMs": duration_ms,
                "contentType": content_type,
                "events": [event.name for event in events],
                "requestedSessionId": session_id,
                "sessionId": previous_session_id,
                "javaSessionId": java_session_id,
                "pythonSessionId": python_session_id,
                "stage": extract_stage(events),
                "timeline": timeline,
                "errors": errors,
                "rawPreview": body[:1000],
            }
        )

    return {
        "ok": not failed,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "gatewayBaseUrl": gateway_base_url,
        "chatPath": normalized_path,
        "resumePath": normalized_resume_path,
        "steps": results,
    }


def write_report(report: dict[str, Any], report_dir: Path, trace_path: Path) -> Path:
    report_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    output = report_dir / f"{trace_path.stem}-{timestamp}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return output


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Replay an interview JSONL trace against SSE chat.")
    parser.add_argument("trace", type=Path, help="Path to a JSONL trace file")
    parser.add_argument("--gateway-base-url", default=os.getenv("GATEWAY_BASE_URL", "http://localhost:9000"))
    parser.add_argument("--chat-path", default=os.getenv("REPLAY_CHAT_PATH", "/api/v1/interviews/chat"))
    parser.add_argument("--resume-path", default=os.getenv("REPLAY_RESUME_PATH", "/api/v1/interviews/{sessionId}/resume"))
    parser.add_argument("--username", default=os.getenv("SMOKE_USERNAME", "admin"))
    parser.add_argument("--password", default=os.getenv("SMOKE_PASSWORD", "admin123"))
    parser.add_argument("--access-token", default=os.getenv("REPLAY_ACCESS_TOKEN"))
    parser.add_argument("--no-login", action="store_true", help="Do not login; send no Authorization header unless token is set")
    parser.add_argument("--timeout", type=int, default=int(os.getenv("SSE_MAX_TIME", "45")))
    parser.add_argument("--report-dir", type=Path, default=DEFAULT_REPORT_DIR)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        steps = load_trace(args.trace)
        token = args.access_token
        if token is None and not args.no_login:
            token = login(args.gateway_base_url, args.username, args.password, args.timeout)
        report = replay_trace(steps, args.gateway_base_url, args.chat_path, args.resume_path, token, args.timeout)
        report_path = write_report(report, args.report_dir, args.trace)
        print(f"Replay report: {report_path}")
        for step in report["steps"]:
            status = "PASS" if not step["errors"] else "FAIL"
            print(
                f"[{status}] step={step['step']} http={step['status']} "
                f"stage={step['stage']} events={','.join(step['events'])}"
            )
            for error in step["errors"]:
                print(f"  - {error}")
        failure_timeline = format_failure_timeline(report)
        if failure_timeline:
            print(failure_timeline)
        return 0 if report["ok"] else 1
    except ReplayError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
