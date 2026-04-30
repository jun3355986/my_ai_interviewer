from __future__ import annotations

import json
from typing import Any

from langchain_core.documents import Document
from pydantic import BaseModel, Field, field_validator, model_validator


class QuestionMedia(BaseModel):
    type: str = "image"
    url: str
    caption: str | None = None
    alt: str | None = None

    @field_validator("type", mode="before")
    @classmethod
    def normalize_type(cls, value: Any) -> str:
        media_type = str(value or "").strip().lower()
        return media_type or "image"

    @field_validator("url")
    @classmethod
    def validate_url(cls, value: str) -> str:
        value = value.strip()
        if not value.startswith(("http://", "https://")):
            raise ValueError("media url must start with http:// or https://")
        return value


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
        value = value.strip()
        if not value:
            raise ValueError("question text must not be empty")
        return value

    @field_validator("tags", mode="before")
    @classmethod
    def normalize_tags(cls, value: Any) -> list[str]:
        return cls._parse_tags(value)

    @model_validator(mode="before")
    @classmethod
    def normalize_legacy_text_keys(cls, data: Any) -> Any:
        if not isinstance(data, dict):
            return data

        normalized = dict(data)
        for key in ("text", "question_text", "content"):
            value = normalized.get(key)
            if cls._empty_to_none(value):
                normalized["text"] = value
                return normalized
        return normalized

    @classmethod
    def from_legacy(
        cls,
        question: str | dict[str, Any] | "QuestionItem",
    ) -> "QuestionItem":
        if isinstance(question, cls):
            return question
        if isinstance(question, str):
            return cls(text=question)
        if isinstance(question, dict):
            return cls.model_validate(dict(question))
        raise ValueError(f"unsupported legacy question type: {type(question).__name__}")

    @classmethod
    def from_document(cls, document: Document) -> "QuestionItem":
        metadata = document.metadata or {}
        return cls(
            id=cls._optional_str(metadata.get("question_id")),
            text=cls._empty_to_none(metadata.get("question_text")) or cls._first_non_empty_line(document.page_content),
            question_type=cls._optional_str(metadata.get("question_type")),
            difficulty=cls._optional_str(metadata.get("difficulty")),
            skill_area=cls._optional_str(metadata.get("skill_area")),
            tags=cls._parse_tags(metadata.get("tags")),
            media=cls._parse_media(metadata.get("media_json")),
            answer_reference=cls._optional_str(metadata.get("answer_reference")),
        )

    def to_public_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "text": self.text,
            "question_type": self.question_type,
            "difficulty": self.difficulty,
            "skill_area": self.skill_area,
            "tags": list(self.tags),
            "media": [media.model_dump() for media in self.media],
            "answer_reference": self.answer_reference,
        }

    def to_history_message(self) -> dict[str, Any]:
        payload = self.to_public_dict()
        return {"role": "ai", "content": self.text, "question": payload}

    def to_pool_dict(self) -> dict[str, Any]:
        return self.to_public_dict()

    @staticmethod
    def _optional_str(value: Any) -> str | None:
        if value is None:
            return None
        return str(value)

    @staticmethod
    def _empty_to_none(value: Any) -> str | None:
        if value is None:
            return None
        text = str(value).strip()
        return text or None

    @staticmethod
    def _parse_tags(value: Any) -> list[str]:
        if value is None:
            return []
        if isinstance(value, str):
            return [tag.strip() for tag in value.split(",") if tag.strip()]
        if isinstance(value, list):
            return [str(tag).strip() for tag in value if str(tag).strip()]
        return [str(value).strip()]

    @staticmethod
    def _parse_media(value: Any) -> list[QuestionMedia]:
        if not value:
            return []
        try:
            media_items = json.loads(value) if isinstance(value, str) else value
        except json.JSONDecodeError:
            return []
        if not isinstance(media_items, list):
            return []
        return [QuestionMedia.model_validate(item) for item in media_items if isinstance(item, dict)]

    @staticmethod
    def _first_non_empty_line(value: str) -> str:
        for line in value.splitlines():
            stripped = line.strip()
            if stripped:
                return stripped
        return value
