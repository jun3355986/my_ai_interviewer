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


def test_repository_serialization_failure_is_best_effort(caplog):
    repository_module = importlib.import_module("services.observability.repository")
    config_module = importlib.import_module("services.observability.config")
    repo = repository_module.SqlAlchemyObservabilityRepository(
        engine=None,
        config=config_module.ObservabilityConfig(db_url="postgresql://unit-test"),
    )

    with caplog.at_level("ERROR"):
        trace_id = repo.create_trace(
            business_type="unit_test",
            entrypoint="serialization_failure_test",
            metadata={"bad": object()},
        )
        step_id = repo.create_step(
            trace_id=trace_id,
            step_name="serialization_failure_step",
            metadata={"bad": object()},
        )
        call_id = repo.record_llm_call(
            trace_id=trace_id,
            step_id=step_id,
            call_type="serialization_failure_call",
            provider="unit",
            model="unit-model",
            status="SUCCESS",
            raw_usage_json={"bad": object()},
            metadata={"bad": object()},
        )

    assert trace_id
    assert step_id
    assert call_id
    assert caplog.text.count("observability write failed") >= 3


class MutatingContent:
    def __init__(self):
        self.message = None

    def __str__(self):
        self.message.usage_metadata.clear()
        self.message.response_metadata.clear()
        return "hello after mutation"


def test_usage_is_extracted_before_message_text_conversion():
    observability_langchain = importlib.import_module("services.observability.langchain")
    writer = FakeTraceWriter()
    content = MutatingContent()
    message = FakeAiMessage(
        content=content,
        usage_metadata={
            "input_tokens": 7,
            "output_tokens": 4,
            "total_tokens": 11,
        },
        response_metadata={},
    )
    content.message = message

    response = observability_langchain.invoke_observable(
        prompt=FakePrompt(),
        llm=FakeLlm(message),
        input_values={},
        call_type="usage_order_test",
        repository=writer,
        provider="openai",
        model="unit-test-model",
    )

    assert response.text == "hello after mutation"
    assert writer.calls[0]["prompt_tokens"] == 7
    assert writer.calls[0]["completion_tokens"] == 4
    assert writer.calls[0]["total_tokens"] == 11


class FakePrimaryRunnable:
    model_name = "primary-model"


class FakeFallbackWrapper:
    def __init__(self, response):
        self.runnable = FakePrimaryRunnable()
        self.response = response

    def invoke(self, prompt_value):
        return self.response


def test_observable_helper_marks_fallback_from_wrapped_primary_model():
    observability_langchain = importlib.import_module("services.observability.langchain")
    writer = FakeTraceWriter()
    response_message = FakeAiMessage(
        content="fallback response",
        usage_metadata={},
        response_metadata={"model_name": "fallback-model"},
    )

    response = observability_langchain.invoke_observable(
        prompt=FakePrompt(),
        llm=FakeFallbackWrapper(response_message),
        input_values={},
        call_type="fallback_test",
        repository=writer,
        provider="openai",
    )

    assert response.text == "fallback response"
    assert writer.calls[0]["model"] == "fallback-model"
    assert writer.calls[0]["fallback_used"] is True
    assert writer.calls[0]["fallback_from_model"] == "primary-model"


class FakeDocument:
    def __init__(self, page_content):
        self.page_content = page_content


class FakeQuestionBank:
    def __init__(self):
        self.calls = []

    def search_questions(self, query, k):
        self.calls.append({"query": query, "k": k})
        return [FakeDocument("问题：Java 中 HashMap 的扩容机制是什么？")]


class FakeStepRepository:
    def __init__(self):
        self.created_steps = []
        self.finished_steps = []

    def create_step(self, **kwargs):
        self.created_steps.append(kwargs)
        return "step-1"

    def finish_step(self, **kwargs):
        self.finished_steps.append(kwargs)


class FakeTraceContext:
    trace_id = "trace-1"

    def __init__(self, repository):
        self.repository = repository


def test_select_technical_questions_records_retrieval_step(monkeypatch):
    interviewer_module = importlib.import_module("api.interviewer")
    session_module = importlib.import_module("services.interview_session")
    repository = FakeStepRepository()
    interviewer = object.__new__(interviewer_module.Interviewer)
    interviewer.question_bank = FakeQuestionBank()
    session = session_module.InterviewSession(
        session_id="session-1",
        job_requirements="Java backend role",
    )
    monkeypatch.setattr(
        interviewer_module,
        "current_trace_context",
        lambda: FakeTraceContext(repository),
        raising=False,
    )

    questions = interviewer.select_technical_questions(
        session,
        question_types=["Java基础"],
        counts={"Java基础": 1},
    )

    assert questions == ["Java 中 HashMap 的扩容机制是什么？"]
    assert repository.created_steps[0]["step_type"] == "retrieval"
    assert repository.created_steps[0]["step_name"] == "question_bank.search_questions"
    assert repository.created_steps[0]["metadata"]["operation"] == "question_bank.search_questions"
    assert repository.created_steps[0]["metadata"]["k"] == 2
    assert repository.finished_steps[0]["step_id"] == "step-1"
    assert repository.finished_steps[0]["status"] == "SUCCESS"
    assert repository.finished_steps[0]["duration_ms"] >= 0
