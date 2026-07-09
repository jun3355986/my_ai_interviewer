from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any
import uuid

from services.agent_runtime.langgraph_wrapper import langgraph_agent_run
from services.agent_runtime.langsmith import build_langsmith_metadata, langsmith_trace
from services.observability.repository import get_observability_repository


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _duration_ms(started_at: datetime, ended_at: datetime) -> int:
    return max(0, int((ended_at - started_at).total_seconds() * 1000))


@dataclass
class ObservabilityTraceContext:
    trace_id: str
    agent_run_id: str
    repository: Any
    started_at: datetime
    status: str = "SUCCESS"
    error_code: str | None = None
    error_message: str | None = None

    def mark_error(self, error_code: str, error_message: str) -> None:
        self.status = "ERROR"
        self.error_code = error_code
        self.error_message = error_message


_current_trace: ContextVar[ObservabilityTraceContext | None] = ContextVar(
    "ai_observability_trace",
    default=None,
)


def current_trace_context() -> ObservabilityTraceContext | None:
    return _current_trace.get()


@contextmanager
def observability_trace(
    *,
    request_id: str | None = None,
    agent_run_id: str | None = None,
    user_id: int | None = None,
    username: str | None = None,
    session_id: str | None = None,
    python_session_id: str | None = None,
    business_type: str = "interview",
    entrypoint: str = "interview_chat",
    metadata: dict[str, Any] | None = None,
):
    repository = get_observability_repository()
    started_at = _utcnow()
    resolved_agent_run_id = agent_run_id or str(uuid.uuid4())
    enriched_metadata = {
        **(metadata or {}),
        "agentRunId": resolved_agent_run_id,
    }
    trace_id = repository.create_trace(
        request_id=request_id,
        user_id=user_id,
        username=username,
        session_id=session_id,
        python_session_id=python_session_id,
        business_type=business_type,
        entrypoint=entrypoint,
        metadata=enriched_metadata,
        started_at=started_at,
    )
    context = ObservabilityTraceContext(
        trace_id=trace_id,
        agent_run_id=resolved_agent_run_id,
        repository=repository,
        started_at=started_at,
    )
    langsmith_metadata = build_langsmith_metadata(
        request_id=request_id,
        user_id=user_id,
        username=username,
        session_id=session_id,
        python_session_id=python_session_id,
        agent_run_id=resolved_agent_run_id,
        entrypoint=entrypoint,
        business_type=business_type,
        metadata=enriched_metadata,
    )
    token = _current_trace.set(context)
    try:
        with langsmith_trace(
            name=entrypoint,
            metadata=langsmith_metadata,
        ), langgraph_agent_run(
            agent_run_id=resolved_agent_run_id,
            entrypoint=entrypoint,
            metadata=langsmith_metadata,
        ):
            yield context
    except Exception as exc:
        context.mark_error("INTERNAL_ERROR", str(exc))
        raise
    finally:
        ended_at = _utcnow()
        repository.finish_trace(
            trace_id=trace_id,
            status=context.status,
            error_code=context.error_code,
            error_message=context.error_message,
            ended_at=ended_at,
            duration_ms=_duration_ms(started_at, ended_at),
        )
        _current_trace.reset(token)
