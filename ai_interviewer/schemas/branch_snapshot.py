from typing import Any, Literal, Optional

from pydantic import BaseModel, Field, model_validator


SUPPORTED_BRANCH_SNAPSHOT_SCHEMA_VERSION = 1


class BranchSnapshotMessage(BaseModel):
    id: int
    owning_branch_id: str
    role: Literal["human", "ai", "system"]
    content: str
    stage: str
    message_type: str
    expects_response: bool = False
    metadata: dict[str, Any] = Field(default_factory=dict)
    sequence: int
    path_order: int


class BranchSnapshotAssessment(BaseModel):
    id: int
    owning_branch_id: str
    turn_id: Optional[str] = None
    question_message_id: int
    answer_message_id: int
    question_type: str
    question: str
    answer: str
    score: Optional[int] = None
    feedback: Optional[str] = None
    is_followup: bool = False
    path_order: int


class BranchSnapshot(BaseModel):
    schema_version: int
    turn_id: str
    branch_id: str
    lineage_id: str
    branch_version: int
    expected_tail_message_id: Optional[int] = None
    owner_user_id: int
    username: Optional[str] = None
    candidate_name: Optional[str] = None
    resume_content: Optional[str] = None
    job_requirements: Optional[str] = None
    current_stage: str
    branch_status: int
    project_questions_count: int = 0
    target_project_questions: int = 5
    current_followup_count: int = 0
    project_questions_pool: list[Any] = Field(default_factory=list)
    technical_questions_pool: list[Any] = Field(default_factory=list)
    messages: list[BranchSnapshotMessage] = Field(default_factory=list)
    assessments: list[BranchSnapshotAssessment] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_canonical_path(self):
        message_orders = [message.path_order for message in self.messages]
        if message_orders != sorted(message_orders) or len(message_orders) != len(set(message_orders)):
            raise ValueError("snapshot messages must have unique deterministic path_order")
        message_ids = [message.id for message in self.messages]
        if len(message_ids) != len(set(message_ids)):
            raise ValueError("snapshot messages must have unique ids")
        actual_tail = self.messages[-1].id if self.messages else None
        if actual_tail != self.expected_tail_message_id:
            raise ValueError("snapshot tail does not match expected_tail_message_id")

        assessment_orders = [assessment.path_order for assessment in self.assessments]
        if assessment_orders != sorted(assessment_orders) or len(assessment_orders) != len(set(assessment_orders)):
            raise ValueError("snapshot assessments must have unique deterministic path_order")
        message_id_set = set(message_ids)
        for assessment in self.assessments:
            if (
                assessment.question_message_id not in message_id_set
                or assessment.answer_message_id not in message_id_set
            ):
                raise ValueError("snapshot assessment is outside the canonical message path")
        return self
