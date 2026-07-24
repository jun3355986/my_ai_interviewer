import importlib
import importlib.util
from copy import deepcopy
from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
from datetime import datetime, timedelta
from threading import Barrier, Event

import pytest
from sqlalchemy import update

from services.database import InterviewRecord, TurnLedgerRecord
from services.interview_session import InterviewSession, InterviewStage, SessionManager


def snapshot_payload(
    *,
    turn_id="turn-1",
    branch_version=3,
    tail_id=20,
    stage="project_qna",
    project_count=0,
    target_project_count=3,
    followup_count=0,
    project_pool=None,
    technical_pool=None,
):
    return {
        "schema_version": 1,
        "turn_id": turn_id,
        "branch_id": "branch-1",
        "lineage_id": "lineage-1",
        "branch_version": branch_version,
        "expected_tail_message_id": tail_id,
        "owner_user_id": 7,
        "username": "tester",
        "candidate_name": "Authoritative Candidate",
        "resume_content": "authoritative resume",
        "job_requirements": "authoritative job",
        "current_stage": stage,
        "branch_status": 2 if stage == "concluded" else 1,
        "project_questions_count": project_count,
        "target_project_questions": target_project_count,
        "current_followup_count": followup_count,
        "project_questions_pool": project_pool or ["Java snapshot 的下一道项目题"],
        "technical_questions_pool": technical_pool or [],
        "messages": [
            {
                "id": tail_id,
                "owning_branch_id": "branch-1",
                "role": "ai",
                "content": "Java snapshot 当前问题",
                "stage": stage,
                "message_type": "final_summary" if stage == "concluded" else "ai_question",
                "expects_response": stage != "concluded",
                "metadata": {},
                "sequence": 1,
                "path_order": 1,
            }
        ],
        "assessments": [],
    }


class FakeInterviewer:
    def __init__(self, *, followup_reason=None, fail=False):
        self.followup_reason = followup_reason
        self.fail = fail
        self.evaluate_calls = 0
        self.histories_seen = []

    def evaluate_answer(self, question, answer, resume_content):
        self.evaluate_calls += 1
        if self.fail:
            raise RuntimeError("secret provider failure")
        return 88, "deterministic feedback", self.followup_reason

    def generate_followup_question(self, question, answer, reason):
        return "请进一步说明一致性保证。"


class BlockingInterviewer(FakeInterviewer):
    def __init__(self, *, fail_after_release=False):
        super().__init__()
        self.fail_after_release = fail_after_release
        self.started = Event()
        self.release = Event()

    def evaluate_answer(self, question, answer, resume_content):
        self.evaluate_calls += 1
        self.started.set()
        if not self.release.wait(timeout=5):
            raise AssertionError("test did not release interviewer")
        if self.fail_after_release:
            raise RuntimeError("secret late owner failure")
        return 88, "deterministic feedback", None


def load_durable_api():
    assert importlib.util.find_spec("services.durable_turn") is not None
    assert hasattr(importlib.import_module("services.database"), "InterviewDatabase")
    return importlib.import_module("services.durable_turn")


def processor(tmp_path, interviewer, *, database_path=None):
    durable_module = load_durable_api()
    database_module = importlib.import_module("services.database")
    database = database_module.InterviewDatabase(
        database_path or tmp_path / "durable-turn.sqlite3"
    )
    manager = SessionManager(database=database)
    return (
        durable_module.DurableTurnProcessor(
            database=database,
            session_manager=manager,
            interviewer=interviewer,
        ),
        manager,
        database,
        durable_module,
    )


def test_processes_from_snapshot_without_memory_or_prior_sqlite_session(tmp_path):
    fake = FakeInterviewer()
    turn_processor, manager, _, _ = processor(tmp_path, fake)

    result = turn_processor.process(
        turn_id="turn-1",
        snapshot_payload=snapshot_payload(),
        candidate_answer="snapshot answer",
    )

    assert result.next_stage == "project_qna"
    assert result.score == 88
    assert result.next_question == "Java snapshot 的下一道项目题"
    assert result.post_turn_state.current_stage == "project_qna"
    assert result.post_turn_state.branch_status == 1
    assert result.post_turn_state.project_questions_count == 1
    assert result.post_turn_state.target_project_questions == 3
    assert result.post_turn_state.current_followup_count == 0
    assert result.post_turn_state.project_questions_pool == []
    assert result.post_turn_state.technical_questions_pool == []
    assert fake.evaluate_calls == 1
    saved = manager.get_session("branch-1")
    assert saved is not None
    assert saved.candidate_name == "Authoritative Candidate"
    assert [message["content"] for message in saved.history][-2:] == [
        "snapshot answer",
        "Java snapshot 的下一道项目题",
    ]


def test_snapshot_overrides_stale_memory_and_sqlite_state(tmp_path):
    fake = FakeInterviewer()
    turn_processor, manager, database, _ = processor(tmp_path, fake)
    stale = InterviewSession(
        session_id="branch-1",
        candidate_name="STALE",
        resume_content="stale resume",
        stage=InterviewStage.TECHNICAL_QNA,
        history=[{"role": "ai", "content": "STALE MESSAGE"}],
    )
    database.save_session(stale)
    manager.sessions["branch-1"] = stale

    turn_processor.process(
        turn_id="turn-1",
        snapshot_payload=snapshot_payload(),
        candidate_answer="fresh answer",
    )

    saved = manager.get_session("branch-1")
    assert saved.candidate_name == "Authoritative Candidate"
    assert saved.resume_content == "authoritative resume"
    assert all(message["content"] != "STALE MESSAGE" for message in saved.history)


def test_exact_duplicate_replays_without_second_interviewer_call_and_survives_restart(tmp_path):
    database_path = tmp_path / "restart-ledger.sqlite3"
    first_fake = FakeInterviewer()
    first, _, _, _ = processor(tmp_path, first_fake, database_path=database_path)
    first_result = first.process(
        turn_id="turn-1",
        snapshot_payload=snapshot_payload(),
        candidate_answer="same answer",
    )
    replay = first.process(
        turn_id="turn-1",
        snapshot_payload=snapshot_payload(),
        candidate_answer="same answer",
    )

    second_fake = FakeInterviewer()
    restarted, _, _, _ = processor(tmp_path, second_fake, database_path=database_path)
    replay_after_restart = restarted.process(
        turn_id="turn-1",
        snapshot_payload=snapshot_payload(),
        candidate_answer="same answer",
    )

    assert replay == first_result
    assert replay_after_restart == first_result
    assert first_fake.evaluate_calls == 1
    assert second_fake.evaluate_calls == 0


def test_next_turn_after_restart_uses_prior_authoritative_post_turn_state(tmp_path):
    database_path = tmp_path / "two-turn-restart.sqlite3"
    first_fake = FakeInterviewer()
    first, _, _, _ = processor(tmp_path / "first", first_fake, database_path=database_path)
    first_result = first.process(
        turn_id="turn-1",
        snapshot_payload=snapshot_payload(
            turn_id="turn-1",
            project_pool=["第二道项目题", "第三道项目题"],
        ),
        candidate_answer="first answer",
    )
    assert first_result.next_question == "第二道项目题"
    assert first_result.post_turn_state.project_questions_count == 1
    assert first_result.post_turn_state.project_questions_pool == ["第三道项目题"]

    second_snapshot = snapshot_payload(
        turn_id="turn-2",
        branch_version=4,
        tail_id=21,
        stage=first_result.post_turn_state.current_stage,
        project_count=first_result.post_turn_state.project_questions_count,
        target_project_count=first_result.post_turn_state.target_project_questions,
        followup_count=first_result.post_turn_state.current_followup_count,
        project_pool=first_result.post_turn_state.project_questions_pool,
        technical_pool=first_result.post_turn_state.technical_questions_pool,
    )
    second_fake = FakeInterviewer()
    restarted, _, _, _ = processor(
        tmp_path / "second",
        second_fake,
        database_path=database_path,
    )
    second_result = restarted.process(
        turn_id="turn-2",
        snapshot_payload=second_snapshot,
        candidate_answer="second answer",
    )

    assert second_result.next_question == "第三道项目题"
    assert second_result.post_turn_state.project_questions_count == 2
    assert second_result.post_turn_state.project_questions_pool == []
    assert first_fake.evaluate_calls == 1
    assert second_fake.evaluate_calls == 1


@pytest.mark.parametrize(
    ("changed_answer", "snapshot_change"),
    [
        ("changed answer", {}),
        ("same answer", {"branch_version": 4}),
        ("same answer", {"tail_id": 21}),
        ("same answer", {"candidate_name": "Changed Snapshot Candidate"}),
    ],
)
def test_reused_turn_id_with_changed_payload_is_rejected(
    tmp_path,
    changed_answer,
    snapshot_change,
):
    fake = FakeInterviewer()
    turn_processor, _, _, durable_module = processor(tmp_path, fake)
    original = snapshot_payload()
    turn_processor.process(
        turn_id="turn-1",
        snapshot_payload=original,
        candidate_answer="same answer",
    )
    changed = snapshot_payload(
        branch_version=snapshot_change.get("branch_version", 3),
        tail_id=snapshot_change.get("tail_id", 20),
    )
    changed.update({key: value for key, value in snapshot_change.items() if key not in {"branch_version", "tail_id"}})

    with pytest.raises(durable_module.TurnIdempotencyConflict):
        turn_processor.process(
            turn_id="turn-1",
            snapshot_payload=changed,
            candidate_answer=changed_answer,
        )

    assert fake.evaluate_calls == 1


def test_failure_does_not_complete_ledger_or_leak_partial_session_and_can_retry(tmp_path):
    database_path = tmp_path / "failure-retry.sqlite3"
    failing_fake = FakeInterviewer(fail=True)
    failing, failing_manager, _, durable_module = processor(
        tmp_path,
        failing_fake,
        database_path=database_path,
    )

    with pytest.raises(durable_module.DurableTurnProcessingError):
        failing.process(
            turn_id="turn-1",
            snapshot_payload=snapshot_payload(),
            candidate_answer="answer that fails",
        )

    assert "branch-1" not in failing_manager.sessions
    assert failing.ledger.find("turn-1") is None

    succeeding_fake = FakeInterviewer()
    retry, retry_manager, _, _ = processor(
        tmp_path,
        succeeding_fake,
        database_path=database_path,
    )
    result = retry.process(
        turn_id="turn-1",
        snapshot_payload=snapshot_payload(),
        candidate_answer="answer that fails",
    )

    assert result.score == 88
    assert succeeding_fake.evaluate_calls == 1
    assert retry_manager.get_session("branch-1").history[0]["content"] == "Java snapshot 当前问题"


def test_cache_upsert_failure_rolls_back_completed_ledger_and_can_retry(
    tmp_path,
    monkeypatch,
):
    database_path = tmp_path / "cache-upsert-rollback.sqlite3"
    first_fake = FakeInterviewer()
    first, first_manager, database, durable_module = processor(
        tmp_path / "first",
        first_fake,
        database_path=database_path,
    )
    original_upsert = database.upsert_session

    def fail_after_cache_write(db, session):
        original_upsert(db, session)
        raise RuntimeError("forced cache upsert failure")

    monkeypatch.setattr(database, "upsert_session", fail_after_cache_write)

    with pytest.raises(durable_module.DurableTurnProcessingError):
        first.process(
            turn_id="turn-cache-upsert-failure",
            snapshot_payload=snapshot_payload(turn_id="turn-cache-upsert-failure"),
            candidate_answer="answer before cache failure",
        )

    assert first.ledger.find("turn-cache-upsert-failure") is None
    assert "branch-1" not in first_manager.sessions
    with database.session() as db:
        assert db.get(InterviewRecord, "branch-1") is None

    monkeypatch.setattr(database, "upsert_session", original_upsert)
    retry_fake = FakeInterviewer()
    retry, retry_manager, _, _ = processor(
        tmp_path / "retry",
        retry_fake,
        database_path=database_path,
    )
    result = retry.process(
        turn_id="turn-cache-upsert-failure",
        snapshot_payload=snapshot_payload(turn_id="turn-cache-upsert-failure"),
        candidate_answer="answer before cache failure",
    )

    assert result.score == 88
    assert retry.ledger.find("turn-cache-upsert-failure").status == "COMPLETED"
    assert retry_manager.get_session("branch-1") is not None
    assert first_fake.evaluate_calls == 1
    assert retry_fake.evaluate_calls == 1


def test_stale_processing_ledger_row_is_recovered_from_immutable_snapshot(tmp_path):
    fake = FakeInterviewer()
    turn_processor, _, database, _ = processor(tmp_path, fake)
    database_module = importlib.import_module("services.database")
    payload = snapshot_payload()
    input_hash = turn_processor.compute_input_hash(payload, "stale retry answer")
    with database.session() as db:
        db.add(database_module.TurnLedgerRecord(
            turn_id="turn-1",
            input_hash=input_hash,
            status="PROCESSING",
            owner_token="dead-process",
            created_at=datetime.now() - timedelta(hours=1),
            updated_at=datetime.now() - timedelta(hours=1),
        ))
        db.commit()

    result = turn_processor.process(
        turn_id="turn-1",
        snapshot_payload=payload,
        candidate_answer="stale retry answer",
    )

    assert result.score == 88
    assert fake.evaluate_calls == 1
    assert turn_processor.ledger.find("turn-1").status == "COMPLETED"


def test_supported_in_flight_duration_cannot_be_stale_taken_over(tmp_path):
    fake = FakeInterviewer()
    turn_processor, _, database, durable_module = processor(tmp_path, fake)
    payload = snapshot_payload()
    input_hash = turn_processor.compute_input_hash(payload, "long-running answer")
    with database.session() as db:
        db.add(TurnLedgerRecord(
            turn_id="turn-1",
            input_hash=input_hash,
            status="PROCESSING",
            owner_token="legitimate-owner",
            created_at=datetime.now() - timedelta(minutes=9),
            updated_at=datetime.now() - timedelta(minutes=9),
        ))
        db.commit()

    with pytest.raises(durable_module.TurnProcessingInProgress):
        turn_processor.ledger.acquire("turn-1", input_hash)

    assert turn_processor.ledger.find("turn-1").owner_token == "legitimate-owner"


def test_ledger_rejects_lease_not_longer_than_supported_processing_timeout(tmp_path):
    _, _, database, durable_module = processor(tmp_path, FakeInterviewer())

    with pytest.raises(ValueError, match="supported processing timeout"):
        durable_module.TurnLedgerRepository(
            database,
            stale_after=durable_module.SUPPORTED_PROCESSING_TIMEOUT,
        )


def test_late_old_owner_success_cannot_publish_cache_or_complete_winner(tmp_path):
    database_path = tmp_path / "late-success.sqlite3"
    interviewer = BlockingInterviewer()
    turn_processor, manager, database, durable_module = processor(
        tmp_path,
        interviewer,
        database_path=database_path,
    )
    payload = snapshot_payload()

    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(
            turn_processor.process,
            turn_id="turn-1",
            snapshot_payload=payload,
            candidate_answer="late success",
        )
        assert interviewer.started.wait(timeout=5)
        with database.session() as db:
            record = db.get(TurnLedgerRecord, "turn-1")
            assert record is not None
            db.execute(
                update(TurnLedgerRecord)
                .where(TurnLedgerRecord.turn_id == "turn-1")
                .values(owner_token="winner-owner", updated_at=datetime.now())
            )
            db.commit()
        interviewer.release.set()
        with pytest.raises(durable_module.TurnIdempotencyConflict):
            future.result(timeout=5)

    assert "branch-1" not in manager.sessions
    with database.session() as db:
        assert db.get(InterviewRecord, "branch-1") is None
        winner = db.get(TurnLedgerRecord, "turn-1")
        assert winner is not None
        assert winner.status == "PROCESSING"
        assert winner.owner_token == "winner-owner"


def test_late_old_owner_failure_cannot_delete_takeover_winner(tmp_path):
    interviewer = BlockingInterviewer(fail_after_release=True)
    turn_processor, _, database, durable_module = processor(tmp_path, interviewer)

    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(
            turn_processor.process,
            turn_id="turn-1",
            snapshot_payload=snapshot_payload(),
            candidate_answer="late failure",
        )
        assert interviewer.started.wait(timeout=5)
        with database.session() as db:
            db.execute(
                update(TurnLedgerRecord)
                .where(TurnLedgerRecord.turn_id == "turn-1")
                .values(owner_token="winner-owner", updated_at=datetime.now())
            )
            db.commit()
        interviewer.release.set()
        with pytest.raises(durable_module.DurableTurnProcessingError):
            future.result(timeout=5)

    winner = turn_processor.ledger.find("turn-1")
    assert winner is not None
    assert winner.status == "PROCESSING"
    assert winner.owner_token == "winner-owner"


def test_failure_delete_uses_single_owner_fenced_statement(tmp_path):
    fake = FakeInterviewer()
    turn_processor, _, database, _ = processor(tmp_path, fake)
    payload = snapshot_payload()
    input_hash = turn_processor.compute_input_hash(payload, "failure race")
    acquisition = turn_processor.ledger.acquire("turn-1", input_hash)
    old_owner = acquisition.owner_token
    assert old_owner is not None

    with database.session() as db:
        db.execute(
            update(TurnLedgerRecord)
            .where(TurnLedgerRecord.turn_id == "turn-1")
            .values(owner_token="winner-owner", updated_at=datetime.now())
        )
        db.commit()

    assert turn_processor.ledger.fail("turn-1", old_owner) is False

    winner = turn_processor.ledger.find("turn-1")
    assert winner is not None
    assert winner.owner_token == "winner-owner"


def test_acquisition_storage_failure_is_wrapped_without_internal_details(tmp_path):
    durable_module = load_durable_api()
    fake = FakeInterviewer()
    turn_processor, _, database, _ = processor(tmp_path, fake)

    class BrokenDatabase:
        def session(self):
            raise RuntimeError("sqlite:////secret/path.db SQL constraint details")

    turn_processor.ledger = durable_module.TurnLedgerRepository(BrokenDatabase())

    with pytest.raises(durable_module.DurableTurnProcessingError) as failure:
        turn_processor.process(
            turn_id="turn-1",
            snapshot_payload=snapshot_payload(),
            candidate_answer="storage failure",
        )

    assert "secret" not in str(failure.value)
    assert "sqlite" not in str(failure.value)
    assert "constraint" not in str(failure.value)


@pytest.mark.parametrize("stale_owner", [False, True])
def test_two_connections_atomically_acquire_same_turn_only_once(tmp_path, stale_owner):
    durable_module = load_durable_api()
    database_module = importlib.import_module("services.database")
    database_path = tmp_path / ("stale-race.sqlite3" if stale_owner else "insert-race.sqlite3")
    interviewer = BlockingInterviewer()
    first, _, first_database, _ = processor(
        tmp_path / "first",
        interviewer,
        database_path=database_path,
    )
    second, _, _, _ = processor(
        tmp_path / "second",
        interviewer,
        database_path=database_path,
    )
    payload = snapshot_payload()
    if stale_owner:
        input_hash = first.compute_input_hash(payload, "racing answer")
        with first_database.session() as db:
            db.add(database_module.TurnLedgerRecord(
                turn_id="turn-1",
                input_hash=input_hash,
                status="PROCESSING",
                owner_token="dead-owner",
                created_at=datetime.now() - timedelta(hours=1),
                updated_at=datetime.now() - timedelta(hours=1),
            ))
            db.commit()

    start = Barrier(2)

    def run(turn_processor):
        start.wait(timeout=5)
        return turn_processor.process(
            turn_id="turn-1",
            snapshot_payload=payload,
            candidate_answer="racing answer",
        )

    with ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(run, first), executor.submit(run, second)]
        assert interviewer.started.wait(timeout=5)
        completed, _ = wait(futures, timeout=5, return_when=FIRST_COMPLETED)
        assert len(completed) == 1
        losing = completed.pop()
        with pytest.raises(durable_module.TurnProcessingInProgress):
            losing.result()
        interviewer.release.set()
        winner = next(future for future in futures if future is not losing)
        assert winner.result(timeout=5).score == 88

    assert interviewer.evaluate_calls == 1
    replay = second.process(
        turn_id="turn-1",
        snapshot_payload=payload,
        candidate_answer="racing answer",
    )
    assert replay.score == 88
    assert interviewer.evaluate_calls == 1


def test_reconstructed_followup_technical_and_concluded_boundaries(tmp_path):
    followup_fake = FakeInterviewer(followup_reason="needs depth")
    followup_processor, followup_manager, _, _ = processor(
        tmp_path / "followup",
        followup_fake,
    )
    followup_result = followup_processor.process(
        turn_id="turn-followup",
        snapshot_payload=snapshot_payload(turn_id="turn-followup", followup_count=1),
        candidate_answer="followup answer",
    )
    assert followup_result.next_question == "请进一步说明一致性保证。"
    assert followup_manager.get_session("branch-1").current_question_followup_count == 2

    technical_fake = FakeInterviewer()
    technical_processor, _, _, _ = processor(tmp_path / "technical", technical_fake)
    structured = {
        "id": "tech-next",
        "text": "选择线程安全集合。",
        "question_type": "MULTIPLE_CHOICE",
        "options": ["HashMap", "ConcurrentHashMap"],
    }
    technical_result = technical_processor.process(
        turn_id="turn-technical",
        snapshot_payload=snapshot_payload(
            turn_id="turn-technical",
            stage="technical_qna",
            technical_pool=[structured],
            project_pool=[],
        ),
        candidate_answer="ConcurrentHashMap",
    )
    assert technical_result.question["id"] == "tech-next"
    assert technical_result.next_stage == "technical_qna"

    concluded_fake = FakeInterviewer()
    concluded_processor, _, _, _ = processor(tmp_path / "concluded", concluded_fake)
    concluded_result = concluded_processor.process(
        turn_id="turn-concluded",
        snapshot_payload=snapshot_payload(
            turn_id="turn-concluded",
            stage="concluded",
            project_pool=[],
        ),
        candidate_answer="ignored",
    )
    assert concluded_result.next_stage == "concluded"
    assert concluded_result.interview_complete is True
    assert concluded_fake.evaluate_calls == 0
