from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path


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


def _default_repo_root() -> Path:
    return Path(__file__).resolve().parents[3]


@dataclass(frozen=True)
class AgentRuntimeConfig:
    langsmith_tracing: bool = False
    langsmith_project: str = "ai-interviewer"
    langsmith_capture_raw_payloads: bool = False
    langsmith_dataset_name: str = "ai-interviewer-replay-traces"
    manual_flow_recorder_enabled: bool = False
    manual_flow_recorder_output_dir: Path = _default_repo_root() / "tests" / "reports" / "manual-traces"
    manual_flow_recorder_capture_raw_payloads: bool = False
    manual_flow_recorder_max_raw_chars: int = 20000
    langgraph_agent_run_enabled: bool = False
    langgraph_checkpoint_db_path: Path = _default_repo_root() / "ai_interviewer" / "storage" / "agent_checkpoints.sqlite3"


def load_agent_runtime_config() -> AgentRuntimeConfig:
    return AgentRuntimeConfig(
        langsmith_tracing=_env_bool("LANGSMITH_TRACING", False),
        langsmith_project=os.getenv("LANGSMITH_PROJECT", "ai-interviewer").strip() or "ai-interviewer",
        langsmith_capture_raw_payloads=_env_bool("LANGSMITH_CAPTURE_RAW_PAYLOADS", False),
        langsmith_dataset_name=(
            os.getenv("LANGSMITH_DATASET_NAME", "ai-interviewer-replay-traces").strip()
            or "ai-interviewer-replay-traces"
        ),
        manual_flow_recorder_enabled=_env_bool("MANUAL_FLOW_RECORDER_ENABLED", False),
        manual_flow_recorder_output_dir=Path(
            os.getenv(
                "MANUAL_FLOW_RECORDER_OUTPUT_DIR",
                str(_default_repo_root() / "tests" / "reports" / "manual-traces"),
            )
        ),
        manual_flow_recorder_capture_raw_payloads=_env_bool(
            "MANUAL_FLOW_RECORDER_CAPTURE_RAW_PAYLOADS",
            False,
        ),
        manual_flow_recorder_max_raw_chars=_env_int("MANUAL_FLOW_RECORDER_MAX_RAW_CHARS", 20000),
        langgraph_agent_run_enabled=_env_bool("LANGGRAPH_AGENT_RUN_ENABLED", False),
        langgraph_checkpoint_db_path=Path(
            os.getenv(
                "LANGGRAPH_CHECKPOINT_DB_PATH",
                str(_default_repo_root() / "ai_interviewer" / "storage" / "agent_checkpoints.sqlite3"),
            )
        ),
    )
