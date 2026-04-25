from typing import List, Literal, Optional, Dict
from datetime import datetime

from pydantic import BaseModel, Field


class Message(BaseModel):
    role: Literal["human", "ai", "system"] = Field(..., description="消息角色")
    content: str = Field(..., description="消息内容")


class ChatRequest(BaseModel):
    question: str = Field(..., description="用户当前问题")
    history: Optional[List[Message]] = Field(default=None, description="历史对话")


class ChatResponse(BaseModel):
    answer: str = Field(..., description="模型回答")


class UnifiedChatRequest(BaseModel):
    session_id: Optional[str] = Field(default=None, description="会话ID，可为空")
    message: str = Field(..., description="用户当前消息")
    resume_content: Optional[str] = Field(default=None, description="简历内容（首次建议传入）")
    job_requirements: Optional[str] = Field(default=None, description="职位要求")
    candidate_name: Optional[str] = Field(default=None, description="候选人姓名")


class ResumeStreamRequest(BaseModel):
    session_id: str = Field(..., description="会话ID")


# 面试流程相关Schemas
class StartInterviewRequest(BaseModel):
    resume_content: str = Field(..., description="简历内容（文本格式）")
    job_requirements: Optional[str] = Field(default=None, description="职位要求")
    candidate_name: Optional[str] = Field(default=None, description="候选人姓名")


class StartInterviewResponse(BaseModel):
    session_id: str = Field(..., description="会话ID")
    opening: str = Field(..., description="开场白")
    stage: str = Field(..., description="当前阶段")


class AnswerRequest(BaseModel):
    session_id: str = Field(..., description="会话ID")
    answer: str = Field(..., description="回答内容")


class AnswerResponse(BaseModel):
    question: Optional[str] = Field(default=None, description="下一个问题")
    next_question: Optional[str] = Field(default=None, description="下一个问题")
    score: Optional[int] = Field(default=None, description="当前回答的分数")
    feedback: Optional[str] = Field(default=None, description="反馈")
    stage: str = Field(..., description="当前阶段")
    message: Optional[str] = Field(default=None, description="提示信息")
    remaining_questions: Optional[int] = Field(default=None, description="剩余问题数")
    qa_record: Optional[Dict[str, object]] = Field(default=None, description="问答记录")
    is_followup: Optional[bool] = Field(default=False, description="是否为追问")


class StartTechnicalInterviewRequest(BaseModel):
    session_id: str = Field(..., description="会话ID")
    question_types: List[str] = Field(..., description="问题类型列表，如 ['Java基础', '多线程']")
    counts: Dict[str, int] = Field(..., description="各类型题目数量，如 {'Java基础': 3, '多线程': 2}")


class ConcludeInterviewResponse(BaseModel):
    final_score: int = Field(..., description="最终分数")
    final_feedback: str = Field(..., description="最终反馈")
    average_score: Optional[float] = Field(default=None, description="平均分")
    stage: str = Field(..., description="当前阶段")


class SessionInfoResponse(BaseModel):
    session_id: str
    candidate_name: Optional[str]
    stage: str
    project_questions_count: int
    target_project_questions: int
    technical_questions_count: int
    final_score: Optional[int]
    created_at: datetime
    updated_at: datetime
    last_question: Optional[str]
    project_questions_pool: Optional[List[str]]


# 问题库管理相关Schemas
class ImportQuestionsRequest(BaseModel):
    file_path: str = Field(..., description="文件路径")


class ImportQuestionsResponse(BaseModel):
    success: bool = Field(..., description="是否成功")
    count: int = Field(..., description="导入的问题数量")
    message: str = Field(..., description="提示信息")


class SearchQuestionsRequest(BaseModel):
    query: str = Field(..., description="搜索查询文本")
    job_requirements: Optional[str] = Field(default=None, description="职位要求")
    question_types: Optional[List[str]] = Field(default=None, description="问题类型列表")
    k: int = Field(default=10, description="返回的问题数量")


class SearchQuestionsResponse(BaseModel):
    count: int = Field(..., description="匹配的问题数量")
    questions: List[Dict[str, object]] = Field(..., description="问题列表")
