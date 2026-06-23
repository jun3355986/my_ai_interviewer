import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.observability.config import ObservabilityConfig
from services.observability.repository import (
    SqlAlchemyObservabilityRepository,
    _build_repository,
)


def test_postgresql_psycopg_url_builds_sqlalchemy_repository_without_connecting():
    repository = _build_repository(
        ObservabilityConfig(
            db_url="postgresql+psycopg://user:pass@localhost:5432/observability"
        )
    )

    assert isinstance(repository, SqlAlchemyObservabilityRepository)
