import os

import pytest

from conftest import encode_query, request_json


@pytest.mark.live_api
def test_job_search_returns_success(gateway_base_url, auth_headers):
    keyword = encode_query(os.getenv("SMOKE_JOB_KEYWORD", "java"))

    status, data, _headers = request_json(
        gateway_base_url,
        "GET",
        f"/api/v1/jobs/search?keyword={keyword}",
        headers=auth_headers,
    )

    assert status == 200
    assert data.get("code") == 200
    assert "data" in data
