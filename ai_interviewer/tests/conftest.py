import os
import sys
import tempfile
from pathlib import Path

os.environ.setdefault(
    "AI_INTERVIEW_DB_PATH",
    str(Path(tempfile.mkdtemp(prefix="ai-interviewer-pytest-")) / "interviews.sqlite3"),
)
os.environ.setdefault(
    "AI_INTERVIEW_VECTOR_DB_PATH",
    str(Path(tempfile.mkdtemp(prefix="ai-interviewer-chroma-pytest-")) / "vector-db"),
)

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
