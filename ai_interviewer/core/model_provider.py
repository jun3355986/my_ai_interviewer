from dataclasses import dataclass
import os
from typing import Optional

from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from core import runtime_config


OPENAI_COMPAT_API_KEY_ENV = "AI_OPENAI_COMPAT_API_KEY"
OPENAI_COMPAT_BASE_URL_ENV = "AI_OPENAI_COMPAT_BASE_URL"
OPENAI_COMPAT_CHAT_MODEL_ENV = "AI_OPENAI_COMPAT_CHAT_MODEL"
OPENAI_COMPAT_FALLBACK_CHAT_MODELS_ENV = "AI_OPENAI_COMPAT_FALLBACK_CHAT_MODELS"

# Azure/Foundry 变量保留为兼容旧部署与 embedding 配置。聊天模型优先读取上面的
# 通用 OpenAI-compatible 变量，避免把供应商名称写进业务层。
AZURE_OPENAI_API_KEY_ENV = "AZURE_OPENAI_API_KEY"
AZURE_OPENAI_ENDPOINT_ENV = "AZURE_OPENAI_ENDPOINT"
AZURE_OPENAI_CHAT_MODEL_ENV = "AZURE_OPENAI_CHAT_MODEL"
AZURE_OPENAI_BACKUP_CHAT_MODEL_ENV = "AZURE_OPENAI_BACKUP_CHAT_MODEL"
AZURE_OPENAI_EMBEDDING_MODEL_ENV = "AZURE_OPENAI_EMBEDDING_MODEL"
AZURE_OPENAI_EMBEDDING_DIMENSION_ENV = "AZURE_OPENAI_EMBEDDING_DIMENSION"

EMBEDDING_API_KEY_ENV = "AI_EMBEDDING_API_KEY"
EMBEDDING_BASE_URL_ENV = "AI_EMBEDDING_BASE_URL"
EMBEDDING_MODEL_ENV = "AI_EMBEDDING_MODEL"
EMBEDDING_DIMENSION_ENV = "AI_EMBEDDING_DIMENSION"

# 兼容旧变量，便于平滑迁移
LEGACY_API_KEY_ENV = "DEEPSEEK_API_KEY"
LEGACY_EMBEDDING_DIMENSION_ENV = "DASHSCOPE_EMBEDDING_DIMENSION"

DEFAULT_CHAT_ENDPOINT = "https://opencode.ai/zen/go/v1/"
DEFAULT_CHAT_MODEL = "deepseek-v4-flash"
DEFAULT_FALLBACK_CHAT_MODELS = ("mimo-v2.5", "mimo-v2.5-pro")
DEFAULT_EMBEDDING_ENDPOINT = "https://liuwe-m7o7yvmk-eastus2.services.ai.azure.com/openai/v1/"
DEFAULT_EMBEDDING_MODEL = "embed-v-4-0"
DEFAULT_EMBEDDING_DIMENSION = 1024

_PLACEHOLDER_API_KEYS = {
    "",
    "test-key",
    "your-api-key",
    "your_azure_api_key",
}


def get_env(name: str, default: Optional[str] = None) -> Optional[str]:
    value = os.getenv(name)
    return value if value is not None and value != "" else default


def _normalize_openai_base_url(endpoint: str) -> str:
    normalized = endpoint.strip().rstrip("/")
    # Agent Plan 的 OpenAI-compatible 地址以 /api/plan/v3 结尾，而不是 /v1。
    # 不能把它再次拼接为 /openai/v1，否则实际请求会落到不存在的路径。
    if normalized.endswith("/v1") or normalized.endswith("/api/plan/v3"):
        return f"{normalized}/"
    if normalized.endswith("/openai"):
        return f"{normalized}/v1/"
    return f"{normalized}/openai/v1/"


@dataclass(frozen=True)
class AzureModelSettings:
    api_key: str
    base_url: str
    chat_model: str
    backup_chat_model: str
    fallback_chat_models: tuple[str, ...]
    embedding_model: str
    embedding_dimension: int


def _require_valid_api_key(api_key: str, env_hint: str) -> str:
    api_key = api_key.strip()
    if api_key.lower() in _PLACEHOLDER_API_KEYS:
        raise RuntimeError(
            f"缺少有效的 {env_hint}。"
            "请在本地配置真实 API Key，不能使用 test-key。"
        )
    return api_key


def _resolve_chat_api_key(explicit_api_key: Optional[str] = None) -> str:
    api_key = (
        explicit_api_key
        or get_env(OPENAI_COMPAT_API_KEY_ENV)
        or get_env(AZURE_OPENAI_API_KEY_ENV)
        or get_env(LEGACY_API_KEY_ENV)
        or ""
    )
    return _require_valid_api_key(api_key, OPENAI_COMPAT_API_KEY_ENV)


def _resolve_embedding_api_key(explicit_api_key: Optional[str] = None) -> str:
    api_key = (
        explicit_api_key
        or get_env(EMBEDDING_API_KEY_ENV)
        or get_env(AZURE_OPENAI_API_KEY_ENV)
        or get_env(LEGACY_API_KEY_ENV)
        or ""
    )
    return _require_valid_api_key(api_key, EMBEDDING_API_KEY_ENV)


def _parse_fallback_models(raw_models: Optional[str], primary_model: str) -> tuple[str, ...]:
    models = []
    for value in (raw_models or "").split(","):
        candidate = value.strip()
        if candidate and candidate != primary_model and candidate not in models:
            models.append(candidate)
    return tuple(models)


def load_model_settings(
    *,
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    endpoint: Optional[str] = None,
    embedding_model: Optional[str] = None,
    embedding_dimension: Optional[int] = None,
) -> AzureModelSettings:
    resolved_api_key = _resolve_chat_api_key(api_key)
    raw_endpoint = (
        endpoint
        or get_env(OPENAI_COMPAT_BASE_URL_ENV)
        or get_env(AZURE_OPENAI_ENDPOINT_ENV)
        or DEFAULT_CHAT_ENDPOINT
    )
    resolved_base_url = _normalize_openai_base_url(raw_endpoint)

    # 模型选择优先级：显式参数 > 运行时覆盖（管理端在线切换）> 环境变量 > 默认值。
    resolved_chat_model = model or runtime_config.chat_model()
    runtime_fallback_models = runtime_config.chat_fallback_models()
    if model is None and runtime_fallback_models:
        resolved_fallback_models = tuple(
            item for item in runtime_fallback_models if item != resolved_chat_model
        )
    else:
        raw_fallback_models = (
            get_env(OPENAI_COMPAT_FALLBACK_CHAT_MODELS_ENV)
            or get_env(AZURE_OPENAI_BACKUP_CHAT_MODEL_ENV)
            or ",".join(DEFAULT_FALLBACK_CHAT_MODELS)
        )
        resolved_fallback_models = _parse_fallback_models(
            raw_fallback_models,
            resolved_chat_model,
        )

    return AzureModelSettings(
        api_key=resolved_api_key,
        base_url=resolved_base_url,
        chat_model=resolved_chat_model,
        backup_chat_model=resolved_fallback_models[0] if resolved_fallback_models else "",
        fallback_chat_models=resolved_fallback_models,
        # 这里的两个字段仅为兼容旧的 Settings 消费者；embedding 使用下方独立配置。
        embedding_model=embedding_model or DEFAULT_EMBEDDING_MODEL,
        embedding_dimension=embedding_dimension or DEFAULT_EMBEDDING_DIMENSION,
    )


def load_azure_model_settings(**kwargs) -> AzureModelSettings:
    """兼容旧导入；新代码请使用 load_model_settings。"""
    return load_model_settings(**kwargs)


def build_chat_llm(
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    endpoint: Optional[str] = None,
    temperature: float = 0.3,
):
    settings = load_model_settings(model=model, api_key=api_key, endpoint=endpoint)
    primary_llm = ChatOpenAI(
        model=settings.chat_model,
        api_key=settings.api_key,
        base_url=settings.base_url,
        temperature=temperature,
    )

    # 显式指定模型时，尊重调用方选择，不自动追加回退模型
    if model:
        return primary_llm

    if settings.fallback_chat_models:
        fallback_llms = [
            ChatOpenAI(
                model=fallback_model,
                api_key=settings.api_key,
                base_url=settings.base_url,
                temperature=temperature,
            )
            for fallback_model in settings.fallback_chat_models
        ]
        return primary_llm.with_fallbacks(fallback_llms)

    return primary_llm


def load_embedding_settings(
    *,
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    endpoint: Optional[str] = None,
    embedding_dimension: Optional[int] = None,
) -> AzureModelSettings:
    resolved_api_key = _resolve_embedding_api_key(api_key)
    raw_endpoint = (
        endpoint
        or get_env(EMBEDDING_BASE_URL_ENV)
        or get_env(AZURE_OPENAI_ENDPOINT_ENV)
        or DEFAULT_EMBEDDING_ENDPOINT
    )
    resolved_embedding_model = (
        model
        or runtime_config.embedding_model()
    )
    resolved_embedding_dimension = embedding_dimension
    if resolved_embedding_dimension is None:
        resolved_embedding_dimension = runtime_config.embedding_dimension()
    return AzureModelSettings(
        api_key=resolved_api_key,
        base_url=_normalize_openai_base_url(raw_endpoint),
        chat_model="",
        backup_chat_model="",
        fallback_chat_models=(),
        embedding_model=resolved_embedding_model,
        embedding_dimension=resolved_embedding_dimension,
    )


def build_embeddings(
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    endpoint: Optional[str] = None,
    dimensions: Optional[int] = None,
) -> OpenAIEmbeddings:
    settings = load_embedding_settings(
        api_key=api_key,
        endpoint=endpoint,
        model=model,
        embedding_dimension=dimensions,
    )
    kwargs = {
        "model": settings.embedding_model,
        "api_key": settings.api_key,
        "base_url": settings.base_url,
        # 非 OpenAI 官方 embedding 兼容实现通常不接受 token-id 数组，直接发送字符串更稳妥
        "check_embedding_ctx_length": False,
    }

    if settings.embedding_dimension > 0:
        kwargs["dimensions"] = settings.embedding_dimension

    return OpenAIEmbeddings(**kwargs)
