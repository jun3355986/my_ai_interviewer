from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class DeterministicEvaluationResult:
    key: str
    passed: bool
    score: float
    message: str


def replay_trace_to_dataset_examples(trace_path: str | Path) -> list[dict[str, Any]]:
    path = Path(trace_path)
    examples: list[dict[str, Any]] = []
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        item = json.loads(line)
        examples.append(
            {
                "id": f"{path.stem}:{line_number}",
                "inputs": {
                    "action": item.get("action", "chat"),
                    "message": item.get("message"),
                    "sessionId": item.get("sessionId"),
                    "sessionRef": item.get("sessionRef"),
                },
                "outputs": {
                    "expectEvents": item.get("expectEvents", []),
                    "expectStage": item.get("expectStage"),
                },
                "metadata": {
                    "tracePath": str(path),
                    "step": item.get("step"),
                },
            }
        )
    return examples


def evaluate_replay_report(report: dict[str, Any]) -> list[DeterministicEvaluationResult]:
    results: list[DeterministicEvaluationResult] = []
    steps = report.get("steps", [])
    if not isinstance(steps, list):
        return [
            DeterministicEvaluationResult(
                key="report_shape",
                passed=False,
                score=0.0,
                message="report.steps must be a list",
            )
        ]

    for step in steps:
        step_number = step.get("step", "?")
        errors = step.get("errors") or []
        status = int(step.get("status") or 0)
        events = step.get("events") or []
        stage = step.get("stage")
        duration_ms = step.get("durationMs")
        passed = status == 200 and not errors and "error" not in events
        results.append(
            DeterministicEvaluationResult(
                key=f"step_{step_number}_http_and_errors",
                passed=passed,
                score=1.0 if passed else 0.0,
                message="ok" if passed else f"status={status} errors={errors}",
            )
        )
        stage_present = bool(stage)
        results.append(
            DeterministicEvaluationResult(
                key=f"step_{step_number}_stage_present",
                passed=stage_present,
                score=1.0 if stage_present else 0.0,
                message=str(stage) if stage_present else "missing stage",
            )
        )
        events_present = bool(events)
        results.append(
            DeterministicEvaluationResult(
                key=f"step_{step_number}_events_present",
                passed=events_present,
                score=1.0 if events_present else 0.0,
                message=",".join(str(event) for event in events) if events_present else "missing events",
            )
        )
        if duration_ms is not None:
            within_limit = int(duration_ms) <= 45000
            results.append(
                DeterministicEvaluationResult(
                    key=f"step_{step_number}_duration_limit",
                    passed=within_limit,
                    score=1.0 if within_limit else 0.0,
                    message=f"durationMs={duration_ms}",
                )
            )
    return results
