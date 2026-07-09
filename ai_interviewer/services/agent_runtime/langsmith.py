from __future__ import annotations

from contextlib import contextmanager, nullcontext
import logging
import sys
from typing import Any, Iterator

from services.agent_runtime.config import AgentRuntimeConfig, load_agent_runtime_config


logger = logging.getLogger(__name__)


def build_langsmith_metadata(
    *,
    request_id: str | None = None,
    user_id: int | None = None,
    username: str | None = None,
    session_id: str | None = None,
    python_session_id: str | None = None,
    agent_run_id: str | None = None,
    entrypoint: str = "interview_chat",
    business_type: str = "interview",
    metadata: dict[str, Any] | None = None,
    capture_raw_payloads: bool = False,
) -> dict[str, Any]:
    safe_metadata: dict[str, Any] = {
        "requestId": request_id,
        "userId": user_id,
        "username": username,
        "interviewSessionId": session_id,
        "pythonSessionId": python_session_id,
        "agentRunId": agent_run_id,
        "entrypoint": entrypoint,
        "businessType": business_type,
        "stage": (metadata or {}).get("stage"),
    }
    for key, value in (metadata or {}).items():
        if capture_raw_payloads or key not in {
            "resume_content",
            "resumeContent",
            "job_requirements",
            "jobRequirements",
            "candidate_answer",
            "message",
            "prompt",
            "response",
        }:
            safe_metadata[key] = value
    return {key: value for key, value in safe_metadata.items() if value is not None}


@contextmanager
def langsmith_trace(
    *,
    name: str,
    metadata: dict[str, Any],
    config: AgentRuntimeConfig | None = None,
) -> Iterator[None]:
    resolved_config = config or load_agent_runtime_config()
    if not resolved_config.langsmith_tracing:
        with nullcontext():
            yield
        return

    try:
        from langsmith.run_helpers import trace
    except Exception:
        logger.exception("LangSmith tracing enabled but langsmith import failed")
        with nullcontext():
            yield
        return

    manager = trace(
        name=name,
        run_type="chain",
        project_name=resolved_config.langsmith_project,
        metadata=metadata,
        inputs={} if not resolved_config.langsmith_capture_raw_payloads else {"metadata": metadata},
    )
    try:
        manager.__enter__()
    except Exception:
        logger.exception("LangSmith trace start failed")
        with nullcontext():
            yield
        return

    try:
        yield
    except BaseException:
        exc_info = sys.exc_info()
        try:
            manager.__exit__(*exc_info)
        except Exception:
            logger.exception("LangSmith trace finish failed")
        raise
    else:
        try:
            manager.__exit__(None, None, None)
        except Exception:
            logger.exception("LangSmith trace finish failed")
