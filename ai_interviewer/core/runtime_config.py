"""
进程内运行时配置：以环境变量为基线，管理端（/admin/runtime-config）可在线覆盖。

约束：
- 只允许白名单键，API key 一律不允许运行时修改（安全边界，仍走环境变量）。
- 覆盖即时生效于后续构建的 LLM / embeddings / 检索调用；不落盘，进程重启回退环境变量。
- 向量集合切换有语义风险（不同 embedding 空间不可互换），必须显式 confirm。
"""
from __future__ import annotations

import os
import threading
from typing import Any, Dict, List, Optional

_LOCK = threading.RLock()
_OVERRIDES: Dict[str, Any] = {}

# 可覆盖键白名单
KEY_CHAT_MODEL = "chat_model"
KEY_CHAT_FALLBACK_MODELS = "chat_fallback_models"
KEY_EMBEDDING_MODEL = "embedding_model"
KEY_EMBEDDING_DIMENSION = "embedding_dimension"
KEY_VECTOR_COLLECTION = "vector_collection"
KEY_RETRIEVAL_TOP_K = "retrieval_top_k"
KEY_RETRIEVAL_KEYWORD_FALLBACK = "retrieval_keyword_fallback"

EDITABLE_KEYS = {
    KEY_CHAT_MODEL,
    KEY_CHAT_FALLBACK_MODELS,
    KEY_EMBEDDING_MODEL,
    KEY_EMBEDDING_DIMENSION,
    KEY_VECTOR_COLLECTION,
    KEY_RETRIEVAL_TOP_K,
    KEY_RETRIEVAL_KEYWORD_FALLBACK,
}


class RuntimeConfigError(ValueError):
    """运行时配置校验失败。"""


def _env(name: str) -> Optional[str]:
    value = os.getenv(name)
    return value if value not in (None, "") else None


def default_chat_model() -> str:
    from core import model_provider

    return (
        _env(model_provider.OPENAI_COMPAT_CHAT_MODEL_ENV)
        or _env(model_provider.AZURE_OPENAI_CHAT_MODEL_ENV)
        or model_provider.DEFAULT_CHAT_MODEL
    )


def default_chat_fallback_models() -> List[str]:
    from core import model_provider

    raw = (
        _env(model_provider.OPENAI_COMPAT_FALLBACK_CHAT_MODELS_ENV)
        or _env(model_provider.AZURE_OPENAI_BACKUP_CHAT_MODEL_ENV)
        or ",".join(model_provider.DEFAULT_FALLBACK_CHAT_MODELS)
    )
    return [item.strip() for item in raw.split(",") if item.strip()]


def default_embedding_model() -> str:
    from core import model_provider

    return (
        _env(model_provider.EMBEDDING_MODEL_ENV)
        or _env(model_provider.AZURE_OPENAI_EMBEDDING_MODEL_ENV)
        or model_provider.DEFAULT_EMBEDDING_MODEL
    )


def default_embedding_dimension() -> int:
    from core import model_provider

    raw = _env(model_provider.EMBEDDING_DIMENSION_ENV) or _env(
        model_provider.AZURE_OPENAI_EMBEDDING_DIMENSION_ENV
    )
    if raw is None:
        return model_provider.DEFAULT_EMBEDDING_DIMENSION
    try:
        return int(raw)
    except ValueError:
        return model_provider.DEFAULT_EMBEDDING_DIMENSION


def default_vector_collection() -> str:
    from services.question_bank import QuestionBank

    return (
        _env(QuestionBank.VECTOR_COLLECTION_ENV)
        or QuestionBank.DEFAULT_COLLECTION_NAME
    )


def get_override(key: str) -> Any:
    with _LOCK:
        return _OVERRIDES.get(key)


def chat_model() -> Optional[str]:
    return get_override(KEY_CHAT_MODEL) or default_chat_model()


def chat_fallback_models() -> List[str]:
    value = get_override(KEY_CHAT_FALLBACK_MODELS)
    if value is not None:
        return list(value)
    return default_chat_fallback_models()


def embedding_model() -> Optional[str]:
    return get_override(KEY_EMBEDDING_MODEL) or default_embedding_model()


def embedding_dimension() -> int:
    value = get_override(KEY_EMBEDDING_DIMENSION)
    if value is not None:
        return int(value)
    return default_embedding_dimension()


def retrieval_top_k(default: int = 10) -> int:
    value = get_override(KEY_RETRIEVAL_TOP_K)
    if isinstance(value, int) and 1 <= value <= 50:
        return value
    return default


def keyword_fallback_enabled() -> bool:
    value = get_override(KEY_RETRIEVAL_KEYWORD_FALLBACK)
    return True if value is None else bool(value)


def validate_and_normalize(payload: Dict[str, Any], *, confirm_collection_switch: bool = False) -> Dict[str, Any]:
    """校验并归一化待应用配置；通过后返回可直接写入覆盖层的键值。"""
    normalized: Dict[str, Any] = {}
    unknown = [key for key in payload if key not in EDITABLE_KEYS]
    if unknown:
        raise RuntimeConfigError(f"未知或不可运行时修改的配置键: {', '.join(sorted(unknown))}")

    if KEY_CHAT_MODEL in payload:
        value = _clean_str(payload.get(KEY_CHAT_MODEL))
        if not value:
            raise RuntimeConfigError("chat_model 不能为空")
        normalized[KEY_CHAT_MODEL] = value

    if KEY_CHAT_FALLBACK_MODELS in payload:
        raw = payload.get(KEY_CHAT_FALLBACK_MODELS)
        if isinstance(raw, str):
            items = [item.strip() for item in raw.split(",")]
        elif isinstance(raw, list):
            items = [str(item).strip() for item in raw]
        else:
            raise RuntimeConfigError("chat_fallback_models 需为逗号分隔字符串或列表")
        items = [item for item in items if item]
        primary = normalized.get(KEY_CHAT_MODEL) or chat_model()
        items = [item for item in items if item != primary]
        if len(items) != len({item for item in items}):
            items = list(dict.fromkeys(items))
        normalized[KEY_CHAT_FALLBACK_MODELS] = items

    if KEY_EMBEDDING_MODEL in payload:
        value = _clean_str(payload.get(KEY_EMBEDDING_MODEL))
        if not value:
            raise RuntimeConfigError("embedding_model 不能为空")
        normalized[KEY_EMBEDDING_MODEL] = value

    if KEY_EMBEDDING_DIMENSION in payload:
        try:
            value = int(payload.get(KEY_EMBEDDING_DIMENSION))
        except (TypeError, ValueError):
            raise RuntimeConfigError("embedding_dimension 需为整数")
        if not 256 <= value <= 8192:
            raise RuntimeConfigError("embedding_dimension 需在 256–8192 之间")
        normalized[KEY_EMBEDDING_DIMENSION] = value

    if KEY_VECTOR_COLLECTION in payload:
        value = _clean_str(payload.get(KEY_VECTOR_COLLECTION))
        if not value:
            raise RuntimeConfigError("vector_collection 不能为空")
        current = get_override(KEY_VECTOR_COLLECTION) or default_vector_collection()
        if value != current and not confirm_collection_switch:
            raise RuntimeConfigError(
                "切换向量集合会影响语义空间（不同 embedding 的集合不可互换）；"
                "如确认目标集合与本服务 embedding 匹配，请携带 confirm_collection_switch=true 重试"
            )
        normalized[KEY_VECTOR_COLLECTION] = value

    if KEY_RETRIEVAL_TOP_K in payload:
        try:
            value = int(payload.get(KEY_RETRIEVAL_TOP_K))
        except (TypeError, ValueError):
            raise RuntimeConfigError("retrieval_top_k 需为整数")
        if not 1 <= value <= 50:
            raise RuntimeConfigError("retrieval_top_k 需在 1–50 之间")
        normalized[KEY_RETRIEVAL_TOP_K] = value

    if KEY_RETRIEVAL_KEYWORD_FALLBACK in payload:
        normalized[KEY_RETRIEVAL_KEYWORD_FALLBACK] = bool(payload.get(KEY_RETRIEVAL_KEYWORD_FALLBACK))

    return normalized


def apply_overrides(normalized: Dict[str, Any]) -> None:
    with _LOCK:
        _OVERRIDES.update(normalized)


def clear_override(key: str) -> None:
    with _LOCK:
        _OVERRIDES.pop(key, None)


def reset_overrides_for_tests() -> None:
    with _LOCK:
        _OVERRIDES.clear()


def snapshot() -> Dict[str, Any]:
    """当前生效配置（脱敏视图：不含任何凭证，只描述模型与检索行为）。"""
    with _LOCK:
        overridden = dict(_OVERRIDES)
    return {
        "chat_model": chat_model(),
        "chat_fallback_models": chat_fallback_models(),
        "embedding_model": embedding_model(),
        "embedding_dimension": embedding_dimension(),
        "vector_collection": get_override(KEY_VECTOR_COLLECTION) or default_vector_collection(),
        "retrieval_top_k": retrieval_top_k(),
        "retrieval_keyword_fallback": keyword_fallback_enabled(),
        "overridden_keys": sorted(overridden),
    }


def _clean_str(value: Any) -> Optional[str]:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
