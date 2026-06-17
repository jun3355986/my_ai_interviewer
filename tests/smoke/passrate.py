#!/usr/bin/env python3

import argparse
import json
from datetime import datetime, timezone
from typing import cast

JsonValue = str | int | float | bool | None | dict[str, "JsonValue"] | list["JsonValue"]
JsonDict = dict[str, JsonValue]


def as_dict(value: JsonValue | object) -> JsonDict:
    if isinstance(value, dict):
        return cast(JsonDict, value)
    return {}


def as_list(value: JsonValue | object) -> list[JsonValue]:
    if isinstance(value, list):
        return cast(list[JsonValue], value)
    return []


def as_str(value: JsonValue | object, default: str = "") -> str:
    if isinstance(value, str):
        return value
    return default


def as_int(value: JsonValue | object, default: int = 0) -> int:
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str):
        try:
            return int(value)
        except ValueError:
            return default
    return default


def as_float(value: JsonValue | object, default: float = 0.0) -> float:
    if isinstance(value, bool):
        return float(value)
    if isinstance(value, int | float):
        return float(value)
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            return default
    return default


def as_bool(value: JsonValue | object, default: bool = False) -> bool:
    if isinstance(value, bool):
        return value
    return default


def load_results(path: str) -> JsonDict:
    with open(path, "r", encoding="utf-8") as f:
        loaded = cast(JsonValue, json.load(f))
    return as_dict(loaded)


def build_report(data: JsonDict) -> str:
    steps_raw = as_list(data.get("steps", []))
    steps: list[JsonDict] = [as_dict(item) for item in steps_raw]
    p0 = as_dict(data.get("p0", {}))
    p1 = as_dict(data.get("p1", {}))
    p2 = as_dict(data.get("p2", {}))
    passed = as_int(p0.get("passed", 0))
    total = as_int(p0.get("total", len(steps) if steps else 10))
    all_passed = as_bool(p0.get("allPassed", passed == total), passed == total)
    p1_passed = as_int(p1.get("passed", 0))
    p1_total = as_int(p1.get("total", 0))
    p2_passed = as_int(p2.get("passed", 0))
    p2_total = as_int(p2.get("total", 0))
    overall = as_dict(data.get("overall", {}))
    weighted = as_float(overall.get("weightedScore", 0))
    status = as_str(overall.get("status", "UNKNOWN"), "UNKNOWN")
    threshold_ok = weighted >= 90
    triads_raw = as_list(data.get("failedTriads", []))
    triads: list[JsonDict] = [as_dict(item) for item in triads_raw]

    lines: list[str] = []
    lines.append("# Task 8 P0 Smoke Report")
    lines.append("")
    lines.append(f"- Generated At: {datetime.now(timezone.utc).isoformat()}")
    lines.append(f"- Source Result: `{as_str(data.get('gatewayBaseUrl', 'unknown'), 'unknown')}`")
    lines.append(f"- P0 Check: **{passed}/{total}**")
    lines.append(f"- P0 Gate (must be 10/10): **{'PASS' if all_passed and total == 10 else 'FAIL'}**")
    if p1_total:
        lines.append(f"- P1 Check: **{p1_passed}/{p1_total}**")
    if p2_total:
        lines.append(f"- P2 Check: **{p2_passed}/{p2_total}**")
    lines.append(f"- Overall Weighted Score: **{weighted}%**")
    lines.append(f"- Overall Gate (must be >=90%): **{'PASS' if threshold_ok else 'FAIL'}**")
    lines.append(f"- Final Status: **{status}**")
    lines.append("")
    lines.append("## Step Details")
    lines.append("")
    lines.append("| Step | Name | Passed | Detail | Artifact |")
    lines.append("|---|---|---|---|---|")
    for step in steps:
        sid = str(step.get("id", ""))
        name = str(step.get("name", ""))
        ok = "YES" if step.get("passed") else "NO"
        detail = str(step.get("detail", "")).replace("|", "\\|")
        artifact = str(step.get("artifact", "")).replace("|", "\\|")
        lines.append(f"| {sid} | {name} | {ok} | {detail} | `{artifact}` |")
    lines.append("")
    lines.append("## Score Notes")
    lines.append("")
    lines.append("- Formula: `(P0*1.0 + P1*0.5 + P2*0.2) / (T0*1.0 + T1*0.5 + T2*0.2) * 100`.")
    lines.append("- Pass condition: `P0=10/10` and `Overall>=90%`.")
    if triads:
        lines.append("")
        lines.append("## Failed Items (Repro/Cause/Fix)")
        lines.append("")
        lines.append("| ID | Repro Command | Direct Cause | Fix Suggestion |")
        lines.append("|---|---|---|---|")
        for item in triads:
            rid = as_str(item.get("id", ""), "").replace("|", "\\|")
            cmd = as_str(item.get("command", ""), "").replace("|", "\\|")
            cause = as_str(item.get("cause", ""), "").replace("|", "\\|")
            fix = as_str(item.get("suggestion", ""), "").replace("|", "\\|")
            lines.append(f"| {rid} | `{cmd}` | {cause} | {fix} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate P0 smoke pass-rate report")
    _ = parser.add_argument("--input", required=True, help="Input result JSON path")
    _ = parser.add_argument("--output", required=True, help="Output markdown report path")
    args = parser.parse_args()
    input_path = as_str(getattr(args, "input", ""), "")
    output_path = as_str(getattr(args, "output", ""), "")

    data = load_results(input_path)
    report = build_report(data)
    with open(output_path, "w", encoding="utf-8") as f:
        _ = f.write(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
