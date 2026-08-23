"""runtime_config 的校验与优先级单测（不依赖网络与真实 provider）。"""

import pytest

from core import runtime_config


@pytest.fixture(autouse=True)
def clean_overrides(monkeypatch):
    runtime_config.reset_overrides_for_tests()
    yield
    runtime_config.reset_overrides_for_tests()


def test_unknown_key_rejected():
    with pytest.raises(runtime_config.RuntimeConfigError):
        runtime_config.validate_and_normalize({"api_key": "sk-xxx"})


def test_chat_model_normalized():
    normalized = runtime_config.validate_and_normalize({"chat_model": " m2-pro "})
    assert normalized == {"chat_model": "m2-pro"}


def test_chat_model_empty_rejected():
    with pytest.raises(runtime_config.RuntimeConfigError):
        runtime_config.validate_and_normalize({"chat_model": "   "})


def test_fallback_models_dedup_and_primary_removed():
    normalized = runtime_config.validate_and_normalize(
        {"chat_model": "m1", "chat_fallback_models": "m2, m1, m3, m2"}
    )
    assert normalized["chat_fallback_models"] == ["m2", "m3"]


def test_embedding_dimension_bounds():
    with pytest.raises(runtime_config.RuntimeConfigError):
        runtime_config.validate_and_normalize({"embedding_dimension": 64})
    with pytest.raises(runtime_config.RuntimeConfigError):
        runtime_config.validate_and_normalize({"embedding_dimension": 99999})
    assert runtime_config.validate_and_normalize({"embedding_dimension": "1024"}) == {
        "embedding_dimension": 1024
    }


def test_vector_collection_switch_requires_confirm():
    with pytest.raises(runtime_config.RuntimeConfigError):
        runtime_config.validate_and_normalize({"vector_collection": "other_v1"})
    normalized = runtime_config.validate_and_normalize(
        {"vector_collection": "other_v1"}, confirm_collection_switch=True
    )
    assert normalized["vector_collection"] == "other_v1"


def test_same_collection_no_confirm_needed(monkeypatch):
    monkeypatch.setattr(
        runtime_config, "default_vector_collection", lambda: "interview_questions"
    )
    normalized = runtime_config.validate_and_normalize({"vector_collection": "interview_questions"})
    assert normalized["vector_collection"] == "interview_questions"


def test_retrieval_top_k_bounds():
    with pytest.raises(runtime_config.RuntimeConfigError):
        runtime_config.validate_and_normalize({"retrieval_top_k": 0})
    with pytest.raises(runtime_config.RuntimeConfigError):
        runtime_config.validate_and_normalize({"retrieval_top_k": 51})
    assert runtime_config.validate_and_normalize({"retrieval_top_k": 5}) == {
        "retrieval_top_k": 5
    }


def test_override_priority_and_snapshot(monkeypatch):
    monkeypatch.setattr(runtime_config, "default_chat_model", lambda: "env-model")
    runtime_config.apply_overrides({"chat_model": "runtime-model"})
    assert runtime_config.chat_model() == "runtime-model"
    snapshot = runtime_config.snapshot()
    assert snapshot["chat_model"] == "runtime-model"
    assert "overridden_keys" in snapshot and "chat_model" in snapshot["overridden_keys"]

    runtime_config.clear_override("chat_model")
    assert runtime_config.chat_model() == "env-model"


def test_retrieval_top_k_fallback_default():
    assert runtime_config.retrieval_top_k(default=10) == 10
    runtime_config.apply_overrides({"retrieval_top_k": 8})
    assert runtime_config.retrieval_top_k(default=10) == 8


def test_keyword_fallback_default_true():
    assert runtime_config.keyword_fallback_enabled() is True
    runtime_config.apply_overrides({"retrieval_keyword_fallback": False})
    assert runtime_config.keyword_fallback_enabled() is False
