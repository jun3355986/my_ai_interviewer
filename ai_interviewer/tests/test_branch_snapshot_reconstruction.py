import importlib
import importlib.util

import pytest


def snapshot_payload(**overrides):
    payload = {
        "schema_version": 1,
        "turn_id": "turn-snapshot-1",
        "branch_id": "branch-child",
        "lineage_id": "lineage-1",
        "branch_version": 7,
        "expected_tail_message_id": 20,
        "owner_user_id": 42,
        "username": "snapshot-user",
        "candidate_name": "Snapshot Candidate",
        "resume_content": "Java and distributed systems",
        "job_requirements": "Senior backend engineer",
        "current_stage": "technical_qna",
        "branch_status": 1,
        "project_questions_count": 1,
        "target_project_questions": 3,
        "current_followup_count": 2,
        "project_questions_pool": ["下一个项目问题"],
        "technical_questions_pool": [
            {
                "id": "tech-next",
                "text": "请解释线程池拒绝策略。",
                "question_type": "MULTIPLE_CHOICE",
                "options": ["Abort", "Retry"],
            }
        ],
        "messages": [
            {
                "id": 10,
                "owning_branch_id": "branch-root",
                "role": "ai",
                "content": "请介绍项目。",
                "stage": "project_qna",
                "message_type": "ai_question",
                "expects_response": True,
                "metadata": {},
                "sequence": 1,
                "path_order": 1,
            },
            {
                "id": 11,
                "owning_branch_id": "branch-root",
                "role": "human",
                "content": "我负责订单系统。",
                "stage": "project_qna",
                "message_type": "candidate_answer",
                "expects_response": False,
                "metadata": {},
                "sequence": 2,
                "path_order": 2,
            },
            {
                "id": 12,
                "owning_branch_id": "branch-child",
                "role": "ai",
                "content": "解释 JVM 内存模型。",
                "stage": "technical_qna",
                "message_type": "ai_question",
                "expects_response": True,
                "metadata": {"id": "tech-answered", "text": "解释 JVM 内存模型。"},
                "sequence": 1,
                "path_order": 3,
            },
            {
                "id": 13,
                "owning_branch_id": "branch-child",
                "role": "human",
                "content": "主内存与工作内存。",
                "stage": "technical_qna",
                "message_type": "candidate_answer",
                "expects_response": False,
                "metadata": {},
                "sequence": 2,
                "path_order": 4,
            },
            {
                "id": 20,
                "owning_branch_id": "branch-child",
                "role": "ai",
                "content": "选择正确的并发集合。",
                "stage": "technical_qna",
                "message_type": "ai_question",
                "expects_response": True,
                "metadata": {
                    "id": "tech-current",
                    "text": "选择正确的并发集合。",
                    "question_type": "MULTIPLE_CHOICE",
                    "options": ["HashMap", "ConcurrentHashMap"],
                },
                "sequence": 3,
                "path_order": 5,
            },
        ],
        "assessments": [
            {
                "id": 100,
                "owning_branch_id": "branch-root",
                "turn_id": "turn-root",
                "question_message_id": 10,
                "answer_message_id": 11,
                "question_type": "project_qna",
                "question": "请介绍项目。",
                "answer": "我负责订单系统。",
                "score": 86,
                "feedback": "结构完整",
                "is_followup": False,
                "path_order": 1,
            },
            {
                "id": 101,
                "owning_branch_id": "branch-child",
                "turn_id": "turn-tech",
                "question_message_id": 12,
                "answer_message_id": 13,
                "question_type": "technical_qna",
                "question": "解释 JVM 内存模型。",
                "answer": "主内存与工作内存。",
                "score": 91,
                "feedback": "准确",
                "is_followup": False,
                "path_order": 2,
            },
        ],
    }
    payload.update(overrides)
    return payload


def load_snapshot_api():
    assert importlib.util.find_spec("schemas.branch_snapshot") is not None
    assert importlib.util.find_spec("services.branch_reconstruction") is not None
    schema_module = importlib.import_module("schemas.branch_snapshot")
    reconstruction_module = importlib.import_module("services.branch_reconstruction")
    return schema_module, reconstruction_module


def test_reconstructs_stage_history_pools_counters_and_assessments_from_snapshot():
    schema_module, reconstruction_module = load_snapshot_api()
    snapshot = schema_module.BranchSnapshot.model_validate(snapshot_payload())

    session = reconstruction_module.reconstruct_session_from_snapshot(snapshot)

    assert session.session_id == "branch-child"
    assert session.candidate_name == "Snapshot Candidate"
    assert session.stage.value == "technical_qna"
    assert session.project_questions_count == 1
    assert session.target_project_questions == 3
    assert session.current_question_followup_count == 2
    assert session.project_questions_pool == ["下一个项目问题"]
    assert session.technical_questions_pool[0]["id"] == "tech-next"
    assert [message["content"] for message in session.history] == [
        "请介绍项目。",
        "我负责订单系统。",
        "解释 JVM 内存模型。",
        "主内存与工作内存。",
        "选择正确的并发集合。",
    ]
    assert session.history[-1]["question"]["id"] == "tech-current"
    assert [(qa.question, qa.answer, qa.score) for qa in session.project_qa_list] == [
        ("请介绍项目。", "我负责订单系统。", 86)
    ]
    assert [(qa.question, qa.answer, qa.score) for qa in session.technical_qa_list] == [
        ("解释 JVM 内存模型。", "主内存与工作内存。", 91)
    ]


def test_reconstruction_rejects_unsupported_snapshot_schema():
    schema_module, reconstruction_module = load_snapshot_api()
    snapshot = schema_module.BranchSnapshot.model_validate(
        snapshot_payload(schema_version=99)
    )

    with pytest.raises(reconstruction_module.SnapshotContractError) as caught:
        reconstruction_module.reconstruct_session_from_snapshot(snapshot)

    assert caught.value.code == "UNSUPPORTED_SNAPSHOT_SCHEMA"


def test_reconstruction_preserves_concluded_boundary():
    schema_module, reconstruction_module = load_snapshot_api()
    snapshot = schema_module.BranchSnapshot.model_validate(
        snapshot_payload(current_stage="concluded", branch_status=2)
    )

    session = reconstruction_module.reconstruct_session_from_snapshot(snapshot)

    assert session.stage.value == "concluded"
