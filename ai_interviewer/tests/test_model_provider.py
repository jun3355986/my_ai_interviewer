import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakeChatOpenAI:
    instances = []

    def __init__(self, **kwargs):
        self.kwargs = kwargs
        self.fallbacks = None
        self.__class__.instances.append(self)

    def with_fallbacks(self, fallbacks):
        self.fallbacks = fallbacks
        return self


def test_openai_compatible_settings_use_opencode_go_primary_and_two_fallbacks(monkeypatch):
    from core import model_provider

    monkeypatch.setenv("AI_OPENAI_COMPAT_API_KEY", "unit-test-valid-key")
    monkeypatch.setenv("AI_OPENAI_COMPAT_BASE_URL", "https://opencode.ai/zen/go/v1")
    monkeypatch.setenv("AI_OPENAI_COMPAT_CHAT_MODEL", "deepseek-v4-flash")
    monkeypatch.setenv("AI_OPENAI_COMPAT_FALLBACK_CHAT_MODELS", "mimo-v2.5,mimo-v2.5-pro")

    settings = model_provider.load_model_settings()

    assert settings.base_url == "https://opencode.ai/zen/go/v1/"
    assert settings.chat_model == "deepseek-v4-flash"
    assert settings.fallback_chat_models == ("mimo-v2.5", "mimo-v2.5-pro")


def test_embedding_settings_preserve_agent_plan_v3_compatibility_endpoint(monkeypatch):
    from core import model_provider

    monkeypatch.setenv("AI_EMBEDDING_API_KEY", "unit-test-valid-key")
    monkeypatch.setenv(
        "AI_EMBEDDING_BASE_URL",
        "https://ark.cn-beijing.volces.com/api/plan/v3",
    )
    monkeypatch.setenv("AI_EMBEDDING_MODEL", "doubao-embedding-vision")
    monkeypatch.setenv("AI_EMBEDDING_DIMENSION", "1024")

    settings = model_provider.load_embedding_settings()

    assert settings.base_url == "https://ark.cn-beijing.volces.com/api/plan/v3/"
    assert settings.embedding_model == "doubao-embedding-vision"
    assert settings.embedding_dimension == 1024


def test_build_chat_llm_constructs_ordered_fallbacks(monkeypatch):
    from core import model_provider

    FakeChatOpenAI.instances = []
    monkeypatch.setattr(model_provider, "ChatOpenAI", FakeChatOpenAI)
    monkeypatch.setenv("AI_OPENAI_COMPAT_API_KEY", "unit-test-valid-key")
    monkeypatch.setenv("AI_OPENAI_COMPAT_FALLBACK_CHAT_MODELS", "mimo-v2.5,mimo-v2.5-pro")

    llm = model_provider.build_chat_llm()

    assert [instance.kwargs["model"] for instance in FakeChatOpenAI.instances] == [
        "deepseek-v4-flash",
        "mimo-v2.5",
        "mimo-v2.5-pro",
    ]
    assert [fallback.kwargs["model"] for fallback in llm.fallbacks] == [
        "mimo-v2.5",
        "mimo-v2.5-pro",
    ]


def test_explicit_chat_model_does_not_append_fallbacks(monkeypatch):
    from core import model_provider

    FakeChatOpenAI.instances = []
    monkeypatch.setattr(model_provider, "ChatOpenAI", FakeChatOpenAI)
    monkeypatch.setenv("AI_OPENAI_COMPAT_API_KEY", "unit-test-valid-key")

    llm = model_provider.build_chat_llm(model="requested-model")

    assert llm.kwargs["model"] == "requested-model"
    assert len(FakeChatOpenAI.instances) == 1


def test_question_bank_uses_keyword_results_when_vector_lookup_fails():
    from langchain_core.documents import Document
    from services.question_bank import QuestionBank

    class BrokenVectorStore:
        def similarity_search(self, *_args, **_kwargs):
            raise RuntimeError("embedding provider unavailable")

    bank = object.__new__(QuestionBank)
    bank.vectorstore = BrokenVectorStore()
    bank._keyword_search = lambda _query, k: [Document(page_content="问题：Java HashMap 如何扩容？")][:k]

    results = bank.search_questions("Java基础", k=1)

    assert [document.page_content for document in results] == ["问题：Java HashMap 如何扩容？"]


def test_question_bank_starts_in_keyword_mode_when_embeddings_are_unconfigured(monkeypatch):
    import pytest

    from services import question_bank

    captured = {}

    class FakeChroma:
        def __init__(self, **kwargs):
            captured.update(kwargs)

    monkeypatch.setattr(question_bank, "get_embeddings", lambda: (_ for _ in ()).throw(RuntimeError("missing key")))
    monkeypatch.setattr(question_bank, "Chroma", FakeChroma)

    bank = question_bank.QuestionBank(collection_name="keyword-only-test")

    assert isinstance(bank.embeddings, question_bank._UnavailableEmbeddings)
    assert captured["embedding_function"] is bank.embeddings
    with pytest.raises(RuntimeError, match="embedding provider is not configured"):
        bank.embeddings.embed_query("Java基础")


def test_question_bank_uses_configured_vector_collection(monkeypatch):
    from services import question_bank

    captured = {}

    class FakeChroma:
        def __init__(self, **kwargs):
            captured.update(kwargs)

    monkeypatch.setenv(
        "AI_INTERVIEW_VECTOR_COLLECTION",
        "interview_questions_doubao_embedding_vision_251215_1024_v1",
    )
    monkeypatch.setattr(question_bank, "get_embeddings", lambda: object())
    monkeypatch.setattr(question_bank, "Chroma", FakeChroma)

    question_bank.QuestionBank()

    assert captured["collection_name"] == "interview_questions_doubao_embedding_vision_251215_1024_v1"
