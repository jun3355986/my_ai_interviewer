from copy import deepcopy

from schemas.branch_snapshot import (
    SUPPORTED_BRANCH_SNAPSHOT_SCHEMA_VERSION,
    BranchSnapshot,
)
from services.interview_session import (
    InterviewSession,
    InterviewStage,
    QuestionAnswer,
)


class SnapshotContractError(ValueError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def reconstruct_session_from_snapshot(snapshot: BranchSnapshot) -> InterviewSession:
    if snapshot.schema_version != SUPPORTED_BRANCH_SNAPSHOT_SCHEMA_VERSION:
        raise SnapshotContractError(
            "UNSUPPORTED_SNAPSHOT_SCHEMA",
            "Unsupported branch snapshot schema version",
        )
    try:
        stage = InterviewStage(snapshot.current_stage)
    except ValueError as exc:
        raise SnapshotContractError(
            "INVALID_BRANCH_SNAPSHOT",
            "Branch snapshot contains an unsupported interview stage",
        ) from exc

    history = []
    for message in snapshot.messages:
        restored = {
            "role": message.role,
            "content": message.content,
            "stage": message.stage,
            "message_type": message.message_type,
            "expects_response": message.expects_response,
            "java_message_id": message.id,
            "owning_branch_id": message.owning_branch_id,
            "path_order": message.path_order,
        }
        if message.metadata:
            restored["metadata"] = deepcopy(message.metadata)
            structured = message.metadata.get("question")
            if not isinstance(structured, dict) and isinstance(message.metadata.get("text"), str):
                structured = message.metadata
            if message.role == "ai" and isinstance(structured, dict):
                restored["question"] = deepcopy(structured)
            if message.role == "ai" and bool(message.metadata.get("is_followup", False)):
                restored["is_followup"] = True
        history.append(restored)

    project_qa_list = []
    technical_qa_list = []
    for assessment in snapshot.assessments:
        restored_qa = QuestionAnswer(
            question=assessment.question,
            answer=assessment.answer,
            score=assessment.score,
            feedback=assessment.feedback,
            is_followup=assessment.is_followup,
        )
        if "technical" in assessment.question_type.lower():
            technical_qa_list.append(restored_qa)
        else:
            project_qa_list.append(restored_qa)

    return InterviewSession(
        session_id=snapshot.branch_id,
        candidate_name=snapshot.candidate_name,
        resume_content=snapshot.resume_content,
        job_requirements=snapshot.job_requirements,
        stage=stage,
        history=history,
        project_qa_list=project_qa_list,
        technical_qa_list=technical_qa_list,
        project_questions_count=snapshot.project_questions_count,
        target_project_questions=snapshot.target_project_questions,
        project_questions_pool=deepcopy(snapshot.project_questions_pool),
        technical_questions_pool=deepcopy(snapshot.technical_questions_pool),
        current_question_followup_count=snapshot.current_followup_count,
    )
