import json
import inspect
import os
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path

import pytest

from conftest import encode_query, request_json


REPO_ROOT = Path(__file__).resolve().parents[3]
TESTS_ROOT = REPO_ROOT / "tests"
FIXTURE_PATH = TESTS_ROOT / "fixtures" / "payloads" / "ai-observability-chat.json"
TEST_CASES_PATH = TESTS_ROOT / "docs" / "test-cases.md"
TOOLING_GUIDE_PATH = TESTS_ROOT / "docs" / "tooling-guide.md"


def _truthy_env(name, default="0"):
    return os.getenv(name, default).lower() in {"1", "true", "yes", "on"}


def _load_fixture():
    with FIXTURE_PATH.open(encoding="utf-8") as file:
        return json.load(file)


def _admin_base_url(gateway_base_url):
    return os.getenv("ADMIN_API_BASE_URL", gateway_base_url).rstrip("/")


def _admin_headers(admin_base_url):
    credentials = {
        "username": os.getenv("ADMIN_SMOKE_USERNAME", "admin"),
        "password": os.getenv("ADMIN_SMOKE_PASSWORD", "admin123"),
    }
    status, data, _headers = request_json(
        admin_base_url,
        "POST",
        "/admin/auth/login",
        credentials,
    )
    assert status == 200, data
    assert data.get("code") == 200, data
    token = (data.get("data") or {}).get("accessToken")
    assert token, data
    return {"Authorization": f"Bearer {token}"}


def _admin_get(admin_base_url, path, headers):
    status, data, _headers = request_json(admin_base_url, "GET", path, headers=headers)
    assert status == 200, data
    assert data.get("code") == 200, data
    return data.get("data") or {}


def _trace_records(admin_base_url, headers):
    data = _admin_get(
        admin_base_url,
        "/admin/ai-observability/traces?current=1&size=20"
        "&businessType=interview&entrypoint=interview_chat",
        headers,
    )
    return data.get("records") or []


def _wait_for_new_trace(admin_base_url, headers, known_ids, timeout_seconds):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        for record in _trace_records(admin_base_url, headers):
            trace_id = record.get("id")
            if trace_id and trace_id not in known_ids:
                return record
        time.sleep(2)
    return None


def _read_sse_prefix(gateway_base_url, auth_headers, payload):
    headers = {
        **auth_headers,
        "Accept": "text/event-stream",
        "Content-Type": "application/json",
    }
    request = urllib.request.Request(
        f"{gateway_base_url}/api/v1/interviews/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(
        request,
        timeout=int(os.getenv("AI_OBSERVABILITY_SSE_MAX_TIME", os.getenv("SSE_MAX_TIME", "60"))),
    ) as response:
        body = response.read(8192).decode("utf-8", errors="replace")
        content_type = response.headers.get("content-type", "")
    assert response.status == 200
    assert "text/event-stream" in content_type.lower()
    assert "data:" in body
    return body


def _psql_count_access_logs():
    db_url = os.getenv("AI_OBSERVABILITY_DB_URL", "").strip()
    if not db_url or shutil.which("psql") is None:
        return None
    if db_url.startswith("postgresql+psycopg://"):
        db_url = "postgresql://" + db_url.removeprefix("postgresql+psycopg://")
    result = subprocess.run(
        [
            "psql",
            db_url,
            "-At",
            "-c",
            "SELECT COUNT(*) FROM t_ai_observability_access_log",
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=10,
    )
    if result.returncode != 0:
        return None
    try:
        return int(result.stdout.strip())
    except ValueError:
        return None


def test_ai_observability_fixture_and_docs_contract():
    payload = _load_fixture()

    assert payload["sessionId"] is None
    assert "AI 观测中心" in payload["message"]
    assert "Spring Boot" in payload["resumeContent"]
    assert "PostgreSQL" in payload["jobRequirements"]
    assert payload["candidateName"]

    smoke_parameters = inspect.signature(test_ai_observability_cross_service_admin_smoke).parameters
    assert "auth_headers" not in smoke_parameters

    test_cases = TEST_CASES_PATH.read_text(encoding="utf-8")
    tooling_guide = TOOLING_GUIDE_PATH.read_text(encoding="utf-8")

    for test_id in ("P1-OBS-002", "P1-OBS-003", "P1-OBS-008", "P1-OBS-009"):
        assert test_id in test_cases

    for env_var in (
        "AI_OBSERVABILITY_ENABLED",
        "AI_OBSERVABILITY_DB_URL",
        "AI_OBSERVABILITY_WRITE_TIMEOUT_MS",
        "AI_OBSERVABILITY_STORE_RAW_PAYLOAD",
        "AI_OBSERVABILITY_MAX_RAW_CHARS",
        "ADMIN_API_BASE_URL",
    ):
        assert env_var in tooling_guide


@pytest.fixture
def ai_observability_auth_headers(request):
    if not _truthy_env("RUN_AI_OBSERVABILITY_API_TESTS"):
        pytest.skip("set RUN_AI_OBSERVABILITY_API_TESTS=1 to run AI observability live smoke")
    return request.getfixturevalue("auth_headers")


@pytest.mark.live_api
@pytest.mark.sse
def test_ai_observability_cross_service_admin_smoke(
    gateway_base_url,
    ai_observability_auth_headers,
):
    admin_base_url = _admin_base_url(gateway_base_url)
    admin_headers = _admin_headers(admin_base_url)
    known_ids = {record.get("id") for record in _trace_records(admin_base_url, admin_headers)}

    _read_sse_prefix(gateway_base_url, ai_observability_auth_headers, _load_fixture())

    trace = _wait_for_new_trace(
        admin_base_url,
        admin_headers,
        known_ids,
        int(os.getenv("AI_OBSERVABILITY_TRACE_WAIT_SECONDS", "45")),
    )
    assert trace, "AI observability trace was not visible in admin list after interview chat"

    trace_id = trace["id"]
    detail = _admin_get(admin_base_url, f"/admin/ai-observability/traces/{trace_id}", admin_headers)
    assert detail.get("id") == trace_id
    assert detail.get("businessType") == "interview"
    assert detail.get("entrypoint") == "interview_chat"
    assert detail.get("metadataJson")

    llm_calls = detail.get("llmCalls") or []
    assert llm_calls, detail
    call_id = llm_calls[0]["id"]

    stats = _admin_get(
        admin_base_url,
        "/admin/ai-observability/stats?businessType=interview&entrypoint=interview_chat",
        admin_headers,
    )
    assert stats.get("traceCount", 0) >= 1
    assert "providerPromptCacheTokenHitRate" in stats
    assert "providerPromptCacheCallHitRate" in stats
    assert "providerCacheUnreportedCalls" in stats

    access_log_count_before = _psql_count_access_logs()
    for raw_type in ("PROMPT", "RESPONSE"):
        raw = _admin_get(
            admin_base_url,
            f"/admin/ai-observability/llm-calls/{call_id}/raw?type={encode_query(raw_type)}",
            admin_headers,
        )
        assert raw.get("callId") == call_id
        assert raw.get("traceId") == trace_id
        assert raw.get("accessType") == raw_type
        assert "rawText" in raw

    access_log_count_after = _psql_count_access_logs()
    if access_log_count_before is not None and access_log_count_after is not None:
        assert access_log_count_after >= access_log_count_before + 2
