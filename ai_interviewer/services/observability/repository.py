from __future__ import annotations

from datetime import datetime, timezone
import json
import logging
import math
import uuid
from functools import lru_cache
from typing import Any

from sqlalchemy import create_engine, text

from services.observability.config import ObservabilityConfig, load_observability_config


logger = logging.getLogger(__name__)
_warned_no_db_url = False


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _json_dumps(value: dict[str, Any] | None) -> str:
    return json.dumps(value or {}, ensure_ascii=False)


def _duration_ms(started_at: datetime, ended_at: datetime) -> int:
    return max(0, int((ended_at - started_at).total_seconds() * 1000))


class NoopObservabilityRepository:
    def create_trace(self, **kwargs: Any) -> str:
        return str(kwargs.get("trace_id") or uuid.uuid4())

    def finish_trace(self, **kwargs: Any) -> None:
        return None

    def create_step(self, **kwargs: Any) -> str:
        return str(kwargs.get("step_id") or uuid.uuid4())

    def finish_step(self, **kwargs: Any) -> None:
        return None

    def record_llm_call(self, **kwargs: Any) -> str:
        return str(kwargs.get("call_id") or uuid.uuid4())


class SqlAlchemyObservabilityRepository:
    def __init__(self, engine: Any, config: ObservabilityConfig) -> None:
        self.engine = engine
        self.config = config

    def _execute(self, statement: str, params: dict[str, Any]) -> None:
        with self.engine.begin() as connection:
            connection.execute(text(statement), params)

    def _run_best_effort(self, statement: str, params: dict[str, Any]) -> None:
        try:
            self._execute(statement, params)
        except Exception:
            logger.exception("observability write failed")

    def create_trace(
        self,
        *,
        trace_id: str | None = None,
        request_id: str | None = None,
        user_id: int | None = None,
        username: str | None = None,
        session_id: str | None = None,
        python_session_id: str | None = None,
        business_type: str = "interview",
        entrypoint: str | None = None,
        metadata: dict[str, Any] | None = None,
        started_at: datetime | None = None,
        status: str = "RUNNING",
    ) -> str:
        resolved_trace_id = str(trace_id or uuid.uuid4())
        try:
            self._run_best_effort(
                """
                INSERT INTO t_ai_trace (
                    id, request_id, user_id, username, session_id, python_session_id,
                    business_type, entrypoint, status, started_at, metadata
                )
                VALUES (
                    :id, :request_id, :user_id, :username, :session_id, :python_session_id,
                    :business_type, :entrypoint, :status, :started_at,
                    CAST(:metadata AS JSONB)
                )
                """,
                {
                    "id": resolved_trace_id,
                    "request_id": request_id,
                    "user_id": user_id,
                    "username": username,
                    "session_id": session_id,
                    "python_session_id": python_session_id,
                    "business_type": business_type,
                    "entrypoint": entrypoint,
                    "status": status,
                    "started_at": started_at or _utcnow(),
                    "metadata": _json_dumps(metadata),
                },
            )
        except Exception:
            logger.exception("observability write failed")
        return resolved_trace_id

    def finish_trace(
        self,
        *,
        trace_id: str,
        status: str,
        error_code: str | None = None,
        error_message: str | None = None,
        ended_at: datetime | None = None,
        duration_ms: int | None = None,
    ) -> None:
        resolved_ended_at = ended_at or _utcnow()
        self._run_best_effort(
            """
            UPDATE t_ai_trace
            SET status = :status,
                error_code = :error_code,
                error_message = :error_message,
                ended_at = :ended_at,
                duration_ms = COALESCE(
                    :duration_ms,
                    EXTRACT(EPOCH FROM (:ended_at - started_at)) * 1000
                )
            WHERE id = :id
            """,
            {
                "id": trace_id,
                "status": status,
                "error_code": error_code,
                "error_message": error_message,
                "ended_at": resolved_ended_at,
                "duration_ms": duration_ms,
            },
        )

    def create_step(
        self,
        *,
        trace_id: str,
        step_id: str | None = None,
        step_order: int = 0,
        step_type: str = "llm",
        step_name: str,
        metadata: dict[str, Any] | None = None,
        started_at: datetime | None = None,
        status: str = "RUNNING",
    ) -> str:
        resolved_step_id = str(step_id or uuid.uuid4())
        try:
            self._run_best_effort(
                """
                INSERT INTO t_ai_trace_step (
                    id, trace_id, step_order, step_type, step_name, status, started_at,
                    metadata
                )
                VALUES (
                    :id, :trace_id, :step_order, :step_type, :step_name, :status,
                    :started_at, CAST(:metadata AS JSONB)
                )
                """,
                {
                    "id": resolved_step_id,
                    "trace_id": trace_id,
                    "step_order": step_order,
                    "step_type": step_type,
                    "step_name": step_name,
                    "status": status,
                    "started_at": started_at or _utcnow(),
                    "metadata": _json_dumps(metadata),
                },
            )
        except Exception:
            logger.exception("observability write failed")
        return resolved_step_id

    def finish_step(
        self,
        *,
        step_id: str,
        status: str,
        error_message: str | None = None,
        ended_at: datetime | None = None,
        duration_ms: int | None = None,
    ) -> None:
        resolved_ended_at = ended_at or _utcnow()
        self._run_best_effort(
            """
            UPDATE t_ai_trace_step
            SET status = :status,
                error_message = :error_message,
                ended_at = :ended_at,
                duration_ms = COALESCE(
                    :duration_ms,
                    EXTRACT(EPOCH FROM (:ended_at - started_at)) * 1000
                )
            WHERE id = :id
            """,
            {
                "id": step_id,
                "status": status,
                "error_message": error_message,
                "ended_at": resolved_ended_at,
                "duration_ms": duration_ms,
            },
        )

    def record_llm_call(
        self,
        *,
        trace_id: str | None = None,
        step_id: str | None = None,
        call_id: str | None = None,
        call_type: str,
        provider: str,
        model: str,
        fallback_used: bool = False,
        fallback_from_model: str | None = None,
        status: str,
        prompt_tokens: int | None = None,
        completion_tokens: int | None = None,
        total_tokens: int | None = None,
        token_source: str = "provider",
        prompt_cache_hit_tokens: int | None = None,
        prompt_cache_miss_tokens: int | None = None,
        prompt_cache_hit_rate: float | None = None,
        cache_reported_by_provider: bool = False,
        latency_ms: int | None = None,
        prompt_text: str | None = None,
        response_text: str | None = None,
        raw_usage_json: dict[str, Any] | None = None,
        metadata: dict[str, Any] | None = None,
        error_message: str | None = None,
        started_at: datetime | None = None,
        ended_at: datetime | None = None,
    ) -> str:
        resolved_call_id = str(call_id or uuid.uuid4())
        try:
            self._run_best_effort(
                """
                INSERT INTO t_ai_llm_call (
                    id, trace_id, step_id, call_type, provider, model, fallback_used,
                    fallback_from_model, status, prompt_tokens, completion_tokens,
                    total_tokens, token_source, prompt_cache_hit_tokens,
                    prompt_cache_miss_tokens, prompt_cache_hit_rate,
                    cache_reported_by_provider, latency_ms, prompt_text, response_text,
                    raw_usage_json, metadata, error_message, started_at, ended_at
                )
                VALUES (
                    :id, :trace_id, :step_id, :call_type, :provider, :model,
                    :fallback_used, :fallback_from_model, :status, :prompt_tokens,
                    :completion_tokens, :total_tokens, :token_source,
                    :prompt_cache_hit_tokens, :prompt_cache_miss_tokens,
                    :prompt_cache_hit_rate, :cache_reported_by_provider, :latency_ms,
                    :prompt_text, :response_text, CAST(:raw_usage_json AS JSONB),
                    CAST(:metadata AS JSONB), :error_message, :started_at, :ended_at
                )
                """,
                {
                    "id": resolved_call_id,
                    "trace_id": trace_id,
                    "step_id": step_id,
                    "call_type": call_type,
                    "provider": provider,
                    "model": model,
                    "fallback_used": fallback_used,
                    "fallback_from_model": fallback_from_model,
                    "status": status,
                    "prompt_tokens": prompt_tokens,
                    "completion_tokens": completion_tokens,
                    "total_tokens": total_tokens,
                    "token_source": token_source,
                    "prompt_cache_hit_tokens": prompt_cache_hit_tokens,
                    "prompt_cache_miss_tokens": prompt_cache_miss_tokens,
                    "prompt_cache_hit_rate": prompt_cache_hit_rate,
                    "cache_reported_by_provider": cache_reported_by_provider,
                    "latency_ms": latency_ms,
                    "prompt_text": prompt_text,
                    "response_text": response_text,
                    "raw_usage_json": _json_dumps(raw_usage_json),
                    "metadata": _json_dumps(metadata),
                    "error_message": error_message,
                    "started_at": started_at or _utcnow(),
                    "ended_at": ended_at,
                },
            )
        except Exception:
            logger.exception("observability write failed")
        return resolved_call_id


def _build_repository(config: ObservabilityConfig):
    global _warned_no_db_url
    if not config.enabled:
        return NoopObservabilityRepository()
    if not config.db_url:
        if not _warned_no_db_url:
            logger.warning(
                "AI observability enabled but AI_OBSERVABILITY_DB_URL is empty; "
                "using no-op repository"
            )
            _warned_no_db_url = True
        return NoopObservabilityRepository()

    timeout_seconds = max(1, math.ceil(config.write_timeout_ms / 1000))
    try:
        engine = create_engine(
            config.db_url,
            pool_pre_ping=True,
            connect_args={
                "connect_timeout": timeout_seconds,
                "options": f"-c statement_timeout={config.write_timeout_ms}",
            },
        )
    except Exception:
        logger.exception("observability write failed")
        return NoopObservabilityRepository()

    return SqlAlchemyObservabilityRepository(engine=engine, config=config)


@lru_cache(maxsize=1)
def get_observability_repository():
    return _build_repository(load_observability_config())
