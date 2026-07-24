import hashlib
import json
import uuid
from copy import deepcopy
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Any, Optional

from pydantic import BaseModel, ValidationError
from sqlalchemy import delete, update
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.exc import IntegrityError

from schemas.branch_snapshot import BranchSnapshot
from services.branch_reconstruction import (
    SnapshotContractError,
    reconstruct_session_from_snapshot,
)
from services.database import InterviewDatabase, TurnLedgerRecord
from services.interview_service import InterviewService
from services.interview_session import InterviewStage, SessionManager


class TurnIdempotencyConflict(ValueError):
    code = "TURN_IDEMPOTENCY_CONFLICT"


class TurnProcessingInProgress(ValueError):
    code = "TURN_PROCESSING_IN_PROGRESS"


class DurableTurnProcessingError(RuntimeError):
    code = "TURN_PROCESSING_FAILED"


SUPPORTED_PROCESSING_TIMEOUT = timedelta(minutes=10)
DEFAULT_STALE_AFTER = timedelta(minutes=15)


class DurablePostTurnState(BaseModel):
    current_stage: str
    branch_status: int
    project_questions_count: int
    target_project_questions: int
    current_followup_count: int
    project_questions_pool: list[Any]
    technical_questions_pool: list[Any]


class DurableTurnResult(BaseModel):
    python_session_id: str
    next_stage: str
    interview_complete: bool
    score: Optional[int] = None
    feedback: Optional[str] = None
    next_question: Optional[str] = None
    question: Optional[dict[str, Any]] = None
    final_message: Optional[str] = None
    post_turn_state: DurablePostTurnState


@dataclass(frozen=True)
class LedgerAcquisition:
    owner_token: Optional[str] = None
    replay_result: Optional[DurableTurnResult] = None


class TurnLedgerRepository:
    def __init__(
        self,
        database: InterviewDatabase,
        *,
        stale_after: timedelta = DEFAULT_STALE_AFTER,
    ):
        if stale_after <= SUPPORTED_PROCESSING_TIMEOUT:
            raise ValueError(
                "stale_after must be longer than the supported processing timeout"
            )
        self.database = database
        self.stale_after = stale_after

    def acquire(self, turn_id: str, input_hash: str) -> LedgerAcquisition:
        try:
            return self._acquire(turn_id, input_hash)
        except (TurnIdempotencyConflict, TurnProcessingInProgress, DurableTurnProcessingError):
            raise
        except Exception as exc:
            raise DurableTurnProcessingError("Durable turn storage failed") from exc

    def _acquire(self, turn_id: str, input_hash: str) -> LedgerAcquisition:
        now = datetime.now()
        owner_token = str(uuid.uuid4())
        with self.database.session() as db:
            try:
                inserted = db.execute(
                    sqlite_insert(TurnLedgerRecord)
                    .values(
                        turn_id=turn_id,
                        input_hash=input_hash,
                        status="PROCESSING",
                        owner_token=owner_token,
                        created_at=now,
                        updated_at=now,
                    )
                    .on_conflict_do_nothing(index_elements=["turn_id"])
                )
                db.commit()
            except IntegrityError:
                db.rollback()
                inserted = None
            if inserted is not None and inserted.rowcount == 1:
                return LedgerAcquisition(owner_token=owner_token)

        return self._acquire_existing(turn_id, input_hash, now)

    def _acquire_existing(
        self,
        turn_id: str,
        input_hash: str,
        now: datetime,
    ) -> LedgerAcquisition:
        with self.database.session() as db:
            record = db.get(TurnLedgerRecord, turn_id)
            if record is None:
                # A conflicting transaction may have rolled back after the insert attempt.
                return self.acquire(turn_id, input_hash)

            if record.input_hash != input_hash:
                raise TurnIdempotencyConflict(
                    "turn_id was already used with a different durable input"
                )
            if record.status == "COMPLETED":
                return LedgerAcquisition(
                    replay_result=DurableTurnResult.model_validate(record.result_payload)
                )
            if record.status != "PROCESSING":
                raise TurnIdempotencyConflict("turn ledger is in an unsupported state")
            if record.updated_at > now - self.stale_after:
                raise TurnProcessingInProgress("turn is already processing")

            owner_token = str(uuid.uuid4())
            previous_owner = record.owner_token
            previous_updated_at = record.updated_at
            taken_over = db.execute(
                update(TurnLedgerRecord)
                .where(
                    TurnLedgerRecord.turn_id == turn_id,
                    TurnLedgerRecord.input_hash == input_hash,
                    TurnLedgerRecord.status == "PROCESSING",
                    TurnLedgerRecord.owner_token == previous_owner,
                    TurnLedgerRecord.updated_at == previous_updated_at,
                    TurnLedgerRecord.updated_at <= now - self.stale_after,
                )
                .values(owner_token=owner_token, updated_at=now)
            )
            db.commit()
            if taken_over.rowcount == 1:
                return LedgerAcquisition(owner_token=owner_token)

        # Another connection won the compare-and-swap. Re-read its authoritative state.
        return self._acquire_existing(turn_id, input_hash, datetime.now())

    def complete(
        self,
        turn_id: str,
        owner_token: str,
        result: DurableTurnResult,
        session=None,
    ):
        try:
            with self.database.session() as db:
                completed = db.execute(
                    update(TurnLedgerRecord)
                    .where(
                        TurnLedgerRecord.turn_id == turn_id,
                        TurnLedgerRecord.status == "PROCESSING",
                        TurnLedgerRecord.owner_token == owner_token,
                    )
                    .values(
                        status="COMPLETED",
                        result_payload=result.model_dump(mode="json"),
                        owner_token=None,
                        updated_at=datetime.now(),
                    )
                )
                if completed.rowcount != 1:
                    db.rollback()
                    raise TurnIdempotencyConflict("turn processing ownership changed")
                if session is not None:
                    self.database.upsert_session(db, session)
                db.commit()
        except TurnIdempotencyConflict:
            raise
        except Exception as exc:
            raise DurableTurnProcessingError("Durable turn storage failed") from exc

    def fail(self, turn_id: str, owner_token: str) -> bool:
        try:
            with self.database.session() as db:
                deleted = db.execute(
                    delete(TurnLedgerRecord).where(
                        TurnLedgerRecord.turn_id == turn_id,
                        TurnLedgerRecord.status == "PROCESSING",
                        TurnLedgerRecord.owner_token == owner_token,
                    )
                )
                db.commit()
                return deleted.rowcount == 1
        except DurableTurnProcessingError:
            raise
        except Exception as exc:
            raise DurableTurnProcessingError("Durable turn storage failed") from exc

    def find(self, turn_id: str) -> Optional[TurnLedgerRecord]:
        try:
            with self.database.session() as db:
                return db.get(TurnLedgerRecord, turn_id)
        except DurableTurnProcessingError:
            raise
        except Exception as exc:
            raise DurableTurnProcessingError("Durable turn storage failed") from exc


class DurableTurnProcessor:
    def __init__(
        self,
        *,
        database: InterviewDatabase,
        session_manager: SessionManager,
        interviewer,
        ledger: Optional[TurnLedgerRepository] = None,
    ):
        self.database = database
        self.session_manager = session_manager
        self.interviewer = interviewer
        self.ledger = ledger or TurnLedgerRepository(database)

    def compute_input_hash(
        self,
        snapshot_payload: dict[str, Any],
        candidate_answer: str,
    ) -> str:
        snapshot = self._parse_snapshot(snapshot_payload)
        canonical = json.dumps(
            {
                "candidate_answer": candidate_answer,
                "snapshot": snapshot.model_dump(mode="json"),
            },
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
        )
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    def process(
        self,
        *,
        turn_id: str,
        snapshot_payload: dict[str, Any],
        candidate_answer: str,
    ) -> DurableTurnResult:
        snapshot = self._parse_snapshot(snapshot_payload)
        if snapshot.turn_id != turn_id:
            raise SnapshotContractError(
                "INVALID_BRANCH_SNAPSHOT",
                "Branch snapshot turn_id does not match the request",
            )
        input_hash = self.compute_input_hash(snapshot_payload, candidate_answer)
        try:
            acquisition = self.ledger.acquire(turn_id, input_hash)
        except (TurnIdempotencyConflict, TurnProcessingInProgress, DurableTurnProcessingError):
            raise
        except Exception as exc:
            raise DurableTurnProcessingError("Durable turn processing failed") from exc
        if acquisition.replay_result is not None:
            return acquisition.replay_result

        owner_token = acquisition.owner_token
        if owner_token is None:
            raise DurableTurnProcessingError("Turn processing ownership was not acquired")
        try:
            working_session = reconstruct_session_from_snapshot(snapshot)
            result = self._process_working_session(working_session, candidate_answer)
            self.ledger.complete(turn_id, owner_token, result, working_session)
            self.session_manager.sessions[snapshot.branch_id] = working_session
            return result
        except (SnapshotContractError, TurnIdempotencyConflict, TurnProcessingInProgress):
            self._release_failed_owner(turn_id, owner_token)
            raise
        except DurableTurnProcessingError:
            self._release_failed_owner(turn_id, owner_token)
            raise
        except Exception as exc:
            self._release_failed_owner(turn_id, owner_token)
            raise DurableTurnProcessingError("Durable turn processing failed") from exc

    def _release_failed_owner(self, turn_id: str, owner_token: str):
        try:
            self.ledger.fail(turn_id, owner_token)
        except DurableTurnProcessingError:
            # Cleanup is best effort; preserve the original sanitized business error.
            pass

    def _parse_snapshot(self, payload: dict[str, Any]) -> BranchSnapshot:
        try:
            return BranchSnapshot.model_validate(payload)
        except ValidationError as exc:
            raise SnapshotContractError(
                "INVALID_BRANCH_SNAPSHOT",
                "Branch snapshot is malformed",
            ) from exc

    def _process_working_session(
        self,
        session,
        candidate_answer: str,
    ) -> DurableTurnResult:
        working_manager = SessionManager(database=self.database)
        working_manager.sessions[session.session_id] = session
        flow = InterviewService(
            interviewer=self.interviewer,
            session_manager_instance=working_manager,
            database=self.database,
            persist_sessions=False,
        )
        current_stage = session.stage
        if current_stage == InterviewStage.OPENING:
            raw_result = flow.handle_opening_response(session.session_id)
        elif current_stage == InterviewStage.SELF_INTRO:
            raw_result = flow.handle_self_introduction(session.session_id, candidate_answer)
        elif current_stage == InterviewStage.PROJECT_QNA:
            raw_result = flow.handle_project_answer(session.session_id, candidate_answer)
        elif current_stage == InterviewStage.TECHNICAL_QNA:
            raw_result = flow.handle_technical_answer(session.session_id, candidate_answer)
        else:
            raw_result = {
                "stage": InterviewStage.CONCLUDED.value,
                "message": "当前面试已结束。",
            }

        next_stage = str(raw_result.get("stage", current_stage.value))
        question = raw_result.get("question")
        question_payload = question if isinstance(question, dict) else None
        next_question = raw_result.get("next_question")
        if next_question is None and isinstance(question, str):
            next_question = question
        if next_question is None and question_payload:
            text_value = question_payload.get("text")
            next_question = text_value if isinstance(text_value, str) else None
        final_message = next_question or raw_result.get("message")
        if raw_result.get("message") and next_question:
            final_message = f"{raw_result['message']}\n\n{next_question}"

        return DurableTurnResult(
            python_session_id=session.session_id,
            next_stage=next_stage,
            interview_complete=next_stage == InterviewStage.CONCLUDED.value,
            score=raw_result.get("score"),
            feedback=raw_result.get("feedback"),
            next_question=next_question,
            question=question_payload,
            final_message=final_message,
            post_turn_state=DurablePostTurnState(
                current_stage=session.stage.value,
                branch_status=2 if session.stage == InterviewStage.CONCLUDED else 1,
                project_questions_count=session.project_questions_count,
                target_project_questions=session.target_project_questions,
                current_followup_count=session.current_question_followup_count,
                project_questions_pool=deepcopy(session.project_questions_pool),
                technical_questions_pool=deepcopy(session.technical_questions_pool),
            ),
        )
