from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import logging
from time import perf_counter
from typing import Any

from collections.abc import Mapping

from services.observability.config import load_observability_config
from services.observability.context import current_trace_context
from services.observability.provider_usage import normalize_provider_usage
from services.observability.repository import get_observability_repository


logger = logging.getLogger(__name__)


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _duration_ms(start_time: float) -> int:
    return max(0, int((perf_counter() - start_time) * 1000))


@dataclass(frozen=True)
class ObservableLLMResponse:
    text: str
    call_id: str | None = None

    def __str__(self) -> str:
        return self.text


def _as_dict(value: Any) -> dict[str, Any]:
    if isinstance(value, Mapping):
        return dict(value)
    return {}


def _extract_usage(ai_message: Any) -> dict[str, Any] | None:
    response_metadata = _as_dict(getattr(ai_message, "response_metadata", None))
    token_usage = response_metadata.get("token_usage")
    if isinstance(token_usage, Mapping):
        return dict(token_usage)

    usage_metadata = _as_dict(getattr(ai_message, "usage_metadata", None))
    if not usage_metadata:
        return None

    raw_usage = dict(usage_metadata)
    if "prompt_tokens" not in raw_usage and "input_tokens" in usage_metadata:
        raw_usage["prompt_tokens"] = usage_metadata["input_tokens"]
    if "completion_tokens" not in raw_usage and "output_tokens" in usage_metadata:
        raw_usage["completion_tokens"] = usage_metadata["output_tokens"]
    if "total_tokens" not in raw_usage and "total_tokens" in usage_metadata:
        raw_usage["total_tokens"] = usage_metadata["total_tokens"]
    return raw_usage


def _message_text(ai_message: Any) -> str:
    if isinstance(ai_message, str):
        return ai_message
    content = getattr(ai_message, "content", ai_message)
    return content if isinstance(content, str) else str(content)


def _prompt_text(prompt_value: Any) -> str:
    to_string = getattr(prompt_value, "to_string", None)
    if callable(to_string):
        return to_string()
    return str(prompt_value)


def _infer_model(llm: Any, ai_message: Any, explicit_model: str | None) -> str:
    response_metadata = _as_dict(getattr(ai_message, "response_metadata", None))
    for key in ("model_name", "model", "model_id"):
        value = response_metadata.get(key)
        if value:
            return str(value)
    for key in ("model_name", "model", "deployment_name"):
        value = getattr(llm, key, None)
        if value:
            return str(value)
    return explicit_model or "unknown"


def _infer_declared_model(llm: Any, seen: set[int] | None = None) -> str | None:
    if llm is None:
        return None

    seen = seen or set()
    object_id = id(llm)
    if object_id in seen:
        return None
    seen.add(object_id)

    for key in ("model_name", "model", "model_id", "deployment_name"):
        value = getattr(llm, key, None)
        if value:
            return str(value)

    for key in ("runnable", "bound"):
        nested = getattr(llm, key, None)
        if nested is not None:
            nested_model = _infer_declared_model(nested, seen)
            if nested_model:
                return nested_model

    return None


def _truncate_payload(
    value: str | None,
    *,
    metadata: dict[str, Any],
    field_name: str,
) -> str | None:
    config = load_observability_config()
    if not config.store_raw_payload:
        return None
    if value is None:
        return None
    if len(value) <= config.max_raw_chars:
        return value

    truncated_fields = metadata.setdefault("raw_truncated_fields", [])
    truncated_fields.append(field_name)
    metadata["raw_truncated"] = True
    return value[: config.max_raw_chars]


def invoke_observable(
    *,
    prompt: Any,
    llm: Any,
    input_values: dict[str, Any],
    call_type: str,
    repository: Any | None = None,
    provider: str = "azure_openai",
    model: str | None = None,
) -> ObservableLLMResponse:
    prompt_value = prompt.invoke(input_values)
    prompt_text = _prompt_text(prompt_value)
    expected_primary_model = model or _infer_declared_model(llm)

    context = current_trace_context()
    resolved_repository = repository or (
        context.repository if context else get_observability_repository()
    )
    trace_id = context.trace_id if context else None
    owns_trace = False

    if trace_id is None and repository is None:
        trace_id = resolved_repository.create_trace(
            business_type="interview",
            entrypoint=call_type,
            metadata={"standalone_llm_call": True},
        )
        owns_trace = True

    started_at = _utcnow()
    start_time = perf_counter()
    step_id = None
    if trace_id and repository is None:
        step_id = resolved_repository.create_step(
            trace_id=trace_id,
            step_name=call_type,
            step_type="llm",
            metadata={"call_type": call_type},
            started_at=started_at,
        )

    try:
        ai_message = llm.invoke(prompt_value)
    except Exception as exc:
        ended_at = _utcnow()
        latency_ms = _duration_ms(start_time)
        metadata = {"error_type": type(exc).__name__}
        resolved_repository.record_llm_call(
            trace_id=trace_id,
            step_id=step_id,
            call_type=call_type,
            provider=provider,
            model=expected_primary_model or _infer_model(llm, None, None),
            status="ERROR",
            token_source="estimated",
            latency_ms=latency_ms,
            prompt_text=_truncate_payload(
                prompt_text,
                metadata=metadata,
                field_name="prompt_text",
            ),
            raw_usage_json={},
            metadata=metadata,
            error_message=str(exc),
            started_at=started_at,
            ended_at=ended_at,
        )
        if step_id:
            resolved_repository.finish_step(
                step_id=step_id,
                status="ERROR",
                error_message=str(exc),
                ended_at=ended_at,
                duration_ms=latency_ms,
            )
        if owns_trace and trace_id:
            resolved_repository.finish_trace(
                trace_id=trace_id,
                status="ERROR",
                error_code="LLM_ERROR",
                error_message=str(exc),
                ended_at=ended_at,
            )
        raise

    raw_usage = _extract_usage(ai_message)
    normalized_usage = normalize_provider_usage(provider, raw_usage)
    response_metadata = _as_dict(getattr(ai_message, "response_metadata", None))
    usage_metadata = _as_dict(getattr(ai_message, "usage_metadata", None))
    resolved_model = _infer_model(llm, ai_message, model)
    metadata = {
        "response_metadata": response_metadata,
        "usage_metadata": usage_metadata,
    }
    fallback_from_model = None
    fallback_used = False
    if (
        expected_primary_model
        and resolved_model != "unknown"
        and resolved_model != expected_primary_model
    ):
        fallback_used = True
        fallback_from_model = expected_primary_model

    try:
        text = _message_text(ai_message)
    except Exception as exc:
        ended_at = _utcnow()
        latency_ms = _duration_ms(start_time)
        error_metadata = dict(metadata)
        error_metadata["error_type"] = type(exc).__name__
        try:
            resolved_repository.record_llm_call(
                trace_id=trace_id,
                step_id=step_id,
                call_type=call_type,
                provider=provider,
                model=resolved_model,
                fallback_used=fallback_used,
                fallback_from_model=fallback_from_model,
                status="ERROR",
                prompt_tokens=normalized_usage.prompt_tokens,
                completion_tokens=normalized_usage.completion_tokens,
                total_tokens=normalized_usage.total_tokens,
                token_source=normalized_usage.token_source,
                prompt_cache_hit_tokens=normalized_usage.prompt_cache_hit_tokens,
                prompt_cache_miss_tokens=normalized_usage.prompt_cache_miss_tokens,
                prompt_cache_hit_rate=normalized_usage.prompt_cache_hit_rate,
                cache_reported_by_provider=normalized_usage.cache_reported_by_provider,
                latency_ms=latency_ms,
                prompt_text=_truncate_payload(
                    prompt_text,
                    metadata=error_metadata,
                    field_name="prompt_text",
                ),
                response_text=None,
                raw_usage_json=normalized_usage.raw_usage,
                metadata=error_metadata,
                error_message=str(exc),
                started_at=started_at,
                ended_at=ended_at,
            )
            if step_id:
                resolved_repository.finish_step(
                    step_id=step_id,
                    status="ERROR",
                    error_message=str(exc),
                    ended_at=ended_at,
                    duration_ms=latency_ms,
                )
            if owns_trace and trace_id:
                resolved_repository.finish_trace(
                    trace_id=trace_id,
                    status="ERROR",
                    error_code="LLM_ERROR",
                    error_message=str(exc),
                    ended_at=ended_at,
                )
        except Exception:
            logger.exception("observability write failed")
        raise

    ended_at = _utcnow()
    latency_ms = _duration_ms(start_time)
    call_id = resolved_repository.record_llm_call(
        trace_id=trace_id,
        step_id=step_id,
        call_type=call_type,
        provider=provider,
        model=resolved_model,
        fallback_used=fallback_used,
        fallback_from_model=fallback_from_model,
        status="SUCCESS",
        prompt_tokens=normalized_usage.prompt_tokens,
        completion_tokens=normalized_usage.completion_tokens,
        total_tokens=normalized_usage.total_tokens,
        token_source=normalized_usage.token_source,
        prompt_cache_hit_tokens=normalized_usage.prompt_cache_hit_tokens,
        prompt_cache_miss_tokens=normalized_usage.prompt_cache_miss_tokens,
        prompt_cache_hit_rate=normalized_usage.prompt_cache_hit_rate,
        cache_reported_by_provider=normalized_usage.cache_reported_by_provider,
        latency_ms=latency_ms,
        prompt_text=_truncate_payload(
            prompt_text,
            metadata=metadata,
            field_name="prompt_text",
        ),
        response_text=_truncate_payload(
            text,
            metadata=metadata,
            field_name="response_text",
        ),
        raw_usage_json=normalized_usage.raw_usage,
        metadata=metadata,
        started_at=started_at,
        ended_at=ended_at,
    )
    if step_id:
        resolved_repository.finish_step(
            step_id=step_id,
            status="SUCCESS",
            ended_at=ended_at,
            duration_ms=latency_ms,
        )
    if owns_trace and trace_id:
        resolved_repository.finish_trace(
            trace_id=trace_id,
            status="SUCCESS",
            ended_at=ended_at,
            duration_ms=latency_ms,
        )

    return ObservableLLMResponse(text=text, call_id=call_id)
