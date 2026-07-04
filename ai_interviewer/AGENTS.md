# AI Interviewer — Python AI Core

## Quick Start

```bash
uv sync && uv run python main.py
uv run python run_debug.py
```

## Module Map

```
main.py                    # FastAPI app entry; includes interview_router
api/router.py               # APIRouter(prefix="/interview"), HTTP endpoints
api/interviewer.py          # LLM-facing Interviewer (prompts + scoring + followups)
core/model_provider.py      # Unified Azure model provider (chat + embeddings)
core/config.py              # get_llm() compatibility wrapper
core/embeddings.py          # get_embeddings() compatibility wrapper
services/interview_service.py  # Interview flow orchestration
services/interview_session.py  # Session state machine + SessionManager (cache + DB restore)
services/database.py        # SQLite + SQLAlchemy InterviewRecord
services/question_bank.py   # ChromaDB vector store + loaders + text splitter
services/resume_parser.py   # PDF/TXT/MD text extraction
schemas/chat.py             # Pydantic request/response models
```

## Runtime & API Surface

- Runs on `0.0.0.0:8000` via uvicorn (`ai_interviewer/main.py`)
- Router prefix: `/interview` (`ai_interviewer/api/router.py`)
- Async endpoints (file IO): `/interview/upload-resume`, `/interview/questions/import`

## Interview Flow

```
START → OPENING → SELF_INTRO → PROJECT_QNA → TECHNICAL_QNA → CONCLUDE
```

- Orchestrator: `InterviewService` (`services/interview_service.py`)
- LLM core: `Interviewer` (`api/interviewer.py`)
- Session persistence: SQLite `InterviewRecord` + `SessionManager` restore

## Gotchas

- In-memory session cache resets on process restart; relies on DB restore
- `database.py` does schema changes via `ALTER TABLE` (no Alembic)
- `test_interview.py` is a simulation harness, not pytest-style unit tests
