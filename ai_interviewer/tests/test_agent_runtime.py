import json
import os
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.agent_runtime.config import AgentRuntimeConfig, load_agent_runtime_config
from services.agent_runtime.evaluation import (
    evaluate_replay_report,
    replay_trace_to_dataset_examples,
)
from services.agent_runtime.langgraph_wrapper import checkpoint_agent_run
from services.agent_runtime.langsmith import build_langsmith_metadata
from services.agent_runtime.manual_recorder import ManualFlowRecorder


def test_runtime_config_defaults_keep_external_integrations_disabled(monkeypatch):
    for key in list(os.environ):
        if key.startswith(("LANGSMITH_", "LANGGRAPH_", "MANUAL_FLOW_RECORDER_")):
            monkeypatch.delenv(key, raising=False)

    config = load_agent_runtime_config()

    assert config.langsmith_tracing is False
    assert config.langsmith_capture_raw_payloads is False
    assert config.manual_flow_recorder_enabled is False
    assert config.langgraph_agent_run_enabled is False


def test_langsmith_metadata_excludes_raw_payloads_by_default():
    metadata = build_langsmith_metadata(
        request_id="req-1",
        session_id="java-session-1",
        python_session_id="python-session-1",
        agent_run_id="agent-run-1",
        entrypoint="interview_chat",
        metadata={
            "stage": "opening",
            "message": "candidate answer",
            "prompt": "full prompt",
            "safe_key": "safe value",
        },
    )

    assert metadata["requestId"] == "req-1"
    assert metadata["interviewSessionId"] == "java-session-1"
    assert metadata["pythonSessionId"] == "python-session-1"
    assert metadata["agentRunId"] == "agent-run-1"
    assert metadata["stage"] == "opening"
    assert metadata["safe_key"] == "safe value"
    assert "message" not in metadata
    assert "prompt" not in metadata


def test_manual_flow_recorder_writes_redacted_candidate_replay_trace(tmp_path):
    config = AgentRuntimeConfig(
        manual_flow_recorder_enabled=True,
        manual_flow_recorder_output_dir=tmp_path,
        manual_flow_recorder_capture_raw_payloads=False,
    )
    recorder = ManualFlowRecorder.for_request(
        entrypoint="interview_chat",
        request_payload={
            "session_id": None,
            "message": "raw answer",
            "resume_content": "raw resume",
            "job_requirements": "raw job",
            "candidate_name": "raw name",
        },
        config=config,
    )

    assert recorder is not None
    recorder.observe_sse_chunk('event: status\ndata: {"session_id":"py-1","stage":"opening"}\n\n')
    recorder.observe_sse_chunk('event: done\ndata: {"stage":"opening"}\n\n')
    trace_path = recorder.finish()

    candidate_step = json.loads(trace_path.read_text(encoding="utf-8").strip())
    assert candidate_step["action"] == "chat"
    assert candidate_step["expectEvents"] == ["status", "done"]
    assert candidate_step["expectStage"] == "opening"
    assert candidate_step["message"] == "<redacted>"
    assert candidate_step["resumeContent"] == "<redacted>"
    assert trace_path.with_suffix(".report.json").exists()


def test_replay_trace_to_langsmith_examples_and_deterministic_eval(tmp_path):
    trace_path = tmp_path / "trace.jsonl"
    trace_path.write_text(
        json.dumps(
            {
                "step": 1,
                "action": "chat",
                "message": "start",
                "expectEvents": ["status", "done"],
                "expectStage": "opening",
            }
        )
        + "\n",
        encoding="utf-8",
    )

    examples = replay_trace_to_dataset_examples(trace_path)
    assert examples[0]["inputs"]["message"] == "start"
    assert examples[0]["outputs"]["expectEvents"] == ["status", "done"]

    results = evaluate_replay_report(
        {
            "steps": [
                {
                    "step": 1,
                    "status": 200,
                    "events": ["status", "done"],
                    "stage": "opening",
                    "durationMs": 120,
                    "errors": [],
                }
            ]
        }
    )
    assert results
    assert all(result.passed for result in results)


def test_langgraph_checkpoint_uses_agent_run_id_as_thread_id(tmp_path):
    checkpoint_path = tmp_path / "checkpoints.sqlite3"
    config = AgentRuntimeConfig(
        langgraph_agent_run_enabled=True,
        langgraph_checkpoint_db_path=checkpoint_path,
    )

    assert checkpoint_agent_run(
        agent_run_id="agent-run-test",
        entrypoint="interview_chat",
        metadata={"stage": "opening"},
        config=config,
    )

    with sqlite3.connect(checkpoint_path) as connection:
        rows = connection.execute(
            "SELECT thread_id FROM checkpoints WHERE thread_id = ?",
            ("agent-run-test",),
        ).fetchall()
    assert rows
