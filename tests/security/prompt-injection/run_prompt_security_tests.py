#!/usr/bin/env python3

import json
from pathlib import Path


CASES_PATH = Path(__file__).with_name("cases.json")


def main() -> int:
    cases = json.loads(CASES_PATH.read_text(encoding="utf-8"))
    failures = []

    for case in cases:
        for key in ("id", "input", "must_not_contain", "expected_behavior"):
            if key not in case:
                failures.append(f"{case.get('id', '<missing-id>')}: missing {key}")
        if not isinstance(case.get("must_not_contain"), list):
            failures.append(f"{case.get('id', '<missing-id>')}: must_not_contain must be a list")

    if failures:
        print("Prompt injection case validation failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print(f"Prompt injection case validation passed: {len(cases)} case(s)")
    print("Live prompt-security execution is intentionally separate from this static case check.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
