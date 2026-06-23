from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class NormalizedUsage:
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None
    token_source: str = "provider"
    prompt_cache_hit_tokens: int | None = None
    prompt_cache_miss_tokens: int | None = None
    prompt_cache_hit_rate: float | None = None
    cache_reported_by_provider: bool = False
    raw_usage: dict[str, Any] = field(default_factory=dict)
