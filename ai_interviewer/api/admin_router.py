from typing import Any, List, Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field

from schemas.question_item import QuestionMedia
from services.question_bank import QuestionBank


router = APIRouter(prefix="/admin", tags=["admin"])
question_bank = QuestionBank()


class AdminQuestionSyncItem(BaseModel):
    id: int
    question_text: str = Field(..., alias="question_text")
    answer_reference: Optional[str] = Field(default=None, alias="answer_reference")
    question_type: Optional[str] = Field(default=None, alias="question_type")
    difficulty: Optional[str] = None
    tags: List[str] = Field(default_factory=list)
    skill_area: Optional[str] = Field(default=None, alias="skill_area")
    media: List[QuestionMedia] = Field(default_factory=list)


class AdminQuestionSyncRequest(BaseModel):
    questions: List[AdminQuestionSyncItem] = Field(default_factory=list)


class AdminQuestionDeleteItem(BaseModel):
    id: int


class AdminQuestionDeleteRequest(BaseModel):
    questions: List[AdminQuestionDeleteItem] = Field(default_factory=list)


class AdminQuestionSyncResult(BaseModel):
    id: int
    status: str
    vector_store_id: Optional[str] = None
    error_message: Optional[str] = None


class AdminQuestionSyncResponse(BaseModel):
    status: str
    total_count: int
    success_count: int
    failed_count: int
    error_message: Optional[str] = None
    success_questions: List[AdminQuestionSyncResult] = Field(default_factory=list)
    failed_questions: List[AdminQuestionSyncResult] = Field(default_factory=list)


@router.post("/question-bank/sync", response_model=AdminQuestionSyncResponse)
def sync_question_bank(req: AdminQuestionSyncRequest) -> AdminQuestionSyncResponse:
    """同步 Admin 后台结构化题库到 Python 向量库。"""
    total_count = len(req.questions)
    if total_count == 0:
        return AdminQuestionSyncResponse(
            status="SUCCESS",
            total_count=0,
            success_count=0,
            failed_count=0,
        )

    success_questions: list[AdminQuestionSyncResult] = []
    failed_questions: list[AdminQuestionSyncResult] = []
    for item in req.questions:
        try:
            synced = question_bank.sync_structured_questions([item.model_dump(by_alias=True)])
            vector_store_id = synced[0].get("vector_store_id") if synced else None
            success_questions.append(
                AdminQuestionSyncResult(
                    id=item.id,
                    status="SYNCED",
                    vector_store_id=str(vector_store_id) if vector_store_id else None,
                )
            )
        except Exception as exc:
            failed_questions.append(
                AdminQuestionSyncResult(
                    id=item.id,
                    status="FAILED",
                    error_message=str(exc),
                )
            )

    failed_count = len(failed_questions)
    success_count = len(success_questions)
    if failed_count == 0:
        status = "SUCCESS"
    elif success_count == 0:
        status = "FAILED"
    else:
        status = "PARTIAL_FAILED"

    return AdminQuestionSyncResponse(
        status=status,
        total_count=total_count,
        success_count=success_count,
        failed_count=failed_count,
        error_message=None if failed_count == 0 else "Some questions failed to sync",
        success_questions=success_questions,
        failed_questions=failed_questions,
    )


@router.post("/question-bank/delete", response_model=AdminQuestionSyncResponse)
def delete_question_bank(req: AdminQuestionDeleteRequest) -> AdminQuestionSyncResponse:
    """从 Python 向量库删除 Admin 后台已下架、驳回或删除的题目。"""
    total_count = len(req.questions)
    if total_count == 0:
        return AdminQuestionSyncResponse(
            status="SUCCESS",
            total_count=0,
            success_count=0,
            failed_count=0,
        )

    success_questions: list[AdminQuestionSyncResult] = []
    failed_questions: list[AdminQuestionSyncResult] = []
    for item in req.questions:
        try:
            deleted = question_bank.delete_structured_questions([item.model_dump()])
            vector_store_id = deleted[0].get("vector_store_id") if deleted else None
            success_questions.append(
                AdminQuestionSyncResult(
                    id=item.id,
                    status="DELETED",
                    vector_store_id=str(vector_store_id) if vector_store_id else None,
                )
            )
        except Exception as exc:
            failed_questions.append(
                AdminQuestionSyncResult(
                    id=item.id,
                    status="FAILED",
                    error_message=str(exc),
                )
            )

    failed_count = len(failed_questions)
    success_count = len(success_questions)
    if failed_count == 0:
        status = "SUCCESS"
    elif success_count == 0:
        status = "FAILED"
    else:
        status = "PARTIAL_FAILED"

    return AdminQuestionSyncResponse(
        status=status,
        total_count=total_count,
        success_count=success_count,
        failed_count=failed_count,
        error_message=None if failed_count == 0 else "Some questions failed to delete",
        success_questions=success_questions,
        failed_questions=failed_questions,
    )
