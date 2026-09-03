from typing import Any, Dict, List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from core import runtime_config
from core.embeddings import get_embeddings
from core.model_provider import build_chat_llm
from services.question_bank import QuestionBank


router = APIRouter(prefix="/admin", tags=["admin"])
question_bank = QuestionBank()


class RuntimeConfigUpdateRequest(BaseModel):
    """管理端运行时配置更新。confirm_collection_switch 用于向量集合切换的二次确认。"""

    confirm_collection_switch: bool = False
    config: Dict[str, Any] = Field(default_factory=dict)


class AdminQuestionSyncItem(BaseModel):
    id: int
    question_text: str = Field(..., alias="question_text")
    answer_reference: Optional[str] = Field(default=None, alias="answer_reference")
    question_type: Optional[str] = Field(default=None, alias="question_type")
    difficulty: Optional[str] = None
    tags: List[str] = Field(default_factory=list)
    skill_area: Optional[str] = Field(default=None, alias="skill_area")
    media: List[dict[str, Any]] = Field(default_factory=list)


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


@router.get("/runtime-config")
def get_runtime_config() -> Dict[str, Any]:
    """当前生效的模型与检索运行时配置（脱敏：不含任何凭证）。"""
    return runtime_config.snapshot()


@router.put("/runtime-config")
def update_runtime_config(req: RuntimeConfigUpdateRequest) -> Dict[str, Any]:
    """
    在线更新模型/检索运行时配置，立即对后续 LLM、embedding 与题库检索生效。

    环境变量仍是基线：进程重启后回到环境变量配置；API key 不在可覆盖范围。
    向量集合切换要求 confirm_collection_switch=true 二次确认。
    """
    try:
        normalized = runtime_config.validate_and_normalize(
            req.config,
            confirm_collection_switch=req.confirm_collection_switch,
        )
    except runtime_config.RuntimeConfigError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    runtime_config.apply_overrides(normalized)

    new_collection = normalized.get(runtime_config.KEY_VECTOR_COLLECTION)
    if new_collection:
        try:
            question_bank.switch_collection(new_collection)
        except Exception as exc:
            # 集合切换失败时回退该键的覆盖，保证配置视图与实际行为一致。
            runtime_config.clear_override(runtime_config.KEY_VECTOR_COLLECTION)
            raise HTTPException(
                status_code=500,
                detail=f"向量集合切换失败，该配置已回退: {exc}",
            )

    return {"status": "ok", "applied_keys": sorted(normalized.keys()), "config": runtime_config.snapshot()}


@router.post("/runtime-config/test")
def test_runtime_config() -> Dict[str, Any]:
    """
    用当前生效配置真实连通性测试：一次最小 chat 调用 + 一次 embedding 调用，
    分别返回模型、延迟与错误信息。
    """
    import time

    chat_result: Dict[str, Any]
    embedding_result: Dict[str, Any]

    started = time.perf_counter()
    try:
        llm = build_chat_llm().bind(max_tokens=8)
        llm.invoke("连通性测试，请只回复 ok")
        chat_result = {
            "ok": True,
            "model": runtime_config.chat_model(),
            "latency_ms": int((time.perf_counter() - started) * 1000),
        }
    except Exception as exc:
        chat_result = {
            "ok": False,
            "model": runtime_config.chat_model(),
            "latency_ms": int((time.perf_counter() - started) * 1000),
            "error": str(exc)[:400],
        }

    started = time.perf_counter()
    try:
        embeddings = get_embeddings()
        vector = embeddings.embed_query("ping")
        embedding_result = {
            "ok": True,
            "model": runtime_config.embedding_model(),
            "dimension": len(vector) if vector else 0,
            "latency_ms": int((time.perf_counter() - started) * 1000),
        }
    except Exception as exc:
        embedding_result = {
            "ok": False,
            "model": runtime_config.embedding_model(),
            "latency_ms": int((time.perf_counter() - started) * 1000),
            "error": str(exc)[:400],
        }

    return {
        "chat": chat_result,
        "embedding": embedding_result,
        "all_ok": chat_result["ok"] and embedding_result["ok"],
    }
