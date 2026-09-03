"""模拟面试（AI 代答）相关 schema。"""

from typing import List, Literal, Optional

from pydantic import BaseModel, Field


MockQuestionType = Literal["self_introduction", "project", "technical"]


class MockCandidateHistoryItem(BaseModel):
    question: str = Field(..., description="已回答的问题")
    answer: str = Field(..., description="候选人当时的回答")


class MockCandidateAnswerRequest(BaseModel):
    question: str = Field(..., description="面试官当前问题")
    question_type: MockQuestionType = Field(..., description="问题类型")
    resume_content: str = Field(..., description="简历原文")
    job_requirements: Optional[str] = Field(default=None, description="职位要求")
    candidate_name: Optional[str] = Field(default=None, description="候选人姓名")
    recent_history: Optional[List[MockCandidateHistoryItem]] = Field(
        default=None,
        description="最近的问答历史（建议最多 3 条），用于保持回答一致性",
    )
    java_session_id: Optional[str] = Field(default=None, description="Java 面试会话 ID")
    request_id: Optional[str] = Field(default=None, description="上游请求 ID")


class MockCandidateAnswerResponse(BaseModel):
    answer: str = Field(..., description="候选人回答")
