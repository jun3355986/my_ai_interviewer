from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any

from services.agent_runtime.config import AgentRuntimeConfig, load_agent_runtime_config


RAW_REQUEST_FIELDS = {
    "message",
    "resumeContent",
    "resume_content",
    "jobRequirements",
    "job_requirements",
    "candidateName",
    "candidate_name",
}


def _utc_timestamp() -> str:
    return datetime.now(timezone.utc).isoformat()


def _event_name_from_chunk(chunk: str) -> str | None:
    for line in chunk.splitlines():
        if line.startswith("event:"):
            return line.split(":", 1)[1].strip() or None
    return None


def _event_payload_from_chunk(chunk: str) -> dict[str, Any]:
    data_lines = [
        line.split(":", 1)[1].strip()
        for line in chunk.splitlines()
        if line.startswith("data:")
    ]
    if not data_lines:
        return {}
    try:
        parsed = json.loads("\n".join(data_lines))
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {"value": parsed}


def _redact_payload(payload: dict[str, Any], config: AgentRuntimeConfig) -> dict[str, Any]:
    redacted: dict[str, Any] = {}
    for key, value in payload.items():
        if key in RAW_REQUEST_FIELDS and not config.manual_flow_recorder_capture_raw_payloads:
            redacted[key] = "<redacted>"
        elif isinstance(value, str) and len(value) > config.manual_flow_recorder_max_raw_chars:
            redacted[key] = value[: config.manual_flow_recorder_max_raw_chars]
        else:
            redacted[key] = value
    return redacted


@dataclass
class ManualFlowRecorder:
    entrypoint: str
    request_payload: dict[str, Any]
    config: AgentRuntimeConfig = field(default_factory=load_agent_runtime_config)
    events: list[str] = field(default_factory=list)
    latest_stage: str | None = None
    error_message: str | None = None
    started_at: str = field(default_factory=_utc_timestamp)

    @classmethod
    def for_request(
        cls,
        *,
        entrypoint: str,
        request_payload: dict[str, Any],
        config: AgentRuntimeConfig | None = None,
    ) -> "ManualFlowRecorder | None":
        resolved_config = config or load_agent_runtime_config()
        if not resolved_config.manual_flow_recorder_enabled:
            return None
        return cls(
            entrypoint=entrypoint,
            request_payload=_redact_payload(request_payload, resolved_config),
            config=resolved_config,
        )

    def observe_sse_chunk(self, chunk: str) -> None:
        event_name = _event_name_from_chunk(chunk)
        if event_name:
            self.events.append(event_name)
        payload = _event_payload_from_chunk(chunk)
        stage = payload.get("stage") or payload.get("next_stage")
        if stage:
            self.latest_stage = str(stage)
        if event_name == "error":
            self.error_message = str(payload.get("message") or payload.get("code") or "error")

    def candidate_trace_step(self) -> dict[str, Any]:
        step: dict[str, Any] = {
            "step": 1,
            "action": "chat" if self.entrypoint == "interview_chat" else "resume",
            "sessionId": self.request_payload.get("sessionId") or self.request_payload.get("session_id"),
            "expectEvents": sorted(set(self.events), key=self.events.index),
        }
        if self.latest_stage:
            step["expectStage"] = self.latest_stage
        field_map = {
            "message": "message",
            "resumeContent": "resumeContent",
            "resume_content": "resumeContent",
            "jobRequirements": "jobRequirements",
            "job_requirements": "jobRequirements",
            "candidateName": "candidateName",
            "candidate_name": "candidateName",
        }
        for source_key, target_key in field_map.items():
            value = self.request_payload.get(source_key)
            if value not in (None, ""):
                step[target_key] = value
        return step

    def finish(self) -> Path:
        output_dir = self.config.manual_flow_recorder_output_dir
        output_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        output_path = output_dir / f"{self.entrypoint}-{timestamp}.jsonl"
        report = {
            "recordedAt": _utc_timestamp(),
            "entrypoint": self.entrypoint,
            "startedAt": self.started_at,
            "status": "ERROR" if self.error_message else "SUCCESS",
            "errorMessage": self.error_message,
            "events": self.events,
            "candidateTrace": self.candidate_trace_step(),
        }
        output_path.write_text(
            json.dumps(report["candidateTrace"], ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        output_path.with_suffix(".report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        return output_path
