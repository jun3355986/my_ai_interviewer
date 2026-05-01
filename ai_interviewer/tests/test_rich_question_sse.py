import json

from api.sse import EVENT_QUESTION, format_sse


def test_question_sse_event_serializes_question_payload():
    raw = format_sse(
        EVENT_QUESTION,
        {
            "question": {
                "id": "123",
                "text": "请结合下图说明两个 Lua 脚本关系。",
                "media": [
                    {
                        "type": "image",
                        "url": "https://example.com/figure.png",
                        "caption": "图 10-17",
                        "alt": None,
                    }
                ],
            },
            "next_stage": "technical_qna",
        },
    )

    event_line, data_line, blank = raw.splitlines()
    payload = json.loads(data_line.removeprefix("data: "))

    assert event_line == "event: question"
    assert payload["question"]["media"][0]["url"] == "https://example.com/figure.png"
    assert blank == ""
