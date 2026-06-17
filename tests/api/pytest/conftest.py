import json
import os
import urllib.error
import urllib.parse
import urllib.request

import pytest


def pytest_configure(config):
    config.addinivalue_line("markers", "live_api: requires a running gateway")
    config.addinivalue_line("markers", "sse: can call the real AI streaming path")


def pytest_collection_modifyitems(config, items):
    if os.getenv("RUN_LIVE_API_TESTS", "0").lower() not in {"1", "true", "yes", "on"}:
        skip_live = pytest.mark.skip(reason="set RUN_LIVE_API_TESTS=1 to run live API tests")
        for item in items:
            if "live_api" in item.keywords:
                item.add_marker(skip_live)

    if os.getenv("RUN_SSE_API_TESTS", "0").lower() not in {"1", "true", "yes", "on"}:
        skip_sse = pytest.mark.skip(reason="set RUN_SSE_API_TESTS=1 to run SSE tests")
        for item in items:
            if "sse" in item.keywords:
                item.add_marker(skip_sse)


@pytest.fixture(scope="session")
def gateway_base_url():
    return os.getenv("GATEWAY_BASE_URL", "http://localhost:9000").rstrip("/")


@pytest.fixture(scope="session")
def smoke_credentials():
    return {
        "username": os.getenv("SMOKE_USERNAME", "admin"),
        "password": os.getenv("SMOKE_PASSWORD", "admin123"),
    }


def request_json(base_url, method, path, payload=None, headers=None, timeout=30):
    body = None
    request_headers = {"Accept": "application/json"}
    if headers:
        request_headers.update(headers)
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        request_headers["Content-Type"] = "application/json"

    request = urllib.request.Request(
        f"{base_url}{path}",
        data=body,
        headers=request_headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
            return response.status, json.loads(raw) if raw else {}, dict(response.headers)
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            data = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            data = {"raw": raw}
        return exc.code, data, dict(exc.headers)


@pytest.fixture(scope="session")
def auth_token(gateway_base_url, smoke_credentials):
    status, data, _headers = request_json(
        gateway_base_url,
        "POST",
        "/api/v1/auth/login",
        smoke_credentials,
    )
    assert status == 200, data
    assert data.get("code") == 200, data
    token = (data.get("data") or {}).get("accessToken")
    assert token, data
    return token


@pytest.fixture
def auth_headers(auth_token):
    return {"Authorization": f"Bearer {auth_token}"}


def encode_query(value):
    return urllib.parse.quote(str(value), safe="")
