from pathlib import Path


def test_database_engine_uses_injected_sqlite_path(monkeypatch, tmp_path):
    database_path = tmp_path / "injected-interviews.sqlite3"
    monkeypatch.setenv("AI_INTERVIEW_DB_PATH", str(database_path))

    from services import database

    engine = database.get_db_engine()
    try:
        assert Path(engine.url.database) == database_path
    finally:
        engine.dispose()


def test_question_bank_uses_injected_vector_directory(monkeypatch, tmp_path):
    from services import question_bank

    captured = {}

    class FakeChroma:
        def __init__(self, **kwargs):
            captured.update(kwargs)

    vector_path = tmp_path / "vector-db"
    monkeypatch.setenv("AI_INTERVIEW_VECTOR_DB_PATH", str(vector_path))
    monkeypatch.setattr(question_bank, "get_embeddings", lambda: object())
    monkeypatch.setattr(question_bank, "Chroma", FakeChroma)

    question_bank.QuestionBank()

    assert Path(captured["persist_directory"]) == vector_path
