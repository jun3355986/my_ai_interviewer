import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient

from api.resume_router import router


def _client() -> TestClient:
    from fastapi import FastAPI

    app = FastAPI()
    app.include_router(router)
    return TestClient(app)


def test_parse_resume_returns_raw_text_for_txt_upload():
    resume_text = (
        "姓名：张三\n"
        "工作经验：5年\n"
        "技能：Java, Spring Boot, Redis\n"
        "项目：电商订单系统 {\"legacy\": \"json-in-resume\"}\n"
    )
    response = _client().post(
        "/resume/parse",
        files={"file": ("resume.txt", resume_text.encode("utf-8"), "text/plain")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["rawText"].strip() == resume_text.strip()
    # otherInfo 仍保留原文（前 6000 字），作为旧数据读侧回退来源
    assert payload["otherInfo"].startswith("姓名：张三")
    assert payload["name"] == "张三"


def test_parse_resume_raw_text_survives_structured_fields():
    resume_text = "Name: Li Si\nExperience: 3 years\nSkills: Docker, K8s\n"
    response = _client().post(
        "/resume/parse",
        files={"file": ("resume.md", resume_text.encode("utf-8"), "text/markdown")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["rawText"].strip() == resume_text.strip()
    assert payload["workYears"] == "3 years"
