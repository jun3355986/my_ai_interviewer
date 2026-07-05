import importlib.util
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[3]
REPLAY_MODULE = ROOT_DIR / "tests" / "scripts" / "interview_replay.py"
STUB_MODULE = ROOT_DIR / "tests" / "stubs" / "python-ai" / "app.py"


def load_replay_module():
    spec = importlib.util.spec_from_file_location("interview_replay", REPLAY_MODULE)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules["interview_replay"] = module
    spec.loader.exec_module(module)
    return module


def load_stub_module():
    spec = importlib.util.spec_from_file_location("python_ai_stub", STUB_MODULE)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules["python_ai_stub"] = module
    spec.loader.exec_module(module)
    return module


def test_parse_sse_events_preserves_order_and_payloads():
    replay = load_replay_module()
    body = "\n".join(
        [
            "event: status",
            'data: {"session_id":"py-1","stage":"opening"}',
            "",
            "event: chunk",
            'data: {"content":"hello"}',
            "",
            "event: done",
            'data: {"stage":"self_introduction","is_interview_complete":false}',
            "",
        ]
    )

    events = replay.parse_sse_events(body)

    assert [event.name for event in events] == ["status", "chunk", "done"]
    assert events[0].data["session_id"] == "py-1"
    assert events[2].data["stage"] == "self_introduction"


def test_load_trace_accepts_jsonl_steps_and_rejects_empty_files(tmp_path):
    replay = load_replay_module()
    trace_file = tmp_path / "trace.jsonl"
    trace_file.write_text(
        '{"step":1,"action":"chat","message":"start","expectEvents":["status","done"]}\n'
        '\n'
        '{"step":2,"action":"chat","sessionRef":"previous","message":"continue"}\n',
        encoding="utf-8",
    )

    steps = replay.load_trace(trace_file)

    assert [step.step for step in steps] == [1, 2]
    assert steps[0].expect_events == ["status", "done"]

    empty_trace = tmp_path / "empty.jsonl"
    empty_trace.write_text("\n", encoding="utf-8")

    try:
        replay.load_trace(empty_trace)
    except replay.ReplayError as exc:
        assert "empty" in str(exc).lower()
    else:
        raise AssertionError("empty trace should be rejected")


def test_validate_step_result_reports_missing_event_and_stage():
    replay = load_replay_module()
    step = replay.TraceStep(
        step=3,
        action="chat",
        message="answer",
        expect_events=["status", "result", "done"],
        expect_stage="technical_qna",
    )
    events = [
        replay.SseEvent("status", {"session_id": "py-1", "stage": "project_qna"}, ""),
        replay.SseEvent("done", {"stage": "project_qna"}, ""),
    ]

    errors = replay.validate_step_result(step, events)

    assert "missing expected event: result" in errors
    assert "expected stage technical_qna but got project_qna" in errors


def test_python_ai_stub_chat_returns_project_sse_events():
    from fastapi.testclient import TestClient

    stub = load_stub_module()
    response = TestClient(stub.app).post(
        "/interview/chat",
        json={
            "session_id": None,
            "message": "start",
            "java_session_id": "java-1",
            "resume_content": "Java backend resume",
        },
    )

    assert response.status_code == 200
    assert "text/event-stream" in response.headers["content-type"]
    events = load_replay_module().parse_sse_events(response.text)
    assert [event.name for event in events] == ["status", "chunk", "result", "done"]
    assert events[0].data["session_id"] == "stub-java-1"
    assert events[-1].data["stage"] == "opening"

    followup = TestClient(stub.app).post(
        "/interview/chat",
        json={
            "sessionId": "stub-java-1",
            "message": "continue",
        },
    )

    followup_events = load_replay_module().parse_sse_events(followup.text)
    assert [event.name for event in followup_events] == ["status", "question", "chunk", "result", "done"]
    assert followup_events[-1].data["stage"] == "self_introduction"
