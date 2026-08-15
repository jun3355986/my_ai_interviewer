import asyncio
import importlib
import json

from schemas.chat import UnifiedChatRequest


async def consume(response):
    chunks = []
    async for chunk in response.body_iterator:
        chunks.append(chunk.decode() if isinstance(chunk, bytes) else chunk)
    return "".join(chunks)


def snapshot_payload():
    return {
        "schema_version": 1,
        "turn_id": "turn-router",
        "branch_id": "branch-router",
        "lineage_id": "lineage-router",
        "branch_version": 2,
        "expected_tail_message_id": 9,
        "owner_user_id": 42,
        "username": "alice",
        "candidate_name": "Router Candidate",
        "resume_content": "resume",
        "job_requirements": "job",
        "current_stage": "technical_qna",
        "branch_status": 1,
        "project_questions_count": 2,
        "target_project_questions": 3,
        "current_followup_count": 0,
        "project_questions_pool": [],
        "technical_questions_pool": [],
        "messages": [
            {
                "id": 9,
                "owning_branch_id": "branch-router",
                "role": "ai",
                "content": "当前问题",
                "stage": "technical_qna",
                "message_type": "ai_question",
                "expects_response": True,
                "metadata": {},
                "sequence": 1,
                "path_order": 1,
            }
        ],
        "assessments": [],
    }


def load_router_with_fake_boundaries(monkeypatch):
    import api.interviewer as interviewer_module
    import services.question_bank as question_bank_module

    monkeypatch.setattr(interviewer_module, "Interviewer", lambda: object())
    monkeypatch.setattr(question_bank_module, "QuestionBank", lambda: object())
    return importlib.import_module("api.router")


def test_durable_chat_emits_existing_sse_contract_with_turn_correlation(monkeypatch):
    router_module = load_router_with_fake_boundaries(monkeypatch)
    durable_module = importlib.import_module("services.durable_turn")
    assert hasattr(router_module, "_get_durable_turn_processor")
    captured = {}

    class FakeProcessor:
        def process(self, **kwargs):
            captured.update(kwargs)
            return durable_module.DurableTurnResult(
                python_session_id="branch-router",
                next_stage="technical_qna",
                interview_complete=False,
                score=93,
                feedback="good",
                is_followup=True,
                next_question="下一道结构化问题",
                question={
                    "id": "q-next",
                    "text": "下一道结构化问题",
                    "media": [{"type": "image", "url": "https://example.test/q.png"}],
                },
                final_message="下一道结构化问题",
                post_turn_state=durable_module.DurablePostTurnState(
                    current_stage="technical_qna",
                    branch_status=1,
                    project_questions_count=2,
                    target_project_questions=3,
                    current_followup_count=0,
                    project_questions_pool=[],
                    technical_questions_pool=[{"id": "q-after-next"}],
                ),
            )

    monkeypatch.setattr(router_module, "_get_durable_turn_processor", lambda: FakeProcessor())
    response = router_module.chat_stream(UnifiedChatRequest(
        turn_id="turn-router",
        branch_snapshot=snapshot_payload(),
        session_id="stale-python-session",
        message="candidate answer",
        request_id="req-router",
        agent_run_id="agent-router",
        java_session_id="branch-router",
        user_id=42,
        username="alice",
        business_type="interview",
        entrypoint="turn_attempt",
    ))

    joined = asyncio.run(consume(response))

    assert captured == {
        "turn_id": "turn-router",
        "snapshot_payload": snapshot_payload(),
        "candidate_answer": "candidate answer",
    }
    assert joined.index("event: status") < joined.index("event: score")
    assert joined.index("event: score") < joined.index("event: question")
    assert joined.index("event: question") < joined.index("event: result")
    assert joined.index("event: result") < joined.index("event: done")
    payloads = [
        json.loads(line.removeprefix("data: "))
        for line in joined.splitlines()
        if line.startswith("data: ")
    ]
    assert all(
        payload["turn_id"] == "turn-router"
        for payload in payloads
        if "turn_id" in payload
    )
    assert '"id": "q-next"' in joined
    score_payload = next(payload for payload in payloads if payload.get("score") == 93)
    assert score_payload["is_followup"] is True
    result_payload = next(
        payload for payload in payloads if "post_turn_state" in payload
    )
    assert result_payload["post_turn_state"] == {
        "current_stage": "technical_qna",
        "branch_status": 1,
        "project_questions_count": 2,
        "target_project_questions": 3,
        "current_followup_count": 0,
        "project_questions_pool": [],
        "technical_questions_pool": [{"id": "q-after-next"}],
    }


def test_durable_chat_returns_sanitized_snapshot_and_idempotency_errors(monkeypatch):
    router_module = load_router_with_fake_boundaries(monkeypatch)
    reconstruction_module = importlib.import_module("services.branch_reconstruction")
    durable_module = importlib.import_module("services.durable_turn")

    errors = [
        reconstruction_module.SnapshotContractError(
            "UNSUPPORTED_SNAPSHOT_SCHEMA",
            "secret schema internals",
        ),
        durable_module.TurnIdempotencyConflict("secret prior payload"),
    ]
    for expected_code, failure in zip(
        ["UNSUPPORTED_SNAPSHOT_SCHEMA", "TURN_IDEMPOTENCY_CONFLICT"],
        errors,
    ):
        class FailingProcessor:
            def process(self, **kwargs):
                raise failure

        monkeypatch.setattr(
            router_module,
            "_get_durable_turn_processor",
            lambda: FailingProcessor(),
        )
        response = router_module.chat_stream(UnifiedChatRequest(
            turn_id="turn-router",
            branch_snapshot=snapshot_payload(),
            message="candidate answer",
            entrypoint="turn_attempt",
        ))
        joined = asyncio.run(consume(response))

        assert "event: error" in joined
        assert expected_code in joined
        assert "secret" not in joined


def test_durable_chat_sanitizes_unexpected_storage_error(monkeypatch):
    router_module = load_router_with_fake_boundaries(monkeypatch)

    class FailingProcessor:
        def process(self, **kwargs):
            raise RuntimeError("sqlite:////secret/path.db UNIQUE constraint failed")

    monkeypatch.setattr(
        router_module,
        "_get_durable_turn_processor",
        lambda: FailingProcessor(),
    )
    response = router_module.chat_stream(UnifiedChatRequest(
        turn_id="turn-router",
        branch_snapshot=snapshot_payload(),
        message="candidate answer",
        entrypoint="turn_attempt",
    ))
    joined = asyncio.run(consume(response))

    assert "event: error" in joined
    assert "TURN_PROCESSING_FAILED" in joined
    assert "secret" not in joined
    assert "sqlite" not in joined
    assert "constraint" not in joined
