import pytest
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


def test_question_item_from_document_skips_invalid_media_items():
    document = Document(
        page_content="请结合下图说明脚本关系。",
        metadata={
            "media_json": (
                "["
                '{"type":"image","url":"ftp://example.com/bad.png","caption":"坏图"},'
                '{"type":"image","url":"https://example.com/good.png","caption":"好图"}'
                "]"
            )
        },
    )

    item = QuestionItem.from_document(document)

    assert len(item.media) == 1
    assert item.media[0].url == "https://example.com/good.png"


def test_question_media_rejects_non_http_url():
    with pytest.raises(ValueError):
        QuestionMedia(url="ftp://example.com/figure.png")


def test_question_media_normalizes_type_and_url():
    default_media = QuestionMedia(type="  ", url=" https://example.com/default.png ")
    alias_media = QuestionMedia(type=" IMAGE ", url=" http://example.com/alias.png ")

    assert default_media.type == "image"
    assert default_media.url == "https://example.com/default.png"
    assert alias_media.type == "image"
    assert alias_media.url == "http://example.com/alias.png"


def test_question_item_rejects_empty_text_after_trim():
    with pytest.raises(ValueError):
        QuestionItem(text="  \n ")


def test_question_item_from_legacy_dict_accepts_content_keys_and_structured_fields():
    item = QuestionItem.from_legacy(
        {
            "content": "  请说明 Redis 限流脚本。 ",
            "tags": "Redis, Lua",
            "answer_reference": "脚本原子执行。",
            "media": [{"type": " IMAGE ", "url": " https://example.com/figure.png "}],
        }
    )

    assert item.text == "请说明 Redis 限流脚本。"
    assert item.tags == ["Redis", "Lua"]
    assert item.answer_reference == "脚本原子执行。"
    assert item.media[0].type == "image"
    assert item.media[0].url == "https://example.com/figure.png"


def test_question_item_from_legacy_dict_uses_content_when_text_is_blank():
    item = QuestionItem.from_legacy(
        {
            "text": "   ",
            "content": "fallback question",
        }
    )

    assert item.text == "fallback question"


def test_question_item_from_document_uses_first_non_empty_line_and_ignores_invalid_media_json_shape():
    document = Document(
        page_content="\n\n  第一行题目  \n第二行补充",
        metadata={"media_json": '{"type":"image","url":"https://example.com/figure.png"}'},
    )

    item = QuestionItem.from_document(document)

    assert item.text == "第一行题目"
    assert item.media == []


def test_question_item_from_document_uses_page_content_when_question_text_is_blank():
    document = Document(
        page_content="\n\n  fallback question  \n第二行补充",
        metadata={"question_text": "   "},
    )

    item = QuestionItem.from_document(document)

    assert item.text == "fallback question"


def test_to_public_dict_does_not_expose_internal_tags_list():
    item = QuestionItem(text="请解释 Redis Lua 限流脚本关系。", tags=["Redis"])

    payload = item.to_public_dict()
    payload["tags"].append("Lua")

    assert item.tags == ["Redis"]
    assert payload["tags"] == ["Redis", "Lua"]
