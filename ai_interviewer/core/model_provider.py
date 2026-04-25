from dataclasses import dataclass
import os
from typing import Optional

from langchain_openai import ChatOpenAI, OpenAIEmbeddings


AZURE_OPENAI_API_KEY_ENV = "AZURE_OPENAI_API_KEY"
AZURE_OPENAI_ENDPOINT_ENV = "AZURE_OPENAI_ENDPOINT"
AZURE_OPENAI_CHAT_MODEL_ENV = "AZURE_OPENAI_CHAT_MODEL"
AZURE_OPENAI_BACKUP_CHAT_MODEL_ENV = "AZURE_OPENAI_BACKUP_CHAT_MODEL"
AZURE_OPENAI_EMBEDDING_MODEL_ENV = "AZURE_OPENAI_EMBEDDING_MODEL"
AZURE_OPENAI_EMBEDDING_DIMENSION_ENV = "AZURE_OPENAI_EMBEDDING_DIMENSION"

# 兼容旧变量，便于平滑迁移
LEGACY_API_KEY_ENV = "DEEPSEEK_API_KEY"
LEGACY_EMBEDDING_DIMENSION_ENV = "DASHSCOPE_EMBEDDING_DIMENSION"

DEFAULT_ENDPOINT = "https://liuwe-m7o7yvmk-eastus2.services.ai.azure.com/openai/v1/"
DEFAULT_CHAT_MODEL = "grok-4-20-reasoning"
DEFAULT_BACKUP_CHAT_MODEL = "gpt-5.4"
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
    if normalized.endswith("/openai/v1"):
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
    embedding_model: str
    embedding_dimension: int


def _resolve_api_key(explicit_api_key: Optional[str] = None) -> str:
    api_key = (
        explicit_api_key
        or get_env(AZURE_OPENAI_API_KEY_ENV)
        or get_env(LEGACY_API_KEY_ENV)
        or ""
    ).strip()
    if api_key.lower() in _PLACEHOLDER_API_KEYS:
        raise RuntimeError(
            f"缺少有效的 {AZURE_OPENAI_API_KEY_ENV}。"
            "请在本地配置真实 Azure API Key，不能使用 test-key。"
        )
    return api_key


def load_azure_model_settings(
    *,
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    endpoint: Optional[str] = None,
    embedding_model: Optional[str] = None,
    embedding_dimension: Optional[int] = None,
) -> AzureModelSettings:
    resolved_api_key = _resolve_api_key(api_key)
    raw_endpoint = endpoint or get_env(AZURE_OPENAI_ENDPOINT_ENV, DEFAULT_ENDPOINT)
    resolved_base_url = _normalize_openai_base_url(raw_endpoint)

    resolved_chat_model = model or get_env(
        AZURE_OPENAI_CHAT_MODEL_ENV, DEFAULT_CHAT_MODEL
    )
    resolved_backup_chat_model = get_env(
        AZURE_OPENAI_BACKUP_CHAT_MODEL_ENV, DEFAULT_BACKUP_CHAT_MODEL
    )
    resolved_embedding_model = embedding_model or get_env(
        AZURE_OPENAI_EMBEDDING_MODEL_ENV, DEFAULT_EMBEDDING_MODEL
    )

    if embedding_dimension is not None:
        resolved_embedding_dimension = embedding_dimension
    else:
        resolved_embedding_dimension = int(
            get_env(
                AZURE_OPENAI_EMBEDDING_DIMENSION_ENV,
                get_env(
                    LEGACY_EMBEDDING_DIMENSION_ENV, str(DEFAULT_EMBEDDING_DIMENSION)
                ),
            )
        )

    return AzureModelSettings(
        api_key=resolved_api_key,
        base_url=resolved_base_url,
        chat_model=resolved_chat_model,
        backup_chat_model=resolved_backup_chat_model,
        embedding_model=resolved_embedding_model,
        embedding_dimension=resolved_embedding_dimension,
    )


def build_chat_llm(
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    endpoint: Optional[str] = None,
    temperature: float = 0.3,
):
    settings = load_azure_model_settings(model=model, api_key=api_key, endpoint=endpoint)
    primary_llm = ChatOpenAI(
        model=settings.chat_model,
        api_key=settings.api_key,
        base_url=settings.base_url,
        temperature=temperature,
    )

    # 显式指定模型时，尊重调用方选择，不自动追加回退模型
    if model:
        return primary_llm

    backup_model = settings.backup_chat_model.strip()
    if backup_model and backup_model != settings.chat_model:
        backup_llm = ChatOpenAI(
            model=backup_model,
            api_key=settings.api_key,
            base_url=settings.base_url,
            temperature=temperature,
        )
        return primary_llm.with_fallbacks([backup_llm])

    return primary_llm


def build_embeddings(
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    endpoint: Optional[str] = None,
    dimensions: Optional[int] = None,
) -> OpenAIEmbeddings:
    settings = load_azure_model_settings(
        api_key=api_key,
        endpoint=endpoint,
        embedding_model=model,
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
