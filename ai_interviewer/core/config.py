from typing import Any, Optional

from core.model_provider import build_chat_llm, get_env as provider_get_env


def get_env(name: str, default: Optional[str] = None) -> Optional[str]:
    return provider_get_env(name, default)


def get_llm(
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
) -> Any:
    """
    返回统一配置后的 OpenAI-compatible Chat LLM。

    优先使用传入参数，其次读取环境变量：
      - AI_OPENAI_COMPAT_API_KEY（必需）
      - AI_OPENAI_COMPAT_BASE_URL（可选，默认 OpenCode Go endpoint）
      - AI_OPENAI_COMPAT_CHAT_MODEL（可选，默认 deepseek-v4-flash）
      - AI_OPENAI_COMPAT_FALLBACK_CHAT_MODELS（可选，逗号分隔，默认 MiMo 2.5、MiMo 2.5 Pro）

    旧的 AZURE_OPENAI_* 变量仍可作为兼容回退读取，但新部署不应再依赖它们。
    """
    return build_chat_llm(
        model=model,
        api_key=api_key,
        endpoint=base_url,
        temperature=0.3,
    )
