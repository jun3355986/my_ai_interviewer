#!/usr/bin/env python3
"""Sync replay traces into LangSmith and run deterministic local evaluators."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[2]
PYTHON_DIR = ROOT_DIR / "ai_interviewer"
sys.path.insert(0, str(PYTHON_DIR))

from services.agent_runtime.config import load_agent_runtime_config
from services.agent_runtime.evaluation import (
    evaluate_replay_report,
    replay_trace_to_dataset_examples,
)


def sync_dataset(examples: list[dict], dataset_name: str) -> None:
    if os.getenv("LANGSMITH_EVALUATION_SYNC", "").strip().lower() not in {"1", "true", "yes", "on"}:
        return
    from langsmith import Client

    client = Client()
    try:
        dataset = client.read_dataset(dataset_name=dataset_name)
    except Exception:
        dataset = client.create_dataset(dataset_name=dataset_name)
    for example in examples:
        client.create_example(
            inputs=example["inputs"],
            outputs=example["outputs"],
            metadata=example["metadata"],
            dataset_id=dataset.id,
        )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare LangSmith replay evaluation examples.")
    parser.add_argument("trace", type=Path, help="Replay trace JSONL")
    parser.add_argument("--report", type=Path, help="Replay report JSON produced by tests/scripts/interview_replay.py")
    parser.add_argument("--dataset-name", default=None)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    config = load_agent_runtime_config()
    examples = replay_trace_to_dataset_examples(args.trace)
    dataset_name = args.dataset_name or config.langsmith_dataset_name
    sync_dataset(examples, dataset_name)
    print(f"Prepared {len(examples)} LangSmith evaluation examples for dataset {dataset_name}")

    if args.report:
        report = json.loads(args.report.read_text(encoding="utf-8"))
        results = evaluate_replay_report(report)
        for result in results:
            status = "PASS" if result.passed else "FAIL"
            print(f"[{status}] {result.key}: {result.message}")
        return 0 if all(result.passed for result in results) else 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
