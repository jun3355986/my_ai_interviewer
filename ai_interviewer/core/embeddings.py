"""
统一 Embeddings 接入层（Azure OpenAI / Foundry）。
"""
from typing import Optional

from langchain_openai import OpenAIEmbeddings

from core.model_provider import build_embeddings


def get_embeddings(
    model: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    dimension: Optional[int] = None,
) -> OpenAIEmbeddings:
    return build_embeddings(
        model=model,
        api_key=api_key,
        endpoint=base_url,
        dimensions=dimension,
    )
