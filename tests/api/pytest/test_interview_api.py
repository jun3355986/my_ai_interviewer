import json
import os
import urllib.request

import pytest


@pytest.mark.live_api
@pytest.mark.sse
def test_interview_chat_sse_returns_stream(gateway_base_url, auth_headers):
    payload = {
        "sessionId": None,
        "message": os.getenv("SMOKE_MESSAGE", "I am ready for the interview."),
        "resumeId": None,
        "jobId": None,
    }
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

    with urllib.request.urlopen(request, timeout=int(os.getenv("SSE_MAX_TIME", "45"))) as response:
        body = response.read(4096).decode("utf-8", errors="replace")
        content_type = response.headers.get("content-type", "")

    assert response.status == 200
    assert "text/event-stream" in content_type.lower()
    assert "data:" in body
