import pytest

from conftest import request_json


@pytest.mark.live_api
def test_gateway_health(gateway_base_url):
    status, data, _headers = request_json(gateway_base_url, "GET", "/actuator/health")

    assert status == 200
    assert data.get("status") in {"UP", "UNKNOWN"}


@pytest.mark.live_api
def test_login_returns_access_token(gateway_base_url, smoke_credentials):
    status, data, _headers = request_json(
        gateway_base_url,
        "POST",
        "/api/v1/auth/login",
        smoke_credentials,
    )

    assert status == 200
    assert data.get("code") == 200
    assert (data.get("data") or {}).get("accessToken")
