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
    返回统一配置后的 Azure OpenAI / Foundry Chat LLM。

    优先使用传入参数，其次读取环境变量：
      - AZURE_OPENAI_API_KEY（必需）
      - AZURE_OPENAI_ENDPOINT（可选，默认项目内置 endpoint）
      - AZURE_OPENAI_CHAT_MODEL（可选，默认 grok-4-20-reasoning）
      - AZURE_OPENAI_BACKUP_CHAT_MODEL（可选，默认 gpt-5.4）
    """
    return build_chat_llm(
        model=model,
        api_key=api_key,
        endpoint=base_url,
        temperature=0.3,
    )
