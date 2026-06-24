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
    bank.RRF_K = QuestionBank.RRF_K
    bank.VECTOR_WEIGHT = QuestionBank.VECTOR_WEIGHT
    bank.KEYWORD_WEIGHT = QuestionBank.KEYWORD_WEIGHT
    bank.METADATA_MATCH_BOOST = QuestionBank.METADATA_MATCH_BOOST
    bank.HOTNESS_BOOST = QuestionBank.HOTNESS_BOOST
    return bank


def test_sync_structured_questions_writes_media_json_and_caption_text():
    bank = make_bank()

    result = bank.sync_structured_questions(
        [
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
        ]
    )

    document = bank.vectorstore.added_documents[0]
    metadata = document.metadata

    assert result == [{"id": 123, "vector_store_id": "admin-question-123"}]
    assert "图注：图 10-17" in document.page_content
    assert "图片说明：Redis 限流图" in document.page_content
    assert "https://example.com/figure.png" not in document.page_content
    assert json.loads(metadata["media_json"])[0]["url"] == "https://example.com/figure.png"
    assert metadata["question_text"] == "请结合下图说明两个 Lua 脚本关系。"
    assert metadata["answer_reference"] == "入口脚本调用底层限流脚本。"


def test_search_question_items_returns_structured_media(monkeypatch):
    bank = make_bank()
    monkeypatch.setattr(bank, "_keyword_search", lambda query, k: [])

    items = bank.search_question_items("Redis Lua 限流", question_types=["TECHNICAL"], k=1)

    assert len(items) == 1
    assert items[0].id == "123"
    assert items[0].text == "请结合下图说明两个 Lua 脚本关系。"
    assert items[0].media[0].caption == "图 10-17"


def test_search_question_items_skips_invalid_media_json_items(monkeypatch):
    bank = make_bank()
    monkeypatch.setattr(
        bank.vectorstore,
        "similarity_search",
        lambda query, k: [
            Document(
                page_content="请结合下图说明两个 Lua 脚本关系。",
                metadata={
                    "question_id": "124",
                    "question_text": "请结合下图说明两个 Lua 脚本关系。",
                    "media_json": (
                        "["
                        '{"type":"image","url":"ftp://example.com/bad.png","caption":"坏图"},'
                        '{"type":"image","url":"https://example.com/good.png","caption":"好图"}'
                        "]"
                    ),
                },
            )
        ],
    )
    monkeypatch.setattr(bank, "_keyword_search", lambda query, k: [])

    items = bank.search_question_items("Redis Lua 限流", question_types=["TECHNICAL"], k=1)

    assert len(items) == 1
    assert len(items[0].media) == 1
    assert items[0].media[0].url == "https://example.com/good.png"
