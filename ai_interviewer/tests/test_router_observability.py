import asyncio
import os
import sys
from contextlib import contextmanager
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("AZURE_OPENAI_API_KEY", "unit-test-key")

from schemas.chat import UnifiedChatRequest
from services.interview_session import InterviewSession


async def _consume_streaming_response(response):
    chunks = []
    async for chunk in response.body_iterator:
        chunks.append(chunk.decode() if isinstance(chunk, bytes) else chunk)
    return chunks


class FakeTrace:
    def mark_error(self, error_code, error_message):
        self.error_code = error_code
        self.error_message = error_message


class FakeQuestionBank:
    pass


def test_first_turn_chat_trace_uses_generated_python_session_before_opening_llm(
    monkeypatch,
):
    import api.interviewer as interviewer_module
    import services.question_bank as question_bank_module

    monkeypatch.setattr(question_bank_module, "QuestionBank", FakeQuestionBank)
    monkeypatch.setattr(interviewer_module, "QuestionBank", FakeQuestionBank)

    import api.router as router_module

    created_session_ids = []
    trace_contexts = []
    opening_call_contexts = []

    def create_session(session_id, resume_content=None, job_requirements=None):
        created_session_ids.append(session_id)
        return InterviewSession(
            session_id=session_id,
            resume_content=resume_content,
            job_requirements=job_requirements,
        )

    @contextmanager
    def fake_observability_trace(**kwargs):
        trace_contexts.append(kwargs)
        yield FakeTrace()

    def generate_opening(resume_content, job_requirements):
        opening_call_contexts.append(trace_contexts[-1])
        return "欢迎参加面试。"

    monkeypatch.setattr(router_module.session_manager, "create_session", create_session)
    monkeypatch.setattr(router_module.interview_service, "_save_session", lambda session: None)
    monkeypatch.setattr(
        router_module.interview_service.interviewer,
        "generate_opening",
        generate_opening,
    )
    monkeypatch.setattr(router_module, "observability_trace", fake_observability_trace)

    response = router_module.chat_stream(
        UnifiedChatRequest(
            session_id=None,
            message="开始面试",
            resume_content="候选人简历",
            job_requirements="后端工程师",
        )
    )

    chunks = asyncio.run(_consume_streaming_response(response))

    assert created_session_ids
    assert "欢迎参加面试。" in "".join(chunks)
    assert opening_call_contexts[0]["python_session_id"] == created_session_ids[0]
    assert opening_call_contexts[0]["session_id"] == created_session_ids[0]
