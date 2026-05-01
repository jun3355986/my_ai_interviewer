import json
from collections.abc import Iterable


EVENT_STATUS = "status"
EVENT_CHUNK = "chunk"
EVENT_QUESTION = "question"
EVENT_SCORE = "score"
EVENT_RESULT = "result"
EVENT_DONE = "done"
EVENT_ERROR = "error"


def format_sse(event: str, data: dict[str, object]) -> str:
    payload = json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n"


def stream_text_chunks(text: str, chunk_size: int = 20) -> Iterable[str]:
    if not text:
        return
    for index in range(0, len(text), chunk_size):
        yield text[index : index + chunk_size]
