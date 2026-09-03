"""模拟面试（AI 代答）接口。

真实面试流程仍由 Java durable-turn 管线驱动；这里只提供无状态的
候选人回答生成，供 Flutter MockAutoDriver 在轮到候选人时调用。
"""

import logging
from typing import Optional

from fastapi import APIRouter

from schemas.mock import MockCandidateAnswerRequest, MockCandidateAnswerResponse
from services.mock_candidate import MockCandidate
from services.observability.context import observability_trace

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/interview/mock", tags=["mock-interview"])

_candidate: Optional[MockCandidate] = None


def _get_candidate() -> MockCandidate:
    global _candidate
    if _candidate is None:
        _candidate = MockCandidate()
    return _candidate


@router.post("/candidate-answer", response_model=MockCandidateAnswerResponse)
def candidate_answer(req: MockCandidateAnswerRequest) -> MockCandidateAnswerResponse:
    """为模拟面试生成候选人回答（无状态，不创建会话）。"""
    with observability_trace(
        request_id=req.request_id,
        session_id=req.java_session_id,
        python_session_id=req.java_session_id,
        business_type="mock_interview",
        entrypoint="mock_candidate_answer",
        metadata={"question_type": req.question_type},
    ):
        answer = _get_candidate().generate_answer(
            question=req.question,
            question_type=req.question_type,
            resume_content=req.resume_content,
            job_requirements=req.job_requirements,
            candidate_name=req.candidate_name,
            recent_history=req.recent_history,
        )
    return MockCandidateAnswerResponse(answer=answer)
