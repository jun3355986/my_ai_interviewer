import os
import tempfile
import uuid

from fastapi import APIRouter, UploadFile, File, HTTPException
from fastapi.responses import StreamingResponse

from api.interviewer import Interviewer
from schemas.chat import (
    ChatRequest,
    ChatResponse,
    UnifiedChatRequest,
    ResumeStreamRequest,
    StartInterviewRequest,
    StartInterviewResponse,
    AnswerRequest,
    AnswerResponse,
    StartTechnicalInterviewRequest,
    ConcludeInterviewResponse,
    SessionInfoResponse,
    ImportQuestionsRequest,
    ImportQuestionsResponse,
    SearchQuestionsRequest,
    SearchQuestionsResponse,
)
from api.sse import (
    EVENT_STATUS,
    EVENT_CHUNK,
    EVENT_SCORE,
    EVENT_RESULT,
    EVENT_DONE,
    EVENT_ERROR,
    format_sse,
    stream_text_chunks,
)
from services.interview_service import interview_service
from services.interview_session import InterviewStage, session_manager
from services.question_bank import QuestionBank
from services.resume_parser import ResumeParser


router = APIRouter(prefix="/interview", tags=["interview"])
interviewer = Interviewer()
question_bank = QuestionBank()
resume_parser = ResumeParser()


# 保留原有的简单对话接口（向后兼容）
@router.post("/ask", response_model=ChatResponse)
def ask(req: ChatRequest) -> ChatResponse:
    history_dicts = (
        [m.model_dump() for m in req.history] if req.history is not None else None
    )
    answer = interviewer.ask(req.question, history=history_dicts)
    return ChatResponse(answer=answer)


# ============ 面试流程接口 ============


def _streaming_headers() -> dict[str, str]:
    return {
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "X-Accel-Buffering": "no",
    }


def _last_ai_message(session) -> str | None:
    for msg in reversed(session.history):
        if msg.get("role") == "ai":
            return msg.get("content")
    return None


@router.post("/chat")
def chat_stream(req: UnifiedChatRequest):
    """统一流式对话接口（SSE）"""

    def event_generator():
        try:
            if req.session_id:
                session = interview_service.get_session(req.session_id)
                if not session:
                    yield format_sse(
                        EVENT_ERROR,
                        {"code": "SESSION_NOT_FOUND", "message": f"会话不存在: {req.session_id}"},
                    )
                    return
            else:
                session_id = str(uuid.uuid4())
                session = session_manager.create_session(
                    session_id=session_id,
                    resume_content=req.resume_content,
                    job_requirements=req.job_requirements,
                )
                session.candidate_name = req.candidate_name
                session.stage = InterviewStage.OPENING
                interview_service._save_session(session)

            current_stage = session.stage
            yield format_sse(
                EVENT_STATUS,
                {"session_id": session.session_id, "stage": current_stage.value},
            )

            score = None
            feedback = None
            next_question = None
            next_stage = current_stage.value
            final_message = None

            if req.session_id is None:
                try:
                    opening = interview_service.interviewer.generate_opening(
                        session.resume_content or "",
                        session.job_requirements,
                    )
                except Exception:
                    opening = "您好，欢迎参加本次面试。接下来我们将按流程进行，请先放松并准备开始。"

                session.add_message("system", opening)
                interview_service._save_session(session)
                final_message = opening

            elif current_stage == InterviewStage.OPENING:
                result = interview_service.handle_opening_response(session.session_id)
                next_question = result.get("question")
                next_stage = result.get("stage", current_stage.value)
                final_message = next_question

            elif current_stage == InterviewStage.SELF_INTRO:
                result = interview_service.handle_self_introduction(session.session_id, req.message)
                next_question = result.get("question")
                next_stage = result.get("stage", current_stage.value)
                final_message = next_question

            elif current_stage == InterviewStage.PROJECT_QNA:
                result = interview_service.handle_project_answer(session.session_id, req.message)
                score = result.get("score")
                feedback = result.get("feedback")
                next_question = result.get("next_question")
                next_stage = result.get("stage", current_stage.value)
                final_message = next_question or result.get("message", "")

            elif current_stage == InterviewStage.TECHNICAL_QNA:
                result = interview_service.handle_technical_answer(session.session_id, req.message)
                score = result.get("score")
                feedback = result.get("feedback")
                next_question = result.get("next_question")
                next_stage = result.get("stage", current_stage.value)
                final_message = next_question or result.get("message", "")

            else:
                next_stage = InterviewStage.CONCLUDED.value
                final_message = "当前面试已结束。"

            if score is not None:
                yield format_sse(EVENT_SCORE, {"score": int(score), "feedback": feedback or ""})

            if final_message:
                for piece in stream_text_chunks(final_message):
                    yield format_sse(EVENT_CHUNK, {"content": piece})

            result_payload: dict[str, object] = {"next_stage": str(next_stage)}
            if next_question:
                result_payload["next_question"] = str(next_question)
            yield format_sse(EVENT_RESULT, result_payload)

            yield format_sse(
                EVENT_DONE,
                {
                    "stage": next_stage,
                    "is_interview_complete": next_stage == InterviewStage.CONCLUDED.value,
                },
            )
        except ValueError as exc:
            yield format_sse(EVENT_ERROR, {"code": "BAD_REQUEST", "message": str(exc)})
        except Exception as exc:
            yield format_sse(EVENT_ERROR, {"code": "INTERNAL_ERROR", "message": str(exc)})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers=_streaming_headers(),
    )


@router.post("/resume")
def resume_stream(req: ResumeStreamRequest):
    """恢复会话状态（SSE）"""

    def event_generator():
        try:
            session = interview_service.get_session(req.session_id)
            if not session:
                yield format_sse(
                    EVENT_ERROR,
                    {"code": "SESSION_NOT_FOUND", "message": f"会话不存在: {req.session_id}"},
                )
                return

            stage = session.stage.value
            yield format_sse(EVENT_STATUS, {"session_id": session.session_id, "stage": stage})

            last_question = _last_ai_message(session)
            if last_question:
                for piece in stream_text_chunks(last_question):
                    yield format_sse(EVENT_CHUNK, {"content": piece})
                yield format_sse(
                    EVENT_RESULT,
                    {"next_stage": stage, "next_question": last_question},
                )
            else:
                yield format_sse(EVENT_RESULT, {"next_stage": stage})

            yield format_sse(
                EVENT_DONE,
                {
                    "stage": stage,
                    "is_interview_complete": stage == InterviewStage.CONCLUDED.value,
                },
            )
        except Exception as exc:
            yield format_sse(EVENT_ERROR, {"code": "RESUME_ERROR", "message": str(exc)})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers=_streaming_headers(),
    )

@router.post("/start", response_model=StartInterviewResponse)
def start_interview(req: StartInterviewRequest) -> StartInterviewResponse:
    """开始新的面试"""
    session = interview_service.start_interview(
        resume_content=req.resume_content,
        job_requirements=req.job_requirements,
        candidate_name=req.candidate_name,
    )
    
    # 获取开场白（最后一个system消息）
    opening = ""
    for msg in reversed(session.history):
        if msg.get("role") == "system":
            opening = msg.get("content", "")
            break
    
    return StartInterviewResponse(
        session_id=session.session_id,
        opening=opening,
        stage=session.stage.value,
    )


@router.post("/upload-resume")
async def upload_resume(file: UploadFile = File(...)) -> dict[str, object]:
    """上传简历文件（PDF或文本）"""
    try:
        # 读取文件内容
        content = await file.read()
        filename = file.filename or ""
        file_extension = os.path.splitext(filename)[1]
        
        # 解析简历
        resume_text = resume_parser.parse_content(content, file_extension)
        
        return {
            "success": True,
            "resume_content": resume_text,
            "filename": file.filename,
        }
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"解析简历失败: {str(e)}")


@router.post("/{session_id}/opening-response", response_model=AnswerResponse)
def handle_opening_response(session_id: str) -> AnswerResponse:
    """处理开场后的响应，进入自我介绍环节"""
    try:
        result = interview_service.handle_opening_response(session_id)
        return AnswerResponse(
            question=result["question"],
            stage=result["stage"],
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/{session_id}/self-introduction", response_model=AnswerResponse)
def handle_self_introduction(session_id: str, req: AnswerRequest) -> AnswerResponse:
    """处理自我介绍，进入项目提问环节"""
    try:
        result = interview_service.handle_self_introduction(
            session_id,
            req.answer,
        )
        return AnswerResponse(
            question=result["question"],
            stage=result["stage"],
            message=f"目标问题数: {result.get('target_questions', 5)}",
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/{session_id}/project-answer", response_model=AnswerResponse)
def handle_project_answer(session_id: str, req: AnswerRequest) -> AnswerResponse:
    """处理项目问题回答"""
    try:
        result = interview_service.handle_project_answer(
            session_id,
            req.answer,
        )
        return AnswerResponse(**result)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/{session_id}/start-technical", response_model=AnswerResponse)
def start_technical_interview(
    session_id: str,
    req: StartTechnicalInterviewRequest,
) -> AnswerResponse:
    """开始技术面试环节"""
    try:
        result = interview_service.start_technical_interview(
            session_id,
            req.question_types,
            req.counts,
        )
        return AnswerResponse(
            question=result["question"],
            stage=result["stage"],
            remaining_questions=result.get("remaining_questions", 0),
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/{session_id}/technical-answer", response_model=AnswerResponse)
def handle_technical_answer(session_id: str, req: AnswerRequest) -> AnswerResponse:
    """处理技术问题回答"""
    try:
        result = interview_service.handle_technical_answer(
            session_id,
            req.answer,
        )
        return AnswerResponse(**result)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/{session_id}/conclude", response_model=ConcludeInterviewResponse)
def conclude_interview(session_id: str) -> ConcludeInterviewResponse:
    """总结面试"""
    try:
        result = interview_service.conclude_interview(session_id)
        return ConcludeInterviewResponse(**result)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/{session_id}/info", response_model=SessionInfoResponse)
def get_session_info(session_id: str) -> SessionInfoResponse:
    """获取会话信息"""
    session = interview_service.get_session(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")

    last_question = None 
    for msg in reversed(session.history):
        if msg.get("role") == "ai":
            last_question = msg.get("content")
            break
    
    return SessionInfoResponse(
        session_id=session.session_id,
        candidate_name=session.candidate_name,
        stage=session.stage.value,
        project_questions_count=session.project_questions_count,
        target_project_questions=session.target_project_questions,
        technical_questions_count=len(session.technical_qa_list),
        final_score=session.final_score,
        created_at=session.created_at,
        updated_at=session.updated_at,
        last_question=last_question,
        project_questions_pool=session.project_questions_pool,
    )


# ============ 问题库管理接口 ============

@router.post("/questions/import", response_model=ImportQuestionsResponse)
async def import_questions(file: UploadFile = File(...)) -> ImportQuestionsResponse:
    """导入面试题文件（PDF或文本）"""
    try:
        # 保存临时文件
        filename = file.filename or ""
        with tempfile.NamedTemporaryFile(delete=False, suffix=os.path.splitext(filename)[1]) as tmp_file:
            content = await file.read()
            tmp_file.write(content)
            tmp_path = tmp_file.name
        
        try:
            # 导入问题
            count = question_bank.import_question_file(tmp_path)
            return ImportQuestionsResponse(
                success=True,
                count=count,
                message=f"成功导入 {count} 个问题片段",
            )
        finally:
            # 清理临时文件
            os.unlink(tmp_path)
            
    except Exception as e:
        return ImportQuestionsResponse(
            success=False,
            count=0,
            message=f"导入失败: {str(e)}",
        )


@router.get("/questions/count")
def get_question_count() -> dict[str, int]:
    """获取问题库中的问题总数"""
    count = question_bank.get_question_count()
    return {"count": count}


@router.post("/questions/search", response_model=SearchQuestionsResponse)
def search_questions(req: SearchQuestionsRequest) -> SearchQuestionsResponse:
    """搜索问题"""
    results = question_bank.search_questions(
        query=req.query,
        job_requirements=req.job_requirements,
        question_types=req.question_types,
        k=req.k,
    )
    return SearchQuestionsResponse(
        count=len(results),
        questions=[{"content": doc.page_content, "metadata": doc.metadata} for doc in results],
    )
