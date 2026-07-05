from __future__ import annotations

import json
import uuid
from typing import Any

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import AliasChoices, BaseModel, ConfigDict, Field


app = FastAPI(title="AI Interviewer Python AI Stub")

SESSIONS: dict[str, str] = {}


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    session_id: str | None = Field(default=None, validation_alias=AliasChoices("session_id", "sessionId"))
    message: str
    resume_content: str | None = Field(default=None, validation_alias=AliasChoices("resume_content", "resumeContent"))
    job_requirements: str | None = Field(default=None, validation_alias=AliasChoices("job_requirements", "jobRequirements"))
    candidate_name: str | None = Field(default=None, validation_alias=AliasChoices("candidate_name", "candidateName"))
    request_id: str | None = Field(default=None, validation_alias=AliasChoices("request_id", "requestId"))
    java_session_id: str | None = Field(default=None, validation_alias=AliasChoices("java_session_id", "javaSessionId"))
    user_id: int | None = Field(default=None, validation_alias=AliasChoices("user_id", "userId"))
    username: str | None = Field(default=None)
    business_type: str | None = Field(default=None, validation_alias=AliasChoices("business_type", "businessType"))
    entrypoint: str | None = Field(default=None)


class ResumeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    session_id: str = Field(validation_alias=AliasChoices("session_id", "sessionId"))
    request_id: str | None = Field(default=None, validation_alias=AliasChoices("request_id", "requestId"))
    java_session_id: str | None = Field(default=None, validation_alias=AliasChoices("java_session_id", "javaSessionId"))
    user_id: int | None = Field(default=None, validation_alias=AliasChoices("user_id", "userId"))
    username: str | None = Field(default=None)
    business_type: str | None = Field(default=None, validation_alias=AliasChoices("business_type", "businessType"))
    entrypoint: str | None = Field(default=None)


def format_sse(event: str, data: dict[str, Any]) -> str:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event}\ndata: {payload}\n\n"


def session_id_for(req: ChatRequest) -> str:
    if req.session_id:
        return req.session_id
    if req.java_session_id:
        return f"stub-{req.java_session_id}"
    return f"stub-{uuid.uuid4()}"


def opening_events(session_id: str) -> list[tuple[str, dict[str, Any]]]:
    text = "您好，欢迎参加本次面试。请准备好后回复，我们将从自我介绍开始。"
    return [
        ("status", {"session_id": session_id, "stage": "opening"}),
        ("chunk", {"content": text}),
        ("result", {"next_stage": "opening", "next_question": text}),
        ("done", {"stage": "opening", "is_interview_complete": False}),
    ]


def self_intro_events(session_id: str) -> list[tuple[str, dict[str, Any]]]:
    question = {
        "id": "stub-self-intro-001",
        "type": "self_introduction",
        "text": "请用 2 分钟介绍一下你的后端项目经验。",
    }
    text = question["text"]
    return [
        ("status", {"session_id": session_id, "stage": "opening"}),
        ("question", {"question": question, "next_stage": "self_introduction"}),
        ("chunk", {"content": text}),
        ("result", {"next_stage": "self_introduction", "question": question, "next_question": text}),
        ("done", {"stage": "self_introduction", "is_interview_complete": False}),
    ]


def project_question_events(session_id: str) -> list[tuple[str, dict[str, Any]]]:
    question = {
        "id": "stub-project-001",
        "type": "project_qna",
        "text": "你在项目中如何设计 Redis 缓存和数据库一致性策略？",
    }
    text = question["text"]
    return [
        ("status", {"session_id": session_id, "stage": "self_introduction"}),
        ("score", {"score": 80, "feedback": "回答结构清晰，可继续追问项目细节。"}),
        ("question", {"question": question, "next_stage": "project_qna"}),
        ("chunk", {"content": text}),
        ("result", {"next_stage": "project_qna", "question": question, "next_question": text}),
        ("done", {"stage": "project_qna", "is_interview_complete": False}),
    ]


def technical_question_events(session_id: str) -> list[tuple[str, dict[str, Any]]]:
    question = {
        "id": "stub-technical-001",
        "type": "technical_qna",
        "text": "请说明 JVM 垃圾回收中可达性分析的基本过程。",
    }
    text = question["text"]
    return [
        ("status", {"session_id": session_id, "stage": "project_qna"}),
        ("score", {"score": 82, "feedback": "项目回答覆盖了关键权衡。"}),
        ("question", {"question": question, "next_stage": "technical_qna"}),
        ("chunk", {"content": text}),
        ("result", {"next_stage": "technical_qna", "question": question, "next_question": text}),
        ("done", {"stage": "technical_qna", "is_interview_complete": False}),
    ]


def next_events(session_id: str, stage: str) -> tuple[str, list[tuple[str, dict[str, Any]]]]:
    if stage == "opening":
        return "self_introduction", self_intro_events(session_id)
    if stage == "self_introduction":
        return "project_qna", project_question_events(session_id)
    if stage == "project_qna":
        return "technical_qna", technical_question_events(session_id)
    return "technical_qna", technical_question_events(session_id)


def stream_events(events: list[tuple[str, dict[str, Any]]]):
    for event, data in events:
        yield format_sse(event, data)


def streaming_headers() -> dict[str, str]:
    return {
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "X-Accel-Buffering": "no",
    }


@app.post("/interview/chat")
def chat(req: ChatRequest):
    sid = session_id_for(req)
    if req.session_id is None:
        SESSIONS[sid] = "opening"
        events = opening_events(sid)
    else:
        current_stage = SESSIONS.get(sid, "opening")
        next_stage, events = next_events(sid, current_stage)
        SESSIONS[sid] = next_stage
    return StreamingResponse(stream_events(events), media_type="text/event-stream", headers=streaming_headers())


@app.post("/interview/resume")
def resume(req: ResumeRequest):
    stage = SESSIONS.get(req.session_id, "opening")
    events = [
        ("status", {"session_id": req.session_id, "stage": stage}),
        ("chunk", {"content": f"已恢复到 {stage} 阶段。"}),
        ("result", {"next_stage": stage}),
        ("done", {"stage": stage, "is_interview_complete": stage == "concluded"}),
    ]
    return StreamingResponse(stream_events(events), media_type="text/event-stream", headers=streaming_headers())
