#!/usr/bin/env python3
"""Copy one Chroma question-bank collection into a new embedding-model collection.

The source collection is read without an embedding function. The target uses the
running ``AI_EMBEDDING_*`` configuration, so every document is embedded again.
It deliberately refuses to write into a non-empty target collection: a rerun
must choose another target or be reviewed before replacing any vectors.
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

from langchain_chroma import Chroma
from langchain_core.documents import Document


PROJECT_ROOT = Path(__file__).resolve().parents[2] / "ai_interviewer"
sys.path.insert(0, str(PROJECT_ROOT))

from services.question_bank import QuestionBank  # noqa: E402


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Re-embed a local Chroma question-bank collection into a new collection."
    )
    parser.add_argument(
        "--source-collection",
        default="interview_questions",
        help="Existing collection to read without modification (default: interview_questions).",
    )
    parser.add_argument(
        "--target-collection",
        required=True,
        help="Empty collection that receives newly generated vectors.",
    )
    parser.add_argument(
        "--vector-db-path",
        default=str(PROJECT_ROOT / "storage" / "vector_db"),
        help="Local Chroma persistence directory.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=10,
        help="Documents per embedding request (default: 10; Agent Plan limit).",
    )
    parser.add_argument(
        "--request-interval-seconds",
        type=float,
        default=1.0,
        help="Minimum delay after each successful Agent Plan request (default: 1.0).",
    )
    parser.add_argument(
        "--max-retries",
        type=int,
        default=5,
        help="Retries after a provider rate limit before stopping (default: 5).",
    )
    parser.add_argument(
        "--resume",
        action="store_true",
        help="Continue an existing target only when it is a strict ID subset of the source.",
    )
    return parser.parse_args()


def read_documents(source: Chroma) -> tuple[list[str], list[Document]]:
    payload = source.get(include=["documents", "metadatas"])
    ids = [str(item) for item in payload.get("ids") or []]
    texts = payload.get("documents") or []
    metadata_rows = payload.get("metadatas") or []

    if len(ids) != len(texts):
        raise RuntimeError("source Chroma collection returned inconsistent ids/documents")

    documents = [
        Document(
            page_content=text or "",
            metadata=dict(metadata_rows[index] or {}) if index < len(metadata_rows) else {},
        )
        for index, text in enumerate(texts)
    ]
    return ids, documents


def target_ids(target: QuestionBank) -> set[str]:
    payload = target.vectorstore.get(include=[])
    return {str(item) for item in payload.get("ids") or []}


def add_batch_with_retry(
    target: QuestionBank,
    documents: list[Document],
    ids: list[str],
    request_interval_seconds: float,
    max_retries: int,
) -> None:
    for attempt in range(max_retries + 1):
        try:
            target.vectorstore.add_documents(documents, ids=ids)
            time.sleep(request_interval_seconds)
            return
        except Exception as exc:
            if type(exc).__name__ != "RateLimitError" or attempt == max_retries:
                raise RuntimeError(
                    f"embedding batch failed after {attempt + 1} attempt(s): {type(exc).__name__}"
                ) from exc
            time.sleep(request_interval_seconds * (2 ** attempt))


def main() -> int:
    args = parse_arguments()
    if args.source_collection == args.target_collection:
        raise SystemExit("source and target collection must be different")
    if not 1 <= args.batch_size <= 10:
        raise SystemExit("batch size must be between 1 and 10 for Agent Plan embeddings")
    if args.request_interval_seconds <= 0:
        raise SystemExit("request interval must be greater than zero")
    if args.max_retries < 0:
        raise SystemExit("max retries must not be negative")

    source = Chroma(
        collection_name=args.source_collection,
        persist_directory=args.vector_db_path,
        embedding_function=None,
    )
    ids, documents = read_documents(source)
    if not documents:
        raise SystemExit("source collection is empty; refusing to create an empty migration")

    target = QuestionBank(collection_name=args.target_collection)
    existing_target_ids = target_ids(target)
    source_by_id = dict(zip(ids, documents))
    unexpected_target_ids = existing_target_ids - source_by_id.keys()
    if unexpected_target_ids:
        raise SystemExit("target contains IDs not present in source; refusing to mix vectors")
    if existing_target_ids and not args.resume:
        raise SystemExit("target collection is not empty; rerun only with --resume after review")

    missing_ids = [item_id for item_id in ids if item_id not in existing_target_ids]
    missing_documents = [source_by_id[item_id] for item_id in missing_ids]

    for start in range(0, len(missing_documents), args.batch_size):
        end = start + args.batch_size
        add_batch_with_retry(
            target,
            missing_documents[start:end],
            missing_ids[start:end],
            args.request_interval_seconds,
            args.max_retries,
        )

    target_count = target.get_question_count()
    if target_count != len(documents):
        raise SystemExit(
            f"target count mismatch: expected {len(documents)}, received {target_count}"
        )

    print(f"source_collection={args.source_collection}")
    print(f"target_collection={args.target_collection}")
    print(f"resumed_document_count={len(existing_target_ids)}")
    print(f"reembedded_document_count={target_count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
