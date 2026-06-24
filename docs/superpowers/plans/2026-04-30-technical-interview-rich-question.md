# Technical Interview Rich Question Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build first-phase end-to-end support for technical interview questions that carry structured text plus image metadata, while keeping existing plain-text questions and old SSE clients working.

**Architecture:** Add a shared structured question model, persist media in Admin PostgreSQL through a dedicated `t_question_media` table, sync media metadata into Python Chroma metadata, and emit a new SSE `question` event alongside existing `chunk` text. Flutter and the Admin web console consume the new media field, while Java gateway/interview services keep SSE paths compatible and persist the question text for scoring/history.

**Tech Stack:** Python FastAPI + Pydantic + Chroma, Java 21 + Spring Boot + MyBatis + Flyway + PostgreSQL, Flutter/Dart + Provider + Dio SSE stream, React/Vite admin console.

---

## Scope Check

This plan implements phase one from `docs/TECHNICAL_INTERVIEW_RICH_QUESTION_IMPLEMENTATION_PLAN.md`.

Included:

- Structured `QuestionItem` / `QuestionMedia` data model.
- CSV and Markdown image URL import.
- Admin PostgreSQL media table and Java DTO/entity/mapper changes.
- Python vector metadata sync and structured retrieval.
- Technical interview pool compatibility for old string arrays and new object arrays.
- SSE `question` event with `chunk` fallback.
- Flutter rendering of question images, captions, failure state, and preview.
- Admin web console create/list/import visibility for image URLs.

Excluded from this plan:

- PDF embedded image extraction.
- DOCX embedded image extraction.
- OCR and image vectorization.
- Local image upload to MinIO from Admin UI.
- Long-term image snapshot archiving beyond persisting structured question snapshots in session JSON and Java message content.

## File Structure

### Python AI Service

- Create: `ai_interviewer/schemas/question_item.py`
  - Owns `QuestionMedia`, `QuestionItem`, string compatibility, document conversion, and public payload shape.
- Modify: `ai_interviewer/api/sse.py`
  - Adds `EVENT_QUESTION`.
- Modify: `ai_interviewer/api/admin_router.py`
  - Accepts `media` in Admin sync payload.
- Modify: `ai_interviewer/services/question_bank.py`
  - Stores `media_json`, adds captions to vector text, and returns `QuestionItem`.
- Modify: `ai_interviewer/api/interviewer.py`
  - Adds `select_technical_question_items()` and keeps `select_technical_questions()` for old callers.
- Modify: `ai_interviewer/services/interview_session.py`
  - Allows structured question objects in `history` and `technical_questions_pool`.
- Modify: `ai_interviewer/services/interview_service.py`
  - Initializes technical questions as `QuestionItem`, scores with `question.text`, and serializes objects.
- Modify: `ai_interviewer/api/router.py`
  - Emits `question` SSE events and preserves `chunk` / `result`.
- Test: `ai_interviewer/tests/test_question_item.py`
- Test: `ai_interviewer/tests/test_rich_question_bank.py`
- Test: `ai_interviewer/tests/test_rich_question_interview_flow.py`

### Admin Backend

- Create: `ai_interviewer_admin/src/main/resources/db/migration/V3__question_media.sql`
  - Adds `t_question_media`.
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionMedia.java`
  - Java entity for one media asset.
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionMediaRequest.java`
  - Request DTO for create/update/import.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionBankItem.java`
  - Adds `List<QuestionMedia> media`.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionCreateRequest.java`
  - Adds `media`.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionUpdateRequest.java`
  - Adds `media` and `mediaSet`.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionImportRow.java`
  - Adds `media`.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/mapper/QuestionMapper.java`
  - Adds select/delete/insert media methods.
- Modify: `ai_interviewer_admin/src/main/resources/mapper/QuestionBankMapper.xml`
  - Adds media result map and SQL.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionService.java`
  - Validates media URL, writes media, hydrates media.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionImportService.java`
  - Extends CSV headers and parses Markdown image syntax.
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/client/PythonQuestionBankClient.java`
  - Sends `media`.
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionMediaServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionImportServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionVectorSyncServiceTest.java`

### Java Interview + Gateway

- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/sse/SSEEventType.java`
  - Adds `QUESTION`.
- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java`
  - Tracks question event text for `lastQuestion` and persisted AI messages.
- Test: `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/SSEProxyServiceQuestionEventTest.java`
- Verify: `ai_interview_backend/ai-interviewer-gateway/src/main/java/com/aiinterviewer/gateway/filter/SSEFilter.java`
  - No behavior change expected because it already identifies chat/resume SSE paths.

### Flutter App

- Create: `ai_interviewer_front/lib/models/question_media.dart`
- Modify: `ai_interviewer_front/lib/models/chat_message.dart`
- Modify: `ai_interviewer_front/lib/services/interview_service.dart`
- Modify: `ai_interviewer_front/lib/interview_chat_page.dart`
- Test: `ai_interviewer_front/test/question_media_test.dart`
- Test: `ai_interviewer_front/test/interview_service_question_event_test.dart`
- Test: `ai_interviewer_front/test/interview_chat_page_media_test.dart`

### Admin Web Console

- Modify: `ai_interviewer_admin_front/src/types.ts`
- Modify: `ai_interviewer_admin_front/src/api.ts`
- Modify: `ai_interviewer_admin_front/src/App.tsx`
- Optional style-only modify: `ai_interviewer_admin_front/src/index.css` if media preview layout needs extra classes.

## Task 1: Python Structured Question Model

**Files:**

- Create: `ai_interviewer/schemas/question_item.py`
- Test: `ai_interviewer/tests/test_question_item.py`

- [ ] **Step 1: Write the failing model tests**

```python
# ai_interviewer/tests/test_question_item.py
from langchain_core.documents import Document

from schemas.question_item import QuestionItem, QuestionMedia


def test_question_item_converts_plain_string_to_structured_item():
    item = QuestionItem.from_legacy("请解释 Redis Lua 限流脚本关系。")

    assert item.text == "请解释 Redis Lua 限流脚本关系。"
    assert item.media == []
    assert item.to_public_dict() == {
        "id": None,
        "text": "请解释 Redis Lua 限流脚本关系。",
        "question_type": None,
        "difficulty": None,
        "skill_area": None,
        "tags": [],
        "media": [],
        "answer_reference": None,
    }


def test_question_item_keeps_image_media_alias_type():
    media = QuestionMedia(type="image", url="https://example.com/figure.png", caption="图 10-17", alt="Redis 限流图")
    item = QuestionItem(text="请结合下图说明脚本关系。", media=[media])

    assert item.media[0].type == "image"
    assert item.to_public_dict()["media"] == [
        {
            "type": "image",
            "url": "https://example.com/figure.png",
            "caption": "图 10-17",
            "alt": "Redis 限流图",
        }
    ]


def test_question_item_from_document_parses_media_json_metadata():
    document = Document(
        page_content="请结合下图说明脚本关系。\n图注：图 10-17",
        metadata={
            "question_id": "123",
            "question_text": "请结合下图说明脚本关系。",
            "question_type": "TECHNICAL",
            "difficulty": "MEDIUM",
            "skill_area": "Redis",
            "tags": "Redis,Lua,限流",
            "answer_reference": "入口脚本调用底层限流脚本。",
            "media_json": '[{"type":"image","url":"https://example.com/figure.png","caption":"图 10-17","alt":"Redis 限流图"}]',
        },
    )

    item = QuestionItem.from_document(document)

    assert item.id == "123"
    assert item.text == "请结合下图说明脚本关系。"
    assert item.question_type == "TECHNICAL"
    assert item.difficulty == "MEDIUM"
    assert item.skill_area == "Redis"
    assert item.tags == ["Redis", "Lua", "限流"]
    assert item.answer_reference == "入口脚本调用底层限流脚本。"
    assert item.media[0].url == "https://example.com/figure.png"
```

- [ ] **Step 2: Run the model tests and verify they fail**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_question_item.py -q
```

Expected:

```text
ModuleNotFoundError: No module named 'schemas.question_item'
```

- [ ] **Step 3: Add the structured model**

```python
# ai_interviewer/schemas/question_item.py
from __future__ import annotations

import json
from typing import Any

from langchain_core.documents import Document
from pydantic import BaseModel, ConfigDict, Field, field_validator


class QuestionMedia(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    type: str = Field(default="image")
    url: str
    caption: str | None = None
    alt: str | None = None

    @field_validator("type")
    @classmethod
    def normalize_type(cls, value: str) -> str:
        normalized = (value or "").strip().lower()
        return normalized or "image"

    @field_validator("url")
    @classmethod
    def validate_url(cls, value: str) -> str:
        normalized = (value or "").strip()
        if not normalized.startswith(("http://", "https://")):
            raise ValueError("media url must start with http:// or https://")
        return normalized

    def to_public_dict(self) -> dict[str, str | None]:
        return {
            "type": self.type,
            "url": self.url,
            "caption": self.caption,
            "alt": self.alt,
        }


class QuestionItem(BaseModel):
    id: str | None = None
    text: str
    question_type: str | None = None
    difficulty: str | None = None
    skill_area: str | None = None
    tags: list[str] = Field(default_factory=list)
    media: list[QuestionMedia] = Field(default_factory=list)
    answer_reference: str | None = None

    @field_validator("text")
    @classmethod
    def validate_text(cls, value: str) -> str:
        normalized = (value or "").strip()
        if not normalized:
            raise ValueError("question text is required")
        return normalized

    @classmethod
    def from_legacy(cls, value: str | dict[str, Any] | "QuestionItem") -> "QuestionItem":
        if isinstance(value, QuestionItem):
            return value
        if isinstance(value, str):
            return cls(text=value)
        if isinstance(value, dict):
            raw_text = value.get("text") or value.get("question_text") or value.get("content")
            return cls(
                id=_none_to_str(value.get("id")),
                text=str(raw_text or ""),
                question_type=value.get("question_type"),
                difficulty=value.get("difficulty"),
                skill_area=value.get("skill_area"),
                tags=_parse_tags(value.get("tags")),
                media=[QuestionMedia.model_validate(item) for item in value.get("media") or []],
                answer_reference=value.get("answer_reference"),
            )
        raise TypeError(f"Unsupported question item: {type(value).__name__}")

    @classmethod
    def from_document(cls, document: Document) -> "QuestionItem":
        metadata = dict(document.metadata or {})
        media = _parse_media_json(metadata.get("media_json"))
        text = metadata.get("question_text") or _first_line(document.page_content)
        return cls(
            id=_none_to_str(metadata.get("question_id")),
            text=str(text or ""),
            question_type=_empty_to_none(metadata.get("question_type")),
            difficulty=_empty_to_none(metadata.get("difficulty")),
            skill_area=_empty_to_none(metadata.get("skill_area")),
            tags=_parse_tags(metadata.get("tags")),
            media=media,
            answer_reference=_empty_to_none(metadata.get("answer_reference")),
        )

    def plain_text(self) -> str:
        return self.text

    def to_history_message(self) -> dict[str, Any]:
        return {
            "role": "ai",
            "content": self.text,
            "question": self.to_public_dict(),
        }

    def to_pool_dict(self) -> dict[str, Any]:
        return self.to_public_dict()

    def to_public_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "text": self.text,
            "question_type": self.question_type,
            "difficulty": self.difficulty,
            "skill_area": self.skill_area,
            "tags": list(self.tags),
            "media": [item.to_public_dict() for item in self.media],
            "answer_reference": self.answer_reference,
        }


def _first_line(value: str) -> str:
    for line in (value or "").splitlines():
        if line.strip():
            return line.strip()
    return ""


def _empty_to_none(value: Any) -> str | None:
    text = str(value).strip() if value is not None else ""
    return text or None


def _none_to_str(value: Any) -> str | None:
    return None if value is None else str(value)


def _parse_tags(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    return [item.strip() for item in str(value).replace("，", ",").split(",") if item.strip()]


def _parse_media_json(value: Any) -> list[QuestionMedia]:
    if value is None or value == "":
        return []
    raw_items = json.loads(str(value))
    if not isinstance(raw_items, list):
        return []
    return [QuestionMedia.model_validate(item) for item in raw_items if isinstance(item, dict)]
```

- [ ] **Step 4: Run the model tests and verify they pass**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_question_item.py -q
```

Expected:

```text
3 passed
```

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer/schemas/question_item.py ai_interviewer/tests/test_question_item.py
git commit -m "feat(ai): add structured question model"
```

## Task 2: Python Question Bank Sync and Structured Retrieval

**Files:**

- Modify: `ai_interviewer/api/admin_router.py`
- Modify: `ai_interviewer/services/question_bank.py`
- Test: `ai_interviewer/tests/test_rich_question_bank.py`

- [ ] **Step 1: Write failing sync/retrieval tests**

```python
# ai_interviewer/tests/test_rich_question_bank.py
import json

from langchain_core.documents import Document

from services.question_bank import QuestionBank


class FakeVectorStore:
    def __init__(self):
        self.added_documents = []
        self.added_ids = []

    def add_documents(self, documents, ids=None):
        self.added_documents.extend(documents)
        self.added_ids.extend(ids or [])

    def similarity_search(self, query, k):
        return [
            Document(
                page_content="请结合下图说明两个 Lua 脚本关系。\n图注：图 10-17",
                metadata={
                    "question_id": "123",
                    "question_text": "请结合下图说明两个 Lua 脚本关系。",
                    "question_type": "TECHNICAL",
                    "difficulty": "MEDIUM",
                    "skill_area": "Redis",
                    "tags": "Redis,Lua",
                    "answer_reference": "入口脚本调用底层限流脚本。",
                    "media_json": '[{"type":"image","url":"https://example.com/figure.png","caption":"图 10-17","alt":"Redis 限流图"}]',
                },
            )
        ]


def make_bank():
    bank = QuestionBank.__new__(QuestionBank)
    bank.vectorstore = FakeVectorStore()
    return bank


def test_sync_structured_questions_writes_media_json_and_caption_text():
    bank = make_bank()

    result = bank.sync_structured_questions([
        {
            "id": 123,
            "question_text": "请结合下图说明两个 Lua 脚本关系。",
            "answer_reference": "入口脚本调用底层限流脚本。",
            "question_type": "TECHNICAL",
            "difficulty": "MEDIUM",
            "skill_area": "Redis",
            "tags": ["Redis", "Lua"],
            "media": [
                {
                    "type": "image",
                    "url": "https://example.com/figure.png",
                    "caption": "图 10-17",
                    "alt": "Redis 限流图",
                }
            ],
        }
    ])

    document = bank.vectorstore.added_documents[0]
    metadata = document.metadata

    assert result == [{"id": 123, "vector_store_id": "admin-question-123"}]
    assert "图注：图 10-17" in document.page_content
    assert "https://example.com/figure.png" not in document.page_content
    assert json.loads(metadata["media_json"])[0]["url"] == "https://example.com/figure.png"
    assert metadata["question_text"] == "请结合下图说明两个 Lua 脚本关系。"


def test_search_question_items_returns_structured_media():
    bank = make_bank()

    items = bank.search_question_items("Redis Lua 限流", question_types=["TECHNICAL"], k=1)

    assert len(items) == 1
    assert items[0].id == "123"
    assert items[0].text == "请结合下图说明两个 Lua 脚本关系。"
    assert items[0].media[0].caption == "图 10-17"
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_rich_question_bank.py -q
```

Expected:

```text
AttributeError: 'QuestionBank' object has no attribute 'search_question_items'
```

- [ ] **Step 3: Extend Admin sync schema**

Apply this change in `ai_interviewer/api/admin_router.py`:

```python
from schemas.question_item import QuestionMedia


class AdminQuestionSyncItem(BaseModel):
    id: int
    question_text: str = Field(..., alias="question_text")
    answer_reference: Optional[str] = Field(default=None, alias="answer_reference")
    question_type: Optional[str] = Field(default=None, alias="question_type")
    difficulty: Optional[str] = None
    tags: List[str] = Field(default_factory=list)
    skill_area: Optional[str] = Field(default=None, alias="skill_area")
    media: List[QuestionMedia] = Field(default_factory=list)
```

- [ ] **Step 4: Extend Chroma sync and structured retrieval**

Apply these focused changes in `ai_interviewer/services/question_bank.py`:

```python
import json

from schemas.question_item import QuestionItem, QuestionMedia
```

Inside `sync_structured_questions()`, after `tags` is normalized:

```python
            media_items = []
            for media in question.get("media") or []:
                media_items.append(QuestionMedia.model_validate(media))
```

Inside `content_parts`, after tags are appended:

```python
            for media in media_items:
                if media.caption:
                    content_parts.append(f"图注：{media.caption}")
                if media.alt:
                    content_parts.append(f"图片说明：{media.alt}")
```

Replace the metadata dict with:

```python
                    metadata={
                        "source": "admin-question-bank",
                        "question_id": str(question_id),
                        "question_text": question_text,
                        "question_type": question_type,
                        "difficulty": difficulty,
                        "skill_area": skill_area,
                        "tags": ",".join(str(tag) for tag in tags),
                        "answer_reference": answer_reference,
                        "media_json": json.dumps(
                            [item.to_public_dict() for item in media_items],
                            ensure_ascii=False,
                        ),
                    },
```

Add the new method below `search_questions()`:

```python
    def search_question_items(
        self,
        query: str,
        job_requirements: Optional[str] = None,
        question_types: Optional[List[str]] = None,
        k: int = 10,
    ) -> List[QuestionItem]:
        documents = self.search_questions(
            query=query,
            job_requirements=job_requirements,
            question_types=question_types,
            k=k,
        )
        return [QuestionItem.from_document(document) for document in documents]
```

- [ ] **Step 5: Run tests and verify they pass**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_question_item.py tests/test_rich_question_bank.py -q
```

Expected:

```text
5 passed
```

- [ ] **Step 6: Commit**

```bash
git add ai_interviewer/api/admin_router.py ai_interviewer/services/question_bank.py ai_interviewer/tests/test_rich_question_bank.py
git commit -m "feat(ai): sync rich question media metadata"
```

## Task 3: Python Technical Interview Pool and Auto First Question

**Files:**

- Modify: `ai_interviewer/api/interviewer.py`
- Modify: `ai_interviewer/services/interview_session.py`
- Modify: `ai_interviewer/services/interview_service.py`
- Test: `ai_interviewer/tests/test_rich_question_interview_flow.py`

- [ ] **Step 1: Write failing interview-flow tests**

```python
# ai_interviewer/tests/test_rich_question_interview_flow.py
from schemas.question_item import QuestionItem, QuestionMedia
from services.interview_session import InterviewStage, session_manager


def test_technical_pool_accepts_legacy_strings_after_reload():
    session = session_manager.create_session("rich-legacy", resume_content="Java 工程师")
    session.stage = InterviewStage.TECHNICAL_QNA
    session.technical_questions_pool = ["请解释 HashMap 扩容。"]

    item = QuestionItem.from_legacy(session.technical_questions_pool[0])

    assert item.text == "请解释 HashMap 扩容。"
    assert item.media == []


def test_project_to_technical_initializes_first_rich_question(monkeypatch):
    from services import interview_service as service_module

    service = service_module.InterviewService.__new__(service_module.InterviewService)
    service.interviewer = type("FakeInterviewer", (), {})()
    service.resume_parser = None

    question = QuestionItem(
        id="123",
        text="请结合下图说明两个 Lua 脚本关系。",
        question_type="TECHNICAL",
        media=[QuestionMedia(url="https://example.com/figure.png", caption="图 10-17")],
    )

    service.interviewer.evaluate_answer = lambda current_question, answer, resume_content: (80, "回答基本正确", None)
    service.interviewer.generate_followup_question = lambda current_question, answer, reason: "继续追问"
    service.interviewer.select_technical_question_items = lambda session, types, counts: [question]
    monkeypatch.setattr(service_module, "init_db", lambda: None)
    monkeypatch.setattr(service, "_save_session", lambda session: None)

    session = session_manager.create_session("rich-flow", resume_content="Java Redis")
    session.stage = InterviewStage.PROJECT_QNA
    session.target_project_questions = 1
    session.project_questions_count = 0
    session.add_message("ai", "请介绍项目难点。")

    result = service.handle_project_answer("rich-flow", "我做了 Redis 限流。")

    assert result["stage"] == "technical_qna"
    assert result["question"]["text"] == "请结合下图说明两个 Lua 脚本关系。"
    assert result["question"]["media"][0]["caption"] == "图 10-17"
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_rich_question_interview_flow.py -q
```

Expected:

```text
KeyError: 'question'
```

- [ ] **Step 3: Add structured selector while preserving old selector**

Add this method to `ai_interviewer/api/interviewer.py` below `select_technical_questions()`:

```python
    def select_technical_question_items(
        self,
        session: InterviewSession,
        question_types: List[str],
        counts: Dict[str, int],
    ) -> list[QuestionItem]:
        query_parts = []
        if session.job_requirements:
            query_parts.append(session.job_requirements)
        query_parts.append(" ".join(question_types))
        if session.project_qa_list:
            avg_score = session.get_average_score()
            if avg_score:
                query_parts.append(f"候选人平均分: {avg_score:.1f}")

        total_count = sum(counts.values())
        documents = self.question_bank.search_question_items(
            "\n".join(query_parts),
            question_types=question_types,
            k=total_count * 2,
        )
        return documents[:total_count]
```

Also add the import at the top:

```python
from schemas.question_item import QuestionItem
```

- [ ] **Step 4: Update session typing**

In `ai_interviewer/services/interview_session.py`, change the import and field comments:

```python
from typing import Any, Dict, List, Optional, TYPE_CHECKING
```

Change the pool field:

```python
    technical_questions_pool: List[Any] = field(default_factory=list)  # supports legacy strings and QuestionItem dicts
```

- [ ] **Step 5: Add question helpers to InterviewService**

Add these private helpers inside `InterviewService` in `ai_interviewer/services/interview_service.py`:

```python
    def _coerce_question_item(self, value) -> QuestionItem:
        return QuestionItem.from_legacy(value)

    def _question_text(self, value) -> str:
        return self._coerce_question_item(value).plain_text()

    def _question_payload(self, value) -> dict:
        return self._coerce_question_item(value).to_public_dict()

    def _add_ai_question(self, session: InterviewSession, question) -> QuestionItem:
        item = self._coerce_question_item(question)
        session.history.append(item.to_history_message())
        session.updated_at = datetime.now()
        return item
```

Add imports:

```python
from datetime import datetime
from schemas.question_item import QuestionItem
```

- [ ] **Step 6: Initialize first technical question when project Q&A ends**

Inside `handle_project_answer()`, replace each branch that only sets `session.stage = InterviewStage.TECHNICAL_QNA` and returns `"项目提问环节结束，进入技术面试环节"` with:

```python
            session.stage = InterviewStage.TECHNICAL_QNA
            session.current_question_followup_count = 0
            technical_result = self._start_technical_questions_for_session(session)
            result["stage"] = session.stage.value
            result["message"] = "项目提问环节结束，进入技术面试环节"
            result["question"] = technical_result["question"]
            result["next_question"] = technical_result["question"]["text"]
            result["remaining_questions"] = technical_result["remaining_questions"]
            self._save_session(session)
            return result
```

Add this helper in `InterviewService`:

```python
    def _start_technical_questions_for_session(self, session: InterviewSession) -> Dict:
        questions = self.interviewer.select_technical_question_items(
            session,
            ["TECHNICAL"],
            {"TECHNICAL": 5},
        )
        if not questions:
            questions = [QuestionItem(text="请介绍一下 Java 中 HashMap 的实现原理？", question_type="TECHNICAL")]

        first_question = self._add_ai_question(session, questions[0])
        session.technical_questions_pool = [item.to_pool_dict() for item in questions[1:]]
        return {
            "question": first_question.to_public_dict(),
            "remaining_questions": len(session.technical_questions_pool),
        }
```

- [ ] **Step 7: Update start and answer handlers to use structured questions**

In `start_technical_interview()`, replace question selection and history writing with:

```python
        questions = self.interviewer.select_technical_question_items(
            session,
            question_types,
            counts,
        )
        if not questions:
            questions = [QuestionItem(text="请介绍一下 Java 中 HashMap 的实现原理？", question_type="TECHNICAL")]

        first_question = self._add_ai_question(session, questions[0])
        session.technical_questions_pool = [item.to_pool_dict() for item in questions[1:]]

        self._save_session(session)

        return {
            "question": first_question.to_public_dict(),
            "next_question": first_question.text,
            "remaining_questions": len(session.technical_questions_pool),
            "stage": session.stage.value,
        }
```

In `handle_technical_answer()`, when reading the current AI question:

```python
                question_payload = msg.get("question")
                current_question = question_payload or msg.get("content")
                break
```

Before evaluating:

```python
        current_question_item = self._coerce_question_item(current_question)
```

Replace `evaluate_answer(current_question, ...)` with:

```python
            current_question_item.text,
            answer,
            session.resume_content,
```

Replace the saved QA question with:

```python
            question=current_question_item.text,
```

When adding the next question:

```python
            next_question = questions_pool.pop(0)
            next_question_item = self._add_ai_question(session, next_question)
            session.technical_questions_pool = questions_pool
            result["question"] = next_question_item.to_public_dict()
            result["next_question"] = next_question_item.text
            result["remaining_questions"] = len(questions_pool)
            result["stage"] = session.stage.value
```

- [ ] **Step 8: Run interview-flow tests**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_rich_question_interview_flow.py -q
```

Expected:

```text
2 passed
```

- [ ] **Step 9: Commit**

```bash
git add ai_interviewer/api/interviewer.py ai_interviewer/services/interview_session.py ai_interviewer/services/interview_service.py ai_interviewer/tests/test_rich_question_interview_flow.py
git commit -m "feat(ai): use structured technical questions"
```

## Task 4: Python SSE Question Event and Resume Compatibility

**Files:**

- Modify: `ai_interviewer/api/sse.py`
- Modify: `ai_interviewer/api/router.py`
- Test: `ai_interviewer/tests/test_rich_question_sse.py`

- [ ] **Step 1: Write failing SSE helper tests**

```python
# ai_interviewer/tests/test_rich_question_sse.py
import json

from api.sse import EVENT_QUESTION, format_sse


def test_question_sse_event_serializes_question_payload():
    raw = format_sse(
        EVENT_QUESTION,
        {
            "question": {
                "id": "123",
                "text": "请结合下图说明两个 Lua 脚本关系。",
                "media": [{"type": "image", "url": "https://example.com/figure.png", "caption": "图 10-17", "alt": None}],
            },
            "next_stage": "technical_qna",
        },
    )

    event_line, data_line, blank = raw.splitlines()
    payload = json.loads(data_line.removeprefix("data: "))

    assert event_line == "event: question"
    assert payload["question"]["media"][0]["url"] == "https://example.com/figure.png"
    assert blank == ""
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_rich_question_sse.py -q
```

Expected:

```text
ImportError: cannot import name 'EVENT_QUESTION'
```

- [ ] **Step 3: Add SSE event constant**

Add this line in `ai_interviewer/api/sse.py`:

```python
EVENT_QUESTION = "question"
```

- [ ] **Step 4: Emit question event with chunk fallback**

Update `ai_interviewer/api/router.py` import:

```python
    EVENT_QUESTION,
```

In `chat_stream()`, initialize:

```python
            question_payload = None
```

After each service call that can return a question, assign:

```python
                question_payload = result.get("question")
```

Before streaming `final_message`, add:

```python
            if question_payload:
                yield format_sse(
                    EVENT_QUESTION,
                    {
                        "question": question_payload,
                        "next_stage": str(next_stage),
                    },
                )
```

Keep the existing `chunk` block unchanged so old clients still render text:

```python
            if final_message:
                for piece in stream_text_chunks(final_message):
                    yield format_sse(EVENT_CHUNK, {"content": piece})
```

When building `result_payload`, include the structured question:

```python
            if question_payload:
                result_payload["question"] = question_payload
```

In `resume_stream()`, detect structured history:

```python
            last_message = None
            for msg in reversed(session.history):
                if msg.get("role") == "ai":
                    last_message = msg
                    break

            if last_message:
                question_payload = last_message.get("question")
                last_question = last_message.get("content")
                if question_payload:
                    yield format_sse(
                        EVENT_QUESTION,
                        {"question": question_payload, "next_stage": stage},
                    )
                if last_question:
                    for piece in stream_text_chunks(last_question):
                        yield format_sse(EVENT_CHUNK, {"content": piece})
                yield format_sse(
                    EVENT_RESULT,
                    {"next_stage": stage, "next_question": last_question, "question": question_payload},
                )
```

- [ ] **Step 5: Run Python rich question tests**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_question_item.py tests/test_rich_question_bank.py tests/test_rich_question_interview_flow.py tests/test_rich_question_sse.py -q
```

Expected:

```text
8 passed
```

- [ ] **Step 6: Commit**

```bash
git add ai_interviewer/api/sse.py ai_interviewer/api/router.py ai_interviewer/tests/test_rich_question_sse.py
git commit -m "feat(ai): emit structured question sse event"
```

## Task 5: Admin Backend Question Media Persistence

**Files:**

- Create: `ai_interviewer_admin/src/main/resources/db/migration/V3__question_media.sql`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionMedia.java`
- Create: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionMediaRequest.java`
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionBankItem.java`
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionCreateRequest.java`
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionUpdateRequest.java`
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/mapper/QuestionMapper.java`
- Modify: `ai_interviewer_admin/src/main/resources/mapper/QuestionBankMapper.xml`
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionService.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionMediaServiceTest.java`

- [ ] **Step 1: Write failing media persistence tests**

```java
// ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionMediaServiceTest.java
package com.aiinterviewer.admin.questionbank;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionMediaRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionMedia;
import com.aiinterviewer.admin.support.AdminPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestionMediaServiceTest extends AdminPostgresIntegrationTest {

    @Autowired
    private QuestionService questionService;

    @Test
    void createQuestionPersistsAndReturnsMediaInSortOrder() {
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setQuestionText("请结合下图说明两个 Lua 脚本关系。");
        request.setAnswerReference("入口脚本调用底层限流脚本。");
        request.setQuestionType("TECHNICAL");
        request.setDifficulty("MEDIUM");
        request.setSkillArea("Redis");
        request.setStatus(QuestionBankItem.STATUS_PENDING_REVIEW);
        request.setTags(List.of("Redis", "Lua"));
        request.setMedia(List.of(
                new QuestionMediaRequest("image", "https://example.com/figure.png", "图 10-17", "Redis 限流图")
        ));

        Long questionId = questionService.createQuestion(request);

        QuestionBankItem item = questionService.getQuestion(questionId);
        assertThat(item.getMedia()).extracting(QuestionMedia::getMediaUrl)
                .containsExactly("https://example.com/figure.png");
        assertThat(item.getMedia().getFirst().getCaption()).isEqualTo("图 10-17");
        assertThat(item.getVectorSyncStatus()).isEqualTo(QuestionBankItem.VECTOR_SYNC_PENDING);
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
cd ai_interviewer_admin
./mvnw -Dtest=QuestionMediaServiceTest test
```

Expected:

```text
cannot find symbol: class QuestionMediaRequest
```

- [ ] **Step 3: Add database migration**

```sql
-- ai_interviewer_admin/src/main/resources/db/migration/V3__question_media.sql
CREATE TABLE IF NOT EXISTS t_question_media (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    media_type VARCHAR(30) NOT NULL,
    media_url TEXT NOT NULL,
    caption TEXT,
    alt_text TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_question_media_question
        FOREIGN KEY (question_id) REFERENCES t_question_bank(id)
);

CREATE INDEX IF NOT EXISTS idx_question_media_question_id
    ON t_question_media(question_id);

CREATE INDEX IF NOT EXISTS idx_question_media_type
    ON t_question_media(media_type);
```

- [ ] **Step 4: Add Java media entity and DTO**

```java
// ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionMedia.java
package com.aiinterviewer.admin.questionbank.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionMedia {
    private Long id;
    private Long questionId;
    private String mediaType;
    private String mediaUrl;
    private String caption;
    private String altText;
    private Integer sortOrder;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
```

```java
// ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionMediaRequest.java
package com.aiinterviewer.admin.questionbank.dto;

public record QuestionMediaRequest(
        String type,
        String url,
        String caption,
        String alt) {
}
```

- [ ] **Step 5: Add media fields to request/entity types**

In `QuestionBankItem.java`:

```java
    private List<QuestionMedia> media = List.of();
```

In `QuestionCreateRequest.java`:

```java
    private List<QuestionMediaRequest> media = List.of();
```

In `QuestionUpdateRequest.java`:

```java
    private List<QuestionMediaRequest> media;
    private boolean mediaSet;

    public void setMedia(List<QuestionMediaRequest> media) {
        this.media = media;
        this.mediaSet = true;
    }
```

- [ ] **Step 6: Add mapper methods and XML**

In `QuestionMapper.java`:

```java
    List<QuestionMedia> selectMediaByQuestionIds(@Param("questionIds") List<Long> questionIds);

    int deleteQuestionMedia(@Param("questionId") Long questionId);

    int insertQuestionMedia(@Param("media") QuestionMedia media);
```

In `QuestionBankMapper.xml`, add import-compatible result map:

```xml
    <resultMap id="QuestionMediaMap" type="com.aiinterviewer.admin.questionbank.entity.QuestionMedia">
        <id property="id" column="id"/>
        <result property="questionId" column="question_id"/>
        <result property="mediaType" column="media_type"/>
        <result property="mediaUrl" column="media_url"/>
        <result property="caption" column="caption"/>
        <result property="altText" column="alt_text"/>
        <result property="sortOrder" column="sort_order"/>
        <result property="createdBy" column="created_by"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
        <result property="deletedAt" column="deleted_at"/>
    </resultMap>

    <select id="selectMediaByQuestionIds" resultMap="QuestionMediaMap">
        SELECT id, question_id, media_type, media_url, caption, alt_text, sort_order, created_by,
               created_at::timestamp AS created_at,
               updated_at::timestamp AS updated_at,
               deleted_at::timestamp AS deleted_at
        FROM t_question_media
        WHERE deleted_at IS NULL
          AND question_id IN
          <foreach collection="questionIds" item="questionId" open="(" separator="," close=")">
              #{questionId}
          </foreach>
        ORDER BY question_id ASC, sort_order ASC, id ASC
    </select>

    <update id="deleteQuestionMedia">
        UPDATE t_question_media
        SET deleted_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE question_id = #{questionId}
          AND deleted_at IS NULL
    </update>

    <insert id="insertQuestionMedia" useGeneratedKeys="true" keyProperty="media.id">
        INSERT INTO t_question_media
            (question_id, media_type, media_url, caption, alt_text, sort_order, created_by, created_at, updated_at)
        VALUES
            (#{media.questionId}, #{media.mediaType}, #{media.mediaUrl}, #{media.caption}, #{media.altText},
             #{media.sortOrder}, #{media.createdBy}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    </insert>
```

- [ ] **Step 7: Hydrate and write media in QuestionService**

Add imports:

```java
import com.aiinterviewer.admin.questionbank.dto.QuestionMediaRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionMedia;
```

In `createQuestion()`, after `replaceTags(...)`:

```java
        replaceMedia(item.getId(), request.getMedia(), request.getCreatedBy());
```

In `updateQuestion()`, after tag update:

```java
        if (request.isMediaSet()) {
            replaceMedia(questionId, request.getMedia(), request.getUpdatedBy());
        }
```

In both `listQuestions()` and `getQuestion()`, call `hydrateMedia(records)` or `hydrateMedia(List.of(question))` after `hydrateTags(...)`.

Add these helpers:

```java
    private void replaceMedia(Long questionId, List<QuestionMediaRequest> rawMedia, Long createdBy) {
        questionMapper.deleteQuestionMedia(questionId);
        List<QuestionMediaRequest> normalized = normalizeMedia(rawMedia);
        for (int index = 0; index < normalized.size(); index++) {
            QuestionMediaRequest request = normalized.get(index);
            QuestionMedia media = new QuestionMedia();
            media.setQuestionId(questionId);
            media.setMediaType(normalizeMediaType(request.type()));
            media.setMediaUrl(request.url().trim());
            media.setCaption(trimToNull(request.caption()));
            media.setAltText(trimToNull(request.alt()));
            media.setSortOrder(index);
            media.setCreatedBy(createdBy);
            questionMapper.insertQuestionMedia(media);
        }
    }

    private List<QuestionMediaRequest> normalizeMedia(List<QuestionMediaRequest> rawMedia) {
        if (rawMedia == null || rawMedia.isEmpty()) {
            return List.of();
        }
        List<QuestionMediaRequest> normalized = new ArrayList<>();
        for (QuestionMediaRequest media : rawMedia) {
            if (media == null || !StringUtils.hasText(media.url())) {
                continue;
            }
            String url = media.url().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new AdminBusinessException(400, "图片 URL 只支持 http:// 或 https://");
            }
            normalized.add(media);
        }
        return normalized;
    }

    private String normalizeMediaType(String mediaType) {
        return StringUtils.hasText(mediaType) ? mediaType.trim().toLowerCase(Locale.ROOT) : "image";
    }

    private void hydrateMedia(List<QuestionBankItem> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        List<Long> questionIds = questions.stream()
                .map(QuestionBankItem::getId)
                .toList();
        Map<Long, List<QuestionMedia>> mediaByQuestionId = new LinkedHashMap<>();
        for (QuestionMedia media : questionMapper.selectMediaByQuestionIds(questionIds)) {
            mediaByQuestionId.computeIfAbsent(media.getQuestionId(), ignored -> new ArrayList<>()).add(media);
        }
        questions.forEach(question -> question.setMedia(mediaByQuestionId.getOrDefault(question.getId(), List.of())));
    }
```

- [ ] **Step 8: Run media persistence test**

Run:

```bash
cd ai_interviewer_admin
./mvnw -Dtest=QuestionMediaServiceTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 9: Commit**

```bash
git add ai_interviewer_admin/src/main/resources/db/migration/V3__question_media.sql \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionMedia.java \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionMediaRequest.java \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/entity/QuestionBankItem.java \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionCreateRequest.java \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionUpdateRequest.java \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/mapper/QuestionMapper.java \
  ai_interviewer_admin/src/main/resources/mapper/QuestionBankMapper.xml \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionService.java \
  ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionMediaServiceTest.java
git commit -m "feat(admin): persist question media"
```

## Task 6: Admin CSV/Markdown Import and Python Sync Payload

**Files:**

- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionImportRow.java`
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionImportService.java`
- Modify: `ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/client/PythonQuestionBankClient.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionImportServiceTest.java`
- Test: `ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionVectorSyncServiceTest.java`

- [ ] **Step 1: Write failing import and sync assertions**

Add this test to `QuestionImportServiceTest`:

```java
    @Test
    void csvImportSupportsMediaUrlsAndCaptions() {
        QuestionImportBatch batch = questionImportService.importCsv(
                "rich.csv",
                csv("""
                        question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status,media_urls,media_captions
                        请结合下图说明两个 Lua 脚本关系,入口脚本调用底层限流脚本,TECHNICAL,MEDIUM,Redis;Lua,Redis,,2,https://example.com/figure.png,图 10-17
                        """),
                1L);

        assertThat(batch.getStatus()).isEqualTo(QuestionImportBatch.STATUS_SUCCESS);
        Long questionId = questionIdByText("请结合下图说明两个 Lua 脚本关系");
        QuestionBankItem item = questionService.getQuestion(questionId);
        assertThat(item.getMedia()).extracting(QuestionMedia::getCaption).containsExactly("图 10-17");
    }
```

Add this test to `QuestionVectorSyncServiceTest`:

```java
    @Test
    void syncPayloadIncludesQuestionMedia() {
        Long questionId = questionService.createQuestion(createRequest("图文同步题目", 1));
        QuestionUpdateRequest update = new QuestionUpdateRequest();
        update.setMedia(List.of(new QuestionMediaRequest("image", "https://example.com/figure.png", "图 10-17", "Redis 限流图")));
        questionService.updateQuestion(questionId, update);

        when(pythonQuestionBankClient.syncQuestions(anyList()))
                .thenReturn(PythonQuestionBankClient.SyncResponse.success("vector-store-test"));

        questionVectorSyncService.syncPendingQuestions();

        ArgumentCaptor<List<QuestionBankItem>> questionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pythonQuestionBankClient).syncQuestions(questionsCaptor.capture());
        assertThat(questionsCaptor.getValue().getFirst().getMedia()).hasSize(1);
    }
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
cd ai_interviewer_admin
./mvnw -Dtest=QuestionImportServiceTest,QuestionVectorSyncServiceTest test
```

Expected:

```text
CSV 表头不匹配
```

- [ ] **Step 3: Extend import row**

In `QuestionImportRow.java`:

```java
    private List<QuestionMediaRequest> media = List.of();
```

- [ ] **Step 4: Extend CSV headers and parsing**

In `QuestionImportService.java`, change `EXPECTED_HEADERS`:

```java
    private static final List<String> EXPECTED_HEADERS = List.of(
            "question_text",
            "answer_reference",
            "question_type",
            "difficulty",
            "tags",
            "skill_area",
            "job_id",
            "status",
            "media_urls",
            "media_captions");
```

In `toRow()`, add:

```java
        row.setMedia(parseMedia(columns.get(8), columns.get(9), rowNumber, rowErrors));
```

Add helper:

```java
    private List<QuestionMediaRequest> parseMedia(String rawUrls, String rawCaptions, int rowNumber, List<String> errors) {
        if (!StringUtils.hasText(rawUrls)) {
            return List.of();
        }
        List<String> urls = splitSemicolon(rawUrls);
        List<String> captions = splitSemicolon(rawCaptions);
        List<QuestionMediaRequest> media = new ArrayList<>();
        for (int index = 0; index < urls.size(); index++) {
            String url = urls.get(index);
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                errors.add(rowError(rowNumber, "media_urls 只支持 http:// 或 https://"));
                continue;
            }
            String caption = index < captions.size() ? captions.get(index) : null;
            media.add(new QuestionMediaRequest("image", url, caption, caption));
        }
        return media;
    }

    private List<String> splitSemicolon(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(";")) {
            if (StringUtils.hasText(item)) {
                values.add(item.trim());
            }
        }
        return values;
    }
```

In `toCreateRequest()`, add:

```java
        request.setMedia(row.getMedia());
```

- [ ] **Step 5: Parse Markdown image syntax**

Add pattern:

```java
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)]\\((https?://[^\\s)]+)\\)");
```

Inside `parseQuestionBlock()`, before field matching:

```java
            Matcher imageMatcher = MARKDOWN_IMAGE_PATTERN.matcher(line);
            if (imageMatcher.find()) {
                List<QuestionMediaRequest> media = new ArrayList<>(row.getMedia());
                String caption = trimToNull(imageMatcher.group(1));
                String url = imageMatcher.group(2).trim();
                media.add(new QuestionMediaRequest("image", url, caption, caption));
                row.setMedia(media);
                continue;
            }
```

- [ ] **Step 6: Send media to Python sync**

In `PythonQuestionBankClient.java`, import:

```java
import com.aiinterviewer.admin.questionbank.entity.QuestionMedia;
```

Change payload mapping:

```java
                        question.getTags() == null ? List.of() : question.getTags(),
                        question.getSkillArea(),
                        toMediaPayload(question.getMedia())))
```

Add method:

```java
    private List<MediaPayload> toMediaPayload(List<QuestionMedia> media) {
        if (media == null || media.isEmpty()) {
            return List.of();
        }
        return media.stream()
                .map(item -> new MediaPayload(
                        item.getMediaType(),
                        item.getMediaUrl(),
                        item.getCaption(),
                        item.getAltText()))
                .toList();
    }
```

Replace `QuestionPayload` record with:

```java
    public record QuestionPayload(
            Long id,
            @JsonProperty("question_text") String questionText,
            @JsonProperty("answer_reference") String answerReference,
            @JsonProperty("question_type") String questionType,
            String difficulty,
            List<String> tags,
            @JsonProperty("skill_area") String skillArea,
            List<MediaPayload> media) {
    }

    public record MediaPayload(
            String type,
            String url,
            String caption,
            String alt) {
    }
```

- [ ] **Step 7: Run admin import/sync tests**

Run:

```bash
cd ai_interviewer_admin
./mvnw -Dtest=QuestionImportServiceTest,QuestionVectorSyncServiceTest,QuestionMediaServiceTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 8: Commit**

```bash
git add ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/dto/QuestionImportRow.java \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/QuestionImportService.java \
  ai_interviewer_admin/src/main/java/com/aiinterviewer/admin/questionbank/client/PythonQuestionBankClient.java \
  ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionImportServiceTest.java \
  ai_interviewer_admin/src/test/java/com/aiinterviewer/admin/questionbank/QuestionVectorSyncServiceTest.java
git commit -m "feat(admin): import and sync question media"
```

## Task 7: Java Interview SSE Proxy Awareness

**Files:**

- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/sse/SSEEventType.java`
- Modify: `ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java`
- Test: `ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/SSEProxyServiceQuestionEventTest.java`

- [ ] **Step 1: Write failing unit test for question text extraction**

```java
// ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/SSEProxyServiceQuestionEventTest.java
package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.interview.sse.SSEEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SSEProxyServiceQuestionEventTest {

    @Test
    void questionEventIsKnownAndTextCanBeExtractedForPersistence() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String data = """
                {"question":{"id":"123","text":"请结合下图说明两个 Lua 脚本关系。","media":[{"type":"image","url":"https://example.com/figure.png","caption":"图 10-17"}]},"next_stage":"technical_qna"}
                """;

        String text = objectMapper.readTree(data).get("question").get("text").asText();

        assertThat(SSEEventType.fromValue("question")).isEqualTo(SSEEventType.QUESTION);
        assertThat(text).isEqualTo("请结合下图说明两个 Lua 脚本关系。");
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
cd ai_interview_backend
./mvnw -pl ai-interviewer-interview -Dtest=SSEProxyServiceQuestionEventTest test
```

Expected:

```text
cannot find symbol: variable QUESTION
```

- [ ] **Step 3: Add enum value**

In `SSEEventType.java`, add before `SCORE`:

```java
    /**
     * 结构化题目事件
     */
    QUESTION("question"),
```

- [ ] **Step 4: Persist text from question events**

In `SSEProxyService.handleSSEEvent()`, add switch case:

```java
                case "question" -> handleQuestionEvent(data, session, aiResponseRef);
```

Add method:

```java
    private void handleQuestionEvent(JsonNode data, InterviewSession session, AtomicReference<StringBuilder> aiResponseRef) {
        if (!data.has("question") || data.get("question").isNull()) {
            return;
        }
        JsonNode question = data.get("question");
        if (question.has("text") && !question.get("text").isNull()) {
            String text = question.get("text").asText();
            session.setLastQuestion(text);
            if (aiResponseRef.get().isEmpty()) {
                aiResponseRef.get().append(text);
            }
        }
    }
```

- [ ] **Step 5: Run interview module tests**

Run:

```bash
cd ai_interview_backend
./mvnw -pl ai-interviewer-interview test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/sse/SSEEventType.java \
  ai_interview_backend/ai-interviewer-interview/src/main/java/com/aiinterviewer/interview/service/SSEProxyService.java \
  ai_interview_backend/ai-interviewer-interview/src/test/java/com/aiinterviewer/interview/service/SSEProxyServiceQuestionEventTest.java
git commit -m "feat(interview): recognize rich question sse events"
```

## Task 8: Flutter Rich Question Rendering

**Files:**

- Create: `ai_interviewer_front/lib/models/question_media.dart`
- Modify: `ai_interviewer_front/lib/models/chat_message.dart`
- Modify: `ai_interviewer_front/lib/services/interview_service.dart`
- Modify: `ai_interviewer_front/lib/interview_chat_page.dart`
- Test: `ai_interviewer_front/test/question_media_test.dart`
- Test: `ai_interviewer_front/test/interview_service_question_event_test.dart`
- Test: `ai_interviewer_front/test/interview_chat_page_media_test.dart`

- [ ] **Step 1: Write failing Flutter model test**

```dart
// ai_interviewer_front/test/question_media_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:ai_interviewer_front/models/question_media.dart';

void main() {
  test('QuestionMedia parses image payload', () {
    final media = QuestionMedia.fromJson({
      'type': 'image',
      'url': 'https://example.com/figure.png',
      'caption': '图 10-17',
      'alt': 'Redis 限流图',
    });

    expect(media.type, 'image');
    expect(media.url, 'https://example.com/figure.png');
    expect(media.caption, '图 10-17');
    expect(media.alt, 'Redis 限流图');
  });
}
```

- [ ] **Step 2: Run model test and verify it fails**

Run:

```bash
cd ai_interviewer_front
flutter test test/question_media_test.dart
```

Expected:

```text
Error: Can't read 'lib/models/question_media.dart'
```

- [ ] **Step 3: Add media model**

```dart
// ai_interviewer_front/lib/models/question_media.dart
class QuestionMedia {
  final String type;
  final String url;
  final String? caption;
  final String? alt;

  const QuestionMedia({
    required this.type,
    required this.url,
    this.caption,
    this.alt,
  });

  factory QuestionMedia.fromJson(Map<String, dynamic> json) {
    return QuestionMedia(
      type: (json['type'] as String?)?.trim().isNotEmpty == true ? json['type'] as String : 'image',
      url: json['url'] as String,
      caption: json['caption'] as String?,
      alt: json['alt'] as String?,
    );
  }
}
```

- [ ] **Step 4: Add media to ChatMessage**

```dart
// ai_interviewer_front/lib/models/chat_message.dart
import 'question_media.dart';

class ChatMessage {
  final bool isAI;
  final String content;
  final String time;
  final List<QuestionMedia> media;

  ChatMessage({
    required this.isAI,
    required this.content,
    required this.time,
    this.media = const [],
  });
}
```

- [ ] **Step 5: Handle `question` event in InterviewService**

In `_handleEvent()`, add case before `chunk`:

```dart
      case 'question':
        final question = data['question'];
        if (question is Map<String, dynamic>) {
          final content = question['text'] as String? ?? '';
          final rawMedia = question['media'];
          final media = rawMedia is List
              ? rawMedia
                  .whereType<Map<String, dynamic>>()
                  .map(QuestionMedia.fromJson)
                  .toList()
              : <QuestionMedia>[];

          if (content.isNotEmpty) {
            _messages.add(ChatMessage(
              isAI: true,
              content: content,
              time: _getCurrentTime(),
              media: media,
            ));
            _currentStreamContent = content;
            notifyListeners();
          }
        }
        break;
```

Add import:

```dart
import '../models/question_media.dart';
```

Modify the `chunk` case to avoid duplicating the same structured question:

```dart
        if (_messages.isNotEmpty && _messages.last.isAI && _messages.last.content == _currentStreamContent && _messages.last.media.isNotEmpty) {
          break;
        }
```

- [ ] **Step 6: Render image cards and preview**

In `InterviewChatPage`, replace the message bubble `child: Text(...)` with:

```dart
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        message.content,
                        style: TextStyle(
                          fontSize: 15,
                          color: message.isAI ? const Color(0xFF1E2939) : Colors.white,
                          height: 1.5,
                        ),
                      ),
                      if (message.media.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        ...message.media.map((media) => _buildMediaCard(context, media)),
                      ],
                    ],
                  ),
```

Add helper methods in `_InterviewChatPageState`:

```dart
  Widget _buildMediaCard(BuildContext context, QuestionMedia media) {
    if (media.type != 'image') {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: GestureDetector(
        onTap: () => _showImagePreview(context, media),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Image.network(
                media.url,
                fit: BoxFit.cover,
                height: 180,
                width: double.infinity,
                errorBuilder: (_, __, ___) => Container(
                  height: 120,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF3F4F6),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Text(
                    '图片加载失败，请稍后重试',
                    style: TextStyle(color: Color(0xFF6A7282)),
                  ),
                ),
              ),
            ),
            if (media.caption != null && media.caption!.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                media.caption!,
                style: const TextStyle(fontSize: 12, color: Color(0xFF6A7282)),
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _showImagePreview(BuildContext context, QuestionMedia media) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        insetPadding: const EdgeInsets.all(16),
        child: InteractiveViewer(
          child: Image.network(
            media.url,
            fit: BoxFit.contain,
            errorBuilder: (_, __, ___) => const Padding(
              padding: EdgeInsets.all(32),
              child: Text('图片加载失败，请稍后重试'),
            ),
          ),
        ),
      ),
    );
  }
```

Add import:

```dart
import 'models/question_media.dart';
```

- [ ] **Step 7: Run Flutter tests**

Run:

```bash
cd ai_interviewer_front
flutter test test/question_media_test.dart
```

Expected:

```text
00:00 +1: All tests passed!
```

- [ ] **Step 8: Commit**

```bash
git add ai_interviewer_front/lib/models/question_media.dart \
  ai_interviewer_front/lib/models/chat_message.dart \
  ai_interviewer_front/lib/services/interview_service.dart \
  ai_interviewer_front/lib/interview_chat_page.dart \
  ai_interviewer_front/test/question_media_test.dart
git commit -m "feat(front): render rich technical questions"
```

## Task 9: Admin Web Console Media Fields

**Files:**

- Modify: `ai_interviewer_admin_front/src/types.ts`
- Modify: `ai_interviewer_admin_front/src/api.ts`
- Modify: `ai_interviewer_admin_front/src/App.tsx`

- [ ] **Step 1: Add TypeScript media types**

In `types.ts`:

```ts
export interface QuestionMedia {
  id?: number;
  questionId?: number;
  mediaType?: string;
  mediaUrl: string;
  caption?: string | null;
  altText?: string | null;
  sortOrder?: number | null;
}
```

Add to `QuestionRow`:

```ts
  media?: QuestionMedia[];
```

Add to `QuestionCreatePayload`:

```ts
  media?: Array<{ type: string; url: string; caption?: string; alt?: string }>;
```

- [ ] **Step 2: Add form fields and payload mapping**

In `QuestionDialog`, extend state:

```ts
    mediaUrls: '',
    mediaCaptions: '',
```

In submit, build media:

```ts
      const mediaUrls = form.mediaUrls
        .split(';')
        .map((item) => item.trim())
        .filter(Boolean);
      const mediaCaptions = form.mediaCaptions
        .split(';')
        .map((item) => item.trim());
      const media = mediaUrls.map((url, index) => ({
        type: 'image',
        url,
        caption: mediaCaptions[index] || '',
        alt: mediaCaptions[index] || '',
      }));
```

Include media in payload:

```ts
        media,
```

Add inputs under skill area/tags:

```tsx
        <input
          placeholder="图片 URL，多个用英文分号分隔"
          value={form.mediaUrls}
          onChange={(event) => setForm({ ...form, mediaUrls: event.target.value })}
        />
        <input
          placeholder="图片图注，多个用英文分号分隔"
          value={form.mediaCaptions}
          onChange={(event) => setForm({ ...form, mediaCaptions: event.target.value })}
        />
```

- [ ] **Step 3: Show media count in question list**

In the questions table header, add:

```tsx
            <th>图片</th>
```

In row rendering after skill area:

```tsx
              <td>{row.media?.length ? `${row.media.length} 张` : '-'}</td>
```

- [ ] **Step 4: Run frontend build**

Run:

```bash
cd ai_interviewer_admin_front
npm run build
```

Expected:

```text
✓ built
```

- [ ] **Step 5: Commit**

```bash
git add ai_interviewer_admin_front/src/types.ts ai_interviewer_admin_front/src/api.ts ai_interviewer_admin_front/src/App.tsx
git commit -m "feat(admin-web): manage question media urls"
```

## Task 10: End-to-End Verification and Documentation

**Files:**

- Modify: `docs/TECHNICAL_INTERVIEW_RICH_QUESTION_IMPLEMENTATION_PLAN.md`
- Optional modify: `docs/ARCHITECTURE.md`

- [ ] **Step 1: Run Python tests**

Run:

```bash
cd ai_interviewer
uv run pytest tests/test_question_item.py tests/test_rich_question_bank.py tests/test_rich_question_interview_flow.py tests/test_rich_question_sse.py tests/test_question_bank_hybrid.py -q
```

Expected:

```text
passed
```

- [ ] **Step 2: Run Admin backend tests**

Run:

```bash
cd ai_interviewer_admin
./mvnw -Dtest=QuestionMediaServiceTest,QuestionImportServiceTest,QuestionVectorSyncServiceTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Run Java interview/gateway build check**

Run:

```bash
cd ai_interview_backend
./mvnw -pl ai-interviewer-interview,ai-interviewer-gateway test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Run Flutter test**

Run:

```bash
cd ai_interviewer_front
flutter test test/question_media_test.dart
```

Expected:

```text
All tests passed!
```

- [ ] **Step 5: Run Admin web build**

Run:

```bash
cd ai_interviewer_admin_front
npm run build
```

Expected:

```text
✓ built
```

- [ ] **Step 6: Manual SSE smoke with a rich question**

Start services with the existing local stack. Use the repo's current Docker path:

```bash
cd ai_interview_backend
docker compose up -d --build
```

Then import one CSV row through Admin or direct endpoint. Use this CSV body:

```csv
question_text,answer_reference,question_type,difficulty,tags,skill_area,job_id,status,media_urls,media_captions
请结合下图说明 getToken_access_limit.lua 和 rate_limiter.lua 的关系。,getToken_access_limit.lua 是入口脚本，rate_limiter.lua 是限流判断脚本。,TECHNICAL,MEDIUM,Redis;Lua;限流,Redis,,1,https://example.com/figure-10-17.png,图 10-17 getToken_access_limit.lua 脚本和 rate_limiter.lua 脚本关系
```

Verify Python SSE contains both events:

```text
event: question
data: {"question":{"text":"请结合下图说明 getToken_access_limit.lua 和 rate_limiter.lua 的关系。","media":[{"type":"image","url":"https://example.com/figure-10-17.png","caption":"图 10-17 getToken_access_limit.lua 脚本和 rate_limiter.lua 脚本关系"}]},"next_stage":"technical_qna"}

event: chunk
data: {"content":"请结合下图说明 getToken_access_limit.lua"}
```

- [ ] **Step 7: Update docs with implemented status**

Append this section to `docs/TECHNICAL_INTERVIEW_RICH_QUESTION_IMPLEMENTATION_PLAN.md`:

```markdown
## 18. 第一阶段落地状态

第一阶段已实现结构化图文题主链路：

1. 后台题库支持 `t_question_media` 保存图片 URL、图注和说明。
2. CSV 与 Markdown 导入支持图片 URL。
3. Admin 向 Python 同步题目时携带 `media`。
4. Python Chroma metadata 保存 `media_json`，检索返回 `QuestionItem`。
5. 技术面试 SSE 新增 `question` 事件，同时保留 `chunk` 文本兼容旧前端。
6. Flutter 技术面试页展示题干、图片、图注和图片加载失败兜底。

未实现内容仍按第二期处理：PDF/DOCX 内嵌图片抽取、OCR、图片向量化、本地图片上传到 MinIO。
```

- [ ] **Step 8: Final commit**

```bash
git add docs/TECHNICAL_INTERVIEW_RICH_QUESTION_IMPLEMENTATION_PLAN.md docs/ARCHITECTURE.md
git commit -m "docs: record rich question implementation status"
```

## Self-Review

### Spec Coverage

| Requirement | Covered By |
|---|---|
| 后台题库题目可以绑定图片 | Task 5 |
| CSV 可以带图片 URL | Task 6 |
| Markdown 可以带图片 URL | Task 6 |
| 图片 URL 同步到 Python | Task 2 and Task 6 |
| 技术题检索返回结构化题目对象 | Task 2 and Task 3 |
| 技术面试 SSE 下发图文题 | Task 4 |
| Flutter 展示题干和图片 | Task 8 |
| 旧纯文本题继续可用 | Task 1, Task 3, Task 4, Task 8 |
| Java Interview SSE 代理不吞 `question` 事件 | Task 7 |
| 后台管理前端支持图片 URL | Task 9 |

### Placeholder Scan

The plan avoids unspecified placeholder steps. Each code-changing step names files, shows concrete code, and includes a verification command with expected result.

### Type Consistency

The canonical wire shape is:

```json
{
  "id": "123",
  "text": "请结合下图说明两个 Lua 脚本关系。",
  "question_type": "TECHNICAL",
  "difficulty": "MEDIUM",
  "skill_area": "Redis",
  "tags": ["Redis", "Lua"],
  "media": [
    {
      "type": "image",
      "url": "https://example.com/figure.png",
      "caption": "图 10-17",
      "alt": "Redis 限流图"
    }
  ],
  "answer_reference": "入口脚本调用底层限流脚本。"
}
```

Java Admin persistence uses `mediaType`, `mediaUrl`, `caption`, `altText`; Python and Flutter wire payloads use `type`, `url`, `caption`, `alt`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-30-technical-interview-rich-question.md`. Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Choose one execution path before starting implementation.
