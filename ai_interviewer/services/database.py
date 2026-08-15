"""SQLite cache models and injectable database runtime."""

import os
from datetime import datetime
from pathlib import Path
from typing import Optional

from sqlalchemy import Column, DateTime, Integer, JSON, String, Text, create_engine, inspect, text
from sqlalchemy.orm import declarative_base, sessionmaker


Base = declarative_base()


class InterviewRecord(Base):
    """Replaceable Python cache for a reconstructed interview session."""

    __tablename__ = "interview_records"

    id = Column(String, primary_key=True)
    candidate_name = Column(String, nullable=True)
    resume_content = Column(Text, nullable=True)
    job_requirements = Column(Text, nullable=True)
    stage = Column(String, nullable=False)
    history = Column(JSON, default=list)
    project_qa_list = Column(JSON, default=list)
    technical_qa_list = Column(JSON, default=list)
    project_questions_count = Column(Integer, default=0)
    target_project_questions = Column(Integer, default=5)
    project_questions_pool = Column(JSON, default=list)
    technical_questions_pool = Column(JSON, default=list)
    final_score = Column(Integer, nullable=True)
    final_feedback = Column(Text, nullable=True)
    current_question_followup_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)


class TurnLedgerRecord(Base):
    """Restart-safe idempotency record for a Java-owned durable turn."""

    __tablename__ = "turn_ledger"

    turn_id = Column(String, primary_key=True)
    input_hash = Column(String, nullable=False)
    status = Column(String, nullable=False)
    owner_token = Column(String, nullable=True)
    result_payload = Column(JSON, nullable=True)
    created_at = Column(DateTime, nullable=False, default=datetime.now)
    updated_at = Column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)


class InterviewDatabase:
    """Injectable SQLite cache used by sessions and the turn ledger."""

    def __init__(self, db_path: str | Path):
        self.db_path = Path(db_path).expanduser()
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.engine = create_engine(
            f"sqlite:///{self.db_path}",
            echo=False,
            connect_args={"check_same_thread": False},
        )
        self._session_factory = sessionmaker(
            autocommit=False,
            autoflush=False,
            expire_on_commit=False,
            bind=self.engine,
        )
        self.init_db()

    def init_db(self):
        Base.metadata.create_all(self.engine)
        inspector = inspect(self.engine)
        if "interview_records" not in inspector.get_table_names():
            return
        columns = {column["name"] for column in inspector.get_columns("interview_records")}
        additions = {
            "project_questions_pool": "TEXT DEFAULT '[]'",
            "current_question_followup_count": "INTEGER DEFAULT 0",
            "technical_questions_pool": "TEXT DEFAULT '[]'",
        }
        for column, definition in additions.items():
            if column not in columns:
                with self.engine.begin() as connection:
                    connection.execute(text(
                        f"ALTER TABLE interview_records ADD COLUMN {column} {definition}"
                    ))

    def session(self):
        return self._session_factory()

    def save_session(self, session):
        with self.session() as db:
            self.upsert_session(db, session)
            db.commit()

    def upsert_session(self, db, session):
        """Write a replaceable session cache using the caller's transaction."""
        record = db.get(InterviewRecord, session.session_id)
        payload = {
            "candidate_name": session.candidate_name,
            "resume_content": session.resume_content,
            "job_requirements": session.job_requirements,
            "stage": session.stage.value,
            "history": session.history,
            "project_qa_list": [_qa_payload(qa) for qa in session.project_qa_list],
            "technical_qa_list": [_qa_payload(qa) for qa in session.technical_qa_list],
            "project_questions_count": session.project_questions_count,
            "target_project_questions": session.target_project_questions,
            "project_questions_pool": session.project_questions_pool,
            "technical_questions_pool": session.technical_questions_pool,
            "final_score": session.final_score,
            "final_feedback": session.final_feedback,
            "current_question_followup_count": session.current_question_followup_count,
            "updated_at": session.updated_at,
        }
        if record is None:
            db.add(InterviewRecord(
                id=session.session_id,
                created_at=session.created_at,
                **payload,
            ))
            return
        for key, value in payload.items():
            setattr(record, key, value)

    def close(self):
        self.engine.dispose()


def _qa_payload(qa):
    return {
        "question": qa.question,
        "answer": qa.answer,
        "score": qa.score,
        "feedback": qa.feedback,
        "is_followup": qa.is_followup,
        "timestamp": qa.timestamp.isoformat(),
    }


_DATABASES: dict[str, InterviewDatabase] = {}


def _default_db_path() -> Path:
    configured_path = os.getenv("AI_INTERVIEW_DB_PATH")
    if configured_path:
        return Path(configured_path).expanduser()
    return Path(__file__).parent.parent / "storage" / "database" / "interviews.db"


def get_default_database() -> InterviewDatabase:
    path = str(_default_db_path().resolve())
    database = _DATABASES.get(path)
    if database is None:
        database = InterviewDatabase(path)
        _DATABASES[path] = database
    return database


def get_db_engine():
    return get_default_database().engine


def init_db():
    get_default_database().init_db()


def get_db_session():
    return get_default_database().session()
