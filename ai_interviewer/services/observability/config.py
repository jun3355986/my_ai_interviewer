from dataclasses import dataclass
import os


def _env_bool(name: str, default: bool) -> bool:
    raw_value = os.getenv(name)
    if raw_value is None or raw_value == "":
        return default
    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int) -> int:
    raw_value = os.getenv(name)
    if raw_value is None or raw_value == "":
        return default
    try:
        return int(raw_value)
    except ValueError:
        return default


@dataclass(frozen=True)
class ObservabilityConfig:
    enabled: bool = True
    db_url: str = ""
    write_timeout_ms: int = 300
    store_raw_payload: bool = True
    max_raw_chars: int = 200000


def load_observability_config() -> ObservabilityConfig:
    return ObservabilityConfig(
        enabled=_env_bool("AI_OBSERVABILITY_ENABLED", True),
        db_url=os.getenv("AI_OBSERVABILITY_DB_URL", "").strip(),
        write_timeout_ms=_env_int("AI_OBSERVABILITY_WRITE_TIMEOUT_MS", 300),
        store_raw_payload=_env_bool("AI_OBSERVABILITY_STORE_RAW_PAYLOAD", True),
        max_raw_chars=_env_int("AI_OBSERVABILITY_MAX_RAW_CHARS", 200000),
    )
