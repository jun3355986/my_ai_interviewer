from collections.abc import Mapping
from typing import Any

from services.observability.models import NormalizedUsage


def _int_token(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    return None


def normalize_provider_usage(
    provider: str,
    usage: Mapping[str, Any] | None,
    estimated_prompt_tokens: int | None = None,
    estimated_completion_tokens: int | None = None,
) -> NormalizedUsage:
    if usage is None:
        total_tokens = None
        if estimated_prompt_tokens is not None and estimated_completion_tokens is not None:
            total_tokens = estimated_prompt_tokens + estimated_completion_tokens

        return NormalizedUsage(
            prompt_tokens=estimated_prompt_tokens,
            completion_tokens=estimated_completion_tokens,
            total_tokens=total_tokens,
            token_source="estimated",
            raw_usage={},
        )

    raw_usage = dict(usage)
    prompt_tokens = _int_token(raw_usage.get("prompt_tokens"))
    completion_tokens = _int_token(raw_usage.get("completion_tokens"))
    total_tokens = _int_token(raw_usage.get("total_tokens"))
    cache_hit_tokens = None
    cache_miss_tokens = None

    if provider.lower() == "deepseek":
        cache_hit_tokens = _int_token(raw_usage.get("prompt_cache_hit_tokens"))
        cache_miss_tokens = _int_token(raw_usage.get("prompt_cache_miss_tokens"))
    else:
        prompt_details = raw_usage.get("prompt_tokens_details")
        if isinstance(prompt_details, Mapping):
            cached_tokens = _int_token(prompt_details.get("cached_tokens"))
            if cached_tokens is not None:
                cache_hit_tokens = cached_tokens
                if prompt_tokens is not None and cached_tokens <= prompt_tokens:
                    cache_miss_tokens = prompt_tokens - cached_tokens

    cache_reported_by_provider = (
        cache_hit_tokens is not None or cache_miss_tokens is not None
    )

    cache_hit_rate = None
    if cache_hit_tokens is not None and cache_miss_tokens is not None:
        if cache_hit_tokens >= 0 and cache_miss_tokens >= 0:
            cache_total = cache_hit_tokens + cache_miss_tokens
            if cache_total > 0:
                cache_hit_rate = cache_hit_tokens / cache_total

    return NormalizedUsage(
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        total_tokens=total_tokens,
        prompt_cache_hit_tokens=cache_hit_tokens,
        prompt_cache_miss_tokens=cache_miss_tokens,
        prompt_cache_hit_rate=cache_hit_rate,
        cache_reported_by_provider=cache_reported_by_provider,
        raw_usage=raw_usage,
    )
