import sys
import importlib
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakePromptValue:
    def to_string(self):
        return "unit test prompt"


class FakePrompt:
    def invoke(self, input_values):
        return FakePromptValue()


@dataclass
class FakeAiMessage:
    content: str
    usage_metadata: dict
    response_metadata: dict


class FakeLlm:
    def __init__(self, response):
        self.response = response

    def invoke(self, prompt_value):
        return self.response


class FakeTraceWriter:
    def __init__(self):
        self.calls = []

    def record_llm_call(self, **kwargs):
        self.calls.append(kwargs)


def test_observable_invoke_captures_usage_before_text_parser():
    observability_langchain = importlib.import_module("services.observability.langchain")
    writer = FakeTraceWriter()
    llm = FakeLlm(
        FakeAiMessage(
            content="hello",
            usage_metadata={
                "input_tokens": 10,
                "output_tokens": 3,
                "total_tokens": 13,
            },
            response_metadata={
                "token_usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 3,
                    "total_tokens": 13,
                },
                "model_name": "unit-test-model",
            },
        )
    )

    response = observability_langchain.invoke_observable(
        prompt=FakePrompt(),
        llm=llm,
        input_values={},
        call_type="unit_test_call",
        repository=writer,
        provider="openai",
        model="unit-test-model",
    )

    assert response.text == "hello"
    assert writer.calls[0]["prompt_tokens"] == 10
    assert writer.calls[0]["completion_tokens"] == 3
    assert writer.calls[0]["total_tokens"] == 13


def test_repository_write_failure_is_logged_and_does_not_raise(caplog):
    repository_module = importlib.import_module("services.observability.repository")
    config_module = importlib.import_module("services.observability.config")
    repo = repository_module.SqlAlchemyObservabilityRepository(
        engine=None,
        config=config_module.ObservabilityConfig(db_url="postgresql://unit-test"),
    )

    def fail_write(*args, **kwargs):
        raise RuntimeError("unit test write failure")

    repo._execute = fail_write

    with caplog.at_level("ERROR"):
        trace_id = repo.create_trace(
            business_type="unit_test",
            entrypoint="repository_best_effort_test",
        )

    assert trace_id
    assert "observability write failed" in caplog.text
